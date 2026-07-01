package com.example

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object LocalLatexCompiler {

    fun compile(context: Context, source: String, logBuilder: StringBuilder): File {
        logBuilder.append("[INFO] Starting true offline compilation using native Tectonic engine...\n")
        
        val workDir = context.cacheDir
        val sourceFile = File(workDir, "document.tex")
        val pdfFile = File(workDir, "document.pdf")
        
        // Clean up old pdf
        if (pdfFile.exists()) {
            pdfFile.delete()
        }

        // Preprocess source for XeTeX compatibility (Tectonic uses XeTeX internally).
        // Many pdfTeX-specific commands crash XeTeX, so we strip them automatically.
        val processedSource = source
            .replace("\\pdfgentounicode=1", "% pdfgentounicode removed (XeTeX handles Unicode natively)")
            .replace("\\input{glyphtounicode}", "% glyphtounicode removed (XeTeX handles Unicode natively)")

        // Write the source tex file
        sourceFile.writeText(processedSource, Charsets.UTF_8)
        logBuilder.append("[INFO] Wrote document.tex to local cache.\n")

        // Extract the pre-warmed Tectonic cache (directory structure with files, urls, manifests)
        // This allows Tectonic to use cached files and dynamically download missing ones.
        val cacheDir = File(workDir, "Tectonic")
        if (!cacheDir.exists()) {
            logBuilder.append("[INFO] Extracting pre-warmed LaTeX cache (first run only)...\n")
            try {
                cacheDir.mkdirs()
                context.assets.open("tectonic_cache.zip").use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val file = File(cacheDir, entry.name)
                            if (entry.isDirectory) {
                                file.mkdirs()
                            } else {
                                file.parentFile?.mkdirs()
                                file.outputStream().use { zis.copyTo(it) }
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                logBuilder.append("[INFO] Cache extracted successfully!\n")
            } catch (e: Exception) {
                logBuilder.append("[ERROR] Failed to extract cache: ${e.localizedMessage}\n")
                throw Exception("Failed to extract LaTeX cache: ${e.localizedMessage}")
            }
        }
        
        // Extract SSL CA certificates for musl/rustls to fix Android HTTPS downloads
        val cacertFile = File(workDir, "cacert.pem")
        if (!cacertFile.exists()) {
            try {
                context.assets.open("cacert.pem").use { input ->
                    cacertFile.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                logBuilder.append("[ERROR] Failed to extract cacert.pem: ${e.localizedMessage}\n")
            }
        }
        
        // Locate the Tectonic binary in the native libraries directory
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        val tectonicBinary = File(nativeLibraryDir, "libtectonic.so")
        
        if (!tectonicBinary.exists()) {
            throw Exception("Tectonic binary not found in native library directory at ${tectonicBinary.absolutePath}")
        }
        
        logBuilder.append("[INFO] Located executable Tectonic engine at ${tectonicBinary.absolutePath}\n")
        logBuilder.append("[INFO] Executing Tectonic engine (dynamic online/offline)...\n")
        
        try {
            // We MUST use --only-cached. Tectonic is statically compiled with musl and rustls,
            // which completely fails to read Android CA certs, causing an unrecoverable panic
            // if it tries to hit the internet. We rely entirely on our 600+ file pre-warmed cache.
            val processBuilder = ProcessBuilder(
                tectonicBinary.absolutePath,
                "--only-cached",
                "document.tex"
            )
            processBuilder.directory(workDir)
            processBuilder.redirectErrorStream(true)

            // Set environment variables for Tectonic's internal operations and SSL fixes
            val env = processBuilder.environment()
            env["HOME"] = workDir.absolutePath
            env["XDG_CACHE_HOME"] = workDir.absolutePath
            env["TMPDIR"] = workDir.absolutePath
            env["SSL_CERT_FILE"] = cacertFile.absolutePath
            
            val process = processBuilder.start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                logBuilder.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            logBuilder.append("[INFO] Tectonic finished with exit code $exitCode\n")
            
            if (exitCode != 0) {
                val logOutput = logBuilder.toString()
                throw Exception("Tectonic compilation failed with exit code $exitCode.\n\n--- TECTONIC LOG ---\n$logOutput")
            }
            
            if (!pdfFile.exists()) {
                throw Exception("PDF file was not generated by Tectonic.")
            }
            
            return pdfFile
            
        } catch (e: Exception) {
            logBuilder.append("[ERROR] Execution failed: ${e.message}\n")
            throw e
        }
    }
}
