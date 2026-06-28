package com.tvbox.app.data

import com.tvbox.app.domain.AiProvider
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

data class AiConfigServerSession(
    val url: String,
    val host: String,
    val port: Int,
)

class AiConfigServer(
    private val scope: CoroutineScope,
    private val onSubmit: (modelName: String, apiKey: String) -> Unit,
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    suspend fun start(
        provider: AiProvider,
        modelName: String,
    ): AiConfigServerSession = withContext(Dispatchers.IO) {
        stop()
        val token = UUID.randomUUID().toString().replace("-", "")
        val socket = createServerSocket()
        serverSocket = socket
        val host = localIpv4Address()
        serverJob = scope.launch(Dispatchers.IO) {
            acceptLoop(
                socket = socket,
                token = token,
                provider = provider,
                modelName = modelName,
            )
        }
        AiConfigServerSession(
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

    private suspend fun acceptLoop(
        socket: ServerSocket,
        token: String,
        provider: AiProvider,
        modelName: String,
    ) {
        while (scope.isActive && !socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch(Dispatchers.IO) {
                runCatching {
                    handleClient(
                        client = client,
                        token = token,
                        provider = provider,
                        modelName = modelName,
                    )
                }
            }
        }
    }

    private fun handleClient(
        client: Socket,
        token: String,
        provider: AiProvider,
        modelName: String,
    ) {
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
                "GET" -> writeHtmlResponse(
                    socket = socket,
                    body = configPage(
                        token = token,
                        provider = provider,
                        modelName = modelName,
                    ),
                )
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
                    val submittedModel = form["modelName"].orEmpty().trim()
                    val submittedKey = form["apiKey"].orEmpty().trim()
                    if (submittedModel.isBlank() || submittedKey.isBlank()) {
                        writeHtmlResponse(
                            socket = socket,
                            body = configPage(
                                token = token,
                                provider = provider,
                                modelName = submittedModel.ifBlank { modelName },
                                error = "模型名称和 API Key 都需要填写。",
                            ),
                        )
                        return
                    }
                    onSubmit(submittedModel, submittedKey)
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
        provider: AiProvider,
        modelName: String,
        error: String? = null,
    ): String {
        val safeModelName = modelName.htmlEscape()
        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>TVBox AI 配置</title>
              <style>
                body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
                main { max-width: 520px; margin: 0 auto; padding: 28px 18px; }
                h1 { font-size: 26px; margin: 0 0 10px; }
                p { color: #b8c0cf; line-height: 1.6; }
                label { display: block; margin: 18px 0 8px; font-weight: 700; }
                input { width: 100%; box-sizing: border-box; border: 1px solid #657084; border-radius: 10px; padding: 14px; font-size: 16px; background: #171d2a; color: #fff; }
                button { width: 100%; margin-top: 22px; border: 0; border-radius: 10px; padding: 15px; font-size: 18px; font-weight: 700; color: #051b17; background: #28d0b0; }
                .panel { background: #1a2130; border: 1px solid #2f3a4f; border-radius: 14px; padding: 18px; }
                .error { color: #ffb4ab; font-weight: 700; }
              </style>
            </head>
            <body>
              <main>
                <h1>TVBox AI 配置</h1>
                <div class="panel">
                  <p>当前大模型：<strong>${provider.name.htmlEscape()}</strong></p>
                  <p>填写后会自动同步到电视。API Key 只保存在电视本机。</p>
                  ${error?.let { "<p class=\"error\">${it.htmlEscape()}</p>" }.orEmpty()}
                  <form method="post" action="/save">
                    <input type="hidden" name="token" value="$token" />
                    <label for="modelName">模型名称</label>
                    <input id="modelName" name="modelName" value="$safeModelName" autocomplete="off" required />
                    <label for="apiKey">API Key</label>
                    <input id="apiKey" name="apiKey" type="password" autocomplete="off" required />
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
                <p>可以回到电视继续使用 AI 找片。</p>
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
        const val PREFERRED_PORT = 9978
    }
}
