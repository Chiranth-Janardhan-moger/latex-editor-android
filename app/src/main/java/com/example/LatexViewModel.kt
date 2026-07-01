package com.example

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class CompileState {
    object Idle : CompileState()
    object Compiling : CompileState()
    object Success : CompileState()
    data class Error(val log: String) : CompileState()
}

enum class CompileMode {
    AUTO, ONLINE, OFFLINE
}

class LatexViewModel : ViewModel() {

    private val _compileMode = MutableStateFlow(CompileMode.OFFLINE)
    val compileMode: StateFlow<CompileMode> = _compileMode.asStateFlow()

    private val _compileLog = MutableStateFlow("note: Ready to invoke Tectonic LaTeX compiler engine.\nnote: Waiting for user action...")
    val compileLog: StateFlow<String> = _compileLog.asStateFlow()

    fun setCompileMode(mode: CompileMode) {
        _compileMode.value = mode
    }

    private val _currentUri = MutableStateFlow<Uri?>(null)
    val currentUri: StateFlow<Uri?> = _currentUri.asStateFlow()

    private val _fileName = MutableStateFlow("untitled.tex")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _latexContent = MutableStateFlow(DEFAULT_LATEX_TEMPLATE)
    val latexContent: StateFlow<String> = _latexContent.asStateFlow()

    private val _isEditing = MutableStateFlow(false) // default to view mode
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _compileState = MutableStateFlow<CompileState>(CompileState.Idle)
    val compileState: StateFlow<CompileState> = _compileState.asStateFlow()

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    init {
        // Initially trigger compilation for the default template
        // Note: Main UI needs a context to cache the PDF, which is handled upon screen init or view binding.
    }

    fun setEditing(editing: Boolean) {
        _isEditing.value = editing
    }

    fun updateContent(newContent: String) {
        _latexContent.value = newContent
    }

