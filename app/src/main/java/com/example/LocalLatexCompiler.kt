package com.example

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

object LocalLatexCompiler {

    private var proxyServerSocket: ServerSocket? = null
    private var proxyPort: Int = -1
    private var proxyCacheDir: File? = null

    @Synchronized
    private fun getProxyPort(): Int {
        if (proxyServerSocket != null && !proxyServerSocket!!.isClosed) {
            return proxyPort
        }
        val serverSocket = ServerSocket(0)
        proxyServerSocket = serverSocket
        proxyPort = serverSocket.localPort
        
        thread {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    thread { handleProxyClient(client) }
                } catch (e: Exception) {
                    break
                }
            }
        }
        return proxyPort
    }

    private fun handleProxyClient(client: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(client.getInputStream()))
            val firstLine = input.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]
            
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine()
                if (line.isNullOrEmpty()) break
                val split = line.indexOf(":")
                if (split > 0) {
                    headers[line.substring(0, split).trim()] = line.substring(split + 1).trim()
                }
            }
            
            val targetUrl = URL("https://relay.fullyjustified.net" + path)
            val connection = targetUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // 5 seconds max wait
            connection.readTimeout = 10000
            connection.requestMethod = method
            connection.instanceFollowRedirects = true
            
            for ((k, v) in headers) {
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Connection", ignoreCase = true)) {
                    connection.setRequestProperty(k, v)
                }
            }
            connection.setRequestProperty("Connection", "close")
            
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: "OK"
            val out = client.getOutputStream()
            out.write("HTTP/1.1 $responseCode $responseMessage\r\n".toByteArray())
            
            for ((k, vList) in connection.headerFields) {
                if (k != null && !k.equals("Transfer-Encoding", ignoreCase = true) && !k.equals("Connection", ignoreCase = true)) {
                    for (v in vList) {
                        out.write("$k: $v\r\n".toByteArray())
                    }
                }
            }
            out.write("Connection: close\r\n\r\n".toByteArray())
            
            if (method != "HEAD") {
                val inputStream = if (responseCode >= 400) connection.errorStream else connection.inputStream
                if (inputStream != null) {
                    inputStream.copyTo(out)
                }
            }
            out.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

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
                val tempZip = File(workDir, "temp_cache.zip")
                context.assets.open("tectonic_cache.zip").use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                java.util.zip.ZipFile(tempZip).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val file = File(cacheDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                tempZip.delete()
                logBuilder.append("[INFO] Cache extracted successfully!\n")
            } catch (e: Exception) {
                logBuilder.append("[ERROR] Failed to extract cache: ${e.localizedMessage}\n")
                throw Exception("Failed to extract LaTeX cache: ${e.localizedMessage}")
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
            proxyCacheDir = cacheDir
            // We use our local Kotlin proxy to bypass the rustls SSL panic on Android.
            // This allows Tectonic to dynamically download any missing files!
            val currentProxyPort = getProxyPort()
            val bundleUrl = "http://127.0.0.1:$currentProxyPort/default_bundle_v33.tar"
            
            // Re-link the dynamic proxy URL to the pre-warmed cache index SHA256.
            // If we don't do this, Tectonic thinks it's a completely new bundle and downloads all 600 files sequentially!
            val urlsDir = File(cacheDir, "urls")
            urlsDir.mkdirs()
            val encodedUrl = "http,58,,47,,47,127.0.0.1,58,$currentProxyPort,47,default_bundle_v33.tar"
            val mappingFile = File(urlsDir, encodedUrl)
            if (!mappingFile.exists()) {
                mappingFile.writeText("6ffe055852f8faf66c0acbe1a7fb27f87b869a90bad1204f3bf4d9683f597c7c\n")
            }

            // First pass: try entirely offline using -C flag
            val pbOffline = ProcessBuilder(
                tectonicBinary.absolutePath,
                "-C",
                "-b", bundleUrl,
                "document.tex"
            )
            pbOffline.directory(workDir)
            pbOffline.redirectErrorStream(true)

            val envOff = pbOffline.environment()
            envOff["HOME"] = workDir.absolutePath
            envOff["XDG_CACHE_HOME"] = workDir.absolutePath
            envOff["TMPDIR"] = workDir.absolutePath
            
            val procOff = pbOffline.start()
            val outOff = procOff.inputStream.bufferedReader().readText()
            var exitCode = procOff.waitFor()
            
            logBuilder.append(outOff)
            
            // If the offline compilation failed because of a missing bundle file (e.g. font), fallback to network
            if (exitCode != 0 && (
                outOff.contains("font not found", ignoreCase = true) ||
                outOff.contains("this bundle isn't cached", ignoreCase = true) ||
                outOff.contains("unable to find file", ignoreCase = true) ||
                outOff.contains("didn't find", ignoreCase = true)
            )) {
                logBuilder.append("\n[INFO] Missing package/font detected. Falling back to dynamic online mode to download missing files...\n")
                
                val pbOnline = ProcessBuilder(
                    tectonicBinary.absolutePath,
                    "-b", bundleUrl,
                    "document.tex"
                )
                pbOnline.directory(workDir)
                pbOnline.redirectErrorStream(true)
                val envOn = pbOnline.environment()
                envOn["HOME"] = workDir.absolutePath
                envOn["XDG_CACHE_HOME"] = workDir.absolutePath
                envOn["TMPDIR"] = workDir.absolutePath
                
                val procOn = pbOnline.start()
                val outOn = procOn.inputStream.bufferedReader().readText()
                exitCode = procOn.waitFor()
                
                logBuilder.append(outOn)
            }
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
