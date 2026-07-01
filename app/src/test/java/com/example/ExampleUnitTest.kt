package com.example

import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class ExampleUnitTest {
    @Test
    fun proxyWorks() {
        val workDir = File("build/test_proxy_workdir")
        workDir.mkdirs()
        
        val proxyPort = LocalLatexCompiler.javaClass.getDeclaredMethod("getProxyPort").apply { isAccessible = true }.invoke(LocalLatexCompiler) as Int
        
        println("Proxy running on port: $proxyPort")
        
        // Use python to test a GET request to the proxy
        val p = ProcessBuilder(
            "python", "-c", 
            "import urllib.request; req = urllib.request.Request('http://127.0.0.1:$proxyPort/default_bundle_v33.tar.index.gz'); req.add_header('Range', 'bytes=0-10'); res = urllib.request.urlopen(req); print(res.status); print(res.read())"
        ).start()
        
        val reader = BufferedReader(InputStreamReader(p.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            println("PY OUT: $line")
        }
        val errReader = BufferedReader(InputStreamReader(p.errorStream))
        while (errReader.readLine().also { line = it } != null) {
            println("PY ERR: $line")
        }
        
        p.waitFor(10, TimeUnit.SECONDS)
    }
}