    fun loadFromUri(context: Context, uri: Uri) {
        _currentUri.value = uri
        _fileName.value = getFileNameFromUri(context, uri)
        
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                readTextFromUri(context, uri)
            }
            if (content != null) {
                _latexContent.value = content
                // Compile automatically on load
                compileLatex(context)
            } else {
                _compileState.value = CompileState.Error("Failed to read LaTeX file from path.")
            }
        }
    }

    fun createNewDocument() {
        _currentUri.value = null
        _fileName.value = "untitled.tex"
        _latexContent.value = DEFAULT_LATEX_TEMPLATE
        _pdfFile.value = null
        _compileState.value = CompileState.Idle
        _isEditing.value = true // switch to edit mode for new files
    }

    fun compileLatex(context: Context) {
        val currentContent = _latexContent.value
        _compileState.value = CompileState.Compiling

        val mode = _compileMode.value
        _compileLog.value = "note: Starting compilation in ${mode.name} mode..."

        if (mode == CompileMode.OFFLINE) {
            runLocalCompile(context, currentContent)
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var success = false
                val errorSummary = StringBuilder()

                // --- Try 1: texlive.net (Fastest & most robust) ---
                try {
                    _compileLog.value = "[INFO] Connecting to primary remote compiler engine (texlive.net)...\n[INFO] Uploading LaTeX document and rendering to PDF..."
                    
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("filecontents[1]", currentContent)
                        .addFormDataPart("filename[1]", "document.tex")
                        .addFormDataPart("engine", "pdflatex")
                        .addFormDataPart("return", "pdf")
                        .build()

                    val request = Request.Builder()
                        .url("https://texlive.net/cgi-bin/texlive.sh")
                        .post(requestBody)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type") ?: ""
                        if (contentType.contains("application/pdf", ignoreCase = true)) {
                            val tempPdfFile = File(context.cacheDir, "compiled_${System.currentTimeMillis()}.pdf")
                            context.cacheDir.listFiles { file -> 
                                file.name.startsWith("compiled_") && file.name.endsWith(".pdf") 
                            }?.forEach { it.delete() }

                            val inputStream = response.body?.byteStream()
                            if (inputStream != null) {
                                FileOutputStream(tempPdfFile).use { fos ->
                                    inputStream.copyTo(fos)
                                }
                                _pdfFile.value = tempPdfFile
                                _compileLog.value = "[INFO] Connected to texlive.net remote server.\n[INFO] Compiling document...\n[SUCCESS] PDF generated successfully via remote TeX Live!\n[INFO] Ready."
                                _compileState.value = CompileState.Success
                                success = true
                            }
                        } else {
                            val errorLog = response.body?.string() ?: "Unknown compile error on texlive.net."
                            errorSummary.append("[texlive.net error]: $errorLog\n")
                        }
                    } else {
                        val responseCode = response.code
                        val errorLog = response.body?.string() ?: "Status code $responseCode"
                        errorSummary.append("[texlive.net HTTP $responseCode]: $errorLog\n")
                    }
                    response.close()
                } catch (e: Exception) {
                    errorSummary.append("[texlive.net connection error]: ${e.localizedMessage}\n")
                }

                if (success) return@withContext

                // --- Try 2: latexonline.cc (Secondary backup) ---
                try {
                    _compileLog.value = "[WARN] texlive.net failed/offline. Trying backup engine (latexonline.cc)...\n[INFO] Formatting multipart request payload..."
                    
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "file",
                            "document.tex",
                            currentContent.toByteArray(Charsets.UTF_8).toRequestBody("text/x-tex".toMediaTypeOrNull())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("https://latexonline.cc/compile?target=document.tex&command=pdflatex")
                        .post(requestBody)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type") ?: ""
                        if (contentType.contains("application/pdf", ignoreCase = true)) {
                            val tempPdfFile = File(context.cacheDir, "compiled_${System.currentTimeMillis()}.pdf")
                            context.cacheDir.listFiles { file -> 
                                file.name.startsWith("compiled_") && file.name.endsWith(".pdf") 
                            }?.forEach { it.delete() }

                            val inputStream = response.body?.byteStream()
                            if (inputStream != null) {
                                FileOutputStream(tempPdfFile).use { fos ->
                                    inputStream.copyTo(fos)
                                }
                                _pdfFile.value = tempPdfFile
                                _compileLog.value = "[INFO] Connected to latexonline.cc remote server.\n[SUCCESS] PDF generated successfully via remote Tectonic!\n[INFO] Ready."
                                _compileState.value = CompileState.Success
                                success = true
                            }
                        } else {
                            val errorLog = response.body?.string() ?: "Unknown compile error on latexonline.cc."
                            errorSummary.append("[latexonline.cc error]: $errorLog\n")
                        }
                    } else {
                        val responseCode = response.code
                        val errorLog = response.body?.string() ?: "Status code $responseCode"
                        errorSummary.append("[latexonline.cc HTTP $responseCode]: $errorLog\n")
                    }
                    response.close()
                } catch (e: Exception) {
                    errorSummary.append("[latexonline.cc connection error]: ${e.localizedMessage}\n")
                }

                if (success) return@withContext

                val combinedError = errorSummary.toString()
                if (mode == CompileMode.AUTO) {
                    _compileLog.value = "warning: Remote compilation failed:\n$combinedError\nnote: Seamlessly falling back to Tectonic offline engine..."
                    runLocalCompile(context, currentContent)
                } else {
                    _compileLog.value = "error: Remote compilation failed:\n$combinedError"
                    _compileState.value = CompileState.Error(combinedError)
                }
            }
        }
    }


    private fun runLocalCompile(context: Context, content: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val logBuilder = StringBuilder()
                    val pdfFile = LocalLatexCompiler.compile(context, content, logBuilder)
                    _pdfFile.value = pdfFile
                    _compileLog.value = logBuilder.toString()
                    _compileState.value = CompileState.Success
                } catch (e: Exception) {
                    val errorMsg = "error: Tectonic offline compilation failed: ${e.localizedMessage}"
                    _compileLog.value = errorMsg
                    _compileState.value = CompileState.Error(errorMsg)
                }
            }
        }
    }

    fun saveEdits(context: Context, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val uri = _currentUri.value
        val content = _latexContent.value
        if (uri == null) {
            onFailure("No original file associated. Use 'Save As' to export your changes.")
            return
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                writeTextToUri(context, uri, content)
            }
            if (success) {
                onSuccess()
            } else {
                onFailure("Unable to save changes to origin URI. Make sure you have grant permissions.")
            }
        }
    }

    fun saveAsUri(context: Context, destinationUri: Uri, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        _currentUri.value = destinationUri
        _fileName.value = getFileNameFromUri(context, destinationUri)
        
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                writeTextToUri(context, destinationUri, _latexContent.value)
            }
            if (success) {
                onSuccess()
            } else {
                onFailure("Failed to write to destination.")
            }
        }
    }

    fun exportPdfToUri(context: Context, destinationUri: Uri, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        val pdfFileVal = _pdfFile.value
        if (pdfFileVal == null || !pdfFileVal.exists()) {
            onFailure("No compiled PDF available to export. Please compile the document first.")
            return
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        pdfFileVal.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e("LatexViewModel", "Error exporting PDF to URI: $destinationUri", e)
                    false
                }
            }
            if (success) {
                onSuccess()
            } else {
                onFailure("Failed to write PDF to destination.")
            }
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            // Attempt to obtain persistable URI permissions if possible
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.d("LatexViewModel", "Could not take persistable URI permission: ${e.message}")
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e("LatexViewModel", "Error reading URI: $uri", e)
            null
        }
    }

    private fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            // Fallback to "w" if "rwt" is not supported by some content providers
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                    outputStream.write(content.toByteArray(Charsets.UTF_8))
                    true
                } ?: false
            } catch (e2: Exception) {
                Log.e("LatexViewModel", "Error writing URI: $uri", e2)
                false
            }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "document.tex"
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } else {
            val path = uri.path
            if (path != null) {
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    name = path.substring(cut + 1)
                }
            }
        }
        return name
    }

    companion object {
        val DEFAULT_LATEX_TEMPLATE = """
\documentclass{article}
\begin{document}
Hello World!
\end{document}
""".trimIndent()
    }
}
