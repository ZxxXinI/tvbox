package com.tvbox.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class VideoApiConfigServerSession(
    val url: String,
    val host: String,
    val port: Int,
)

class VideoApiConfigServer(
    private val scope: CoroutineScope,
    private val onSubmit: (lineName: String, apiUrl: String) -> String?,
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    suspend fun start(): VideoApiConfigServerSession = withContext(Dispatchers.IO) {
        stop()
        val token = UUID.randomUUID().toString().replace("-", "")
        val socket = createServerSocket()
        serverSocket = socket
        val host = localIpv4Address()
        serverJob = scope.launch(Dispatchers.IO) {
            acceptLoop(socket = socket, token = token)
        }
        VideoApiConfigServerSession(
            url = "http://$host:${socket.localPort}/?token=$token",
            host = host,
            port = socket.localPort,
        )
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun acceptLoop(socket: ServerSocket, token: String) {
        while (scope.isActive && !socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch(Dispatchers.IO) {
                runCatching {
                    handleClient(client = client, token = token)
                }
            }
        }
    }

    private fun handleClient(client: Socket, token: String) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            if (requestLine.isBlank()) return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0).orEmpty()
            val target = parts.getOrNull(1).orEmpty()
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }

            when (method) {
                "GET" -> writeHtmlResponse(socket = socket, body = configPage(token = token))
                "POST" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val read = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (read <= 0) break
                        readTotal += read
                    }
                    val form = parseForm(String(bodyChars, 0, readTotal))
                    if (form["token"] != token || !target.startsWith("/save")) {
                        writeHtmlResponse(socket, "请求已失效，请回到电视重新打开二维码。", status = "403 Forbidden")
                        return
                    }
                    val submittedName = form["lineName"].orEmpty().trim()
                    val submittedUrl = form["apiUrl"].orEmpty().trim()
                    val error = onSubmit(submittedName, submittedUrl)
                    if (error != null) {
                        writeHtmlResponse(
                            socket = socket,
                            body = configPage(
                                token = token,
                                lineName = submittedName,
                                apiUrl = submittedUrl,
                                error = error,
                            ),
                        )
                        return
                    }
                    writeHtmlResponse(socket, savedPage())
                }
                else -> writeHtmlResponse(socket, "不支持的请求。", status = "405 Method Not Allowed")
            }
        }
    }

    private fun writeHtmlResponse(
        socket: Socket,
        body: String,
        status: String = "200 OK",
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)).use { writer ->
            writer.write("HTTP/1.1 $status\r\n")
            writer.write("Content-Type: text/html; charset=utf-8\r\n")
            writer.write("Content-Length: ${bytes.size}\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.flush()
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }

    private fun configPage(
        token: String,
        lineName: String = "",
        apiUrl: String = "",
        error: String? = null,
    ): String {
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>TVBox 视频接口配置</title>
              <style>
                body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
                main { max-width: 540px; margin: 0 auto; padding: 28px 18px; }
                h1 { font-size: 26px; margin: 0 0 10px; }
                p { color: #b8c0cf; line-height: 1.6; }
                label { display: block; margin: 18px 0 8px; font-weight: 700; }
                input { width: 100%; box-sizing: border-box; border: 1px solid #657084; border-radius: 10px; padding: 14px; font-size: 16px; background: #171d2a; color: #fff; }
                button { width: 100%; margin-top: 22px; border: 0; border-radius: 10px; padding: 15px; font-size: 18px; font-weight: 700; color: #051b17; background: #28d0b0; }
                .panel { background: #1a2130; border: 1px solid #2f3a4f; border-radius: 14px; padding: 18px; }
                .hint { font-size: 14px; color: #8f9bb0; }
                .error { color: #ffb4ab; font-weight: 700; }
              </style>
            </head>
            <body>
              <main>
                <h1>TVBox 视频接口配置</h1>
                <div class="panel">
                  <p>添加 MacCms 视频接口后，会同步到电视设置页的视频接口列表。</p>
                  <p class="hint">示例：https://example.com/api.php/provide/vod</p>
                  ${error?.let { "<p class=\"error\">${it.htmlEscape()}</p>" }.orEmpty()}
                  <form method="post" action="/save">
                    <input type="hidden" name="token" value="$token" />
                    <label for="lineName">接口名称</label>
                    <input id="lineName" name="lineName" value="${lineName.htmlEscape()}" autocomplete="off" required />
                    <label for="apiUrl">MacCms 接口地址</label>
                    <input id="apiUrl" name="apiUrl" value="${apiUrl.htmlEscape()}" inputmode="url" autocomplete="off" required />
                    <button type="submit">确认同步到电视</button>
                  </form>
                </div>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun savedPage(): String {
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>已保存</title>
              <style>
                body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
                main { max-width: 520px; margin: 0 auto; padding: 34px 18px; }
                h1 { color: #28d0b0; }
                p { color: #b8c0cf; line-height: 1.6; }
              </style>
            </head>
            <body>
              <main>
                <h1>已同步到电视</h1>
                <p>可以回到电视，在设置页选择这个视频接口。</p>
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseForm(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return body.split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator < 0) return@mapNotNull null
                val key = pair.substring(0, separator).urlDecode()
                val value = pair.substring(separator + 1).urlDecode()
                key to value
            }
            .toMap()
    }

    private fun createServerSocket(): ServerSocket {
        return runCatching { ServerSocket(PREFERRED_PORT) }
            .getOrElse { ServerSocket(0) }
    }

    private fun localIpv4Address(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        return interfaces
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
            ?: "127.0.0.1"
    }

    private fun String.urlDecode(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }

    private fun String.htmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private companion object {
        const val PREFERRED_PORT = 9979
    }
}
