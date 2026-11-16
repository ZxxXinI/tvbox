package com.tvbox.app.data

import kotlinx.coroutines.*
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class PhoneSearchSession(val url: String)

/** 仅在搜索对话框打开时监听；不提供配置、文件读取或其他遥控接口。 */
class PhoneSearchServer(
    parentScope: CoroutineScope,
    private val onSubmit: suspend (String) -> String?,
    private val onDelivered: suspend () -> Unit,
    private val onExpired: suspend () -> Unit = {},
    private val hostProvider: () -> String = ::phoneSearchLocalAddress,
    private val ttlMillis: Long = 5 * 60_000L,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean(false)
    private val claimed = AtomicBoolean(false)
    private val lock = Any()
    private var listener: ServerSocket? = null
    private val token = UUID.randomUUID().toString().replace("-", "")
    @Volatile private var deadlineNanos = 0L
    val isSessionActive: Boolean get() = !closed.get() && System.nanoTime() < deadlineNanos

    fun start(): PhoneSearchSession {
        val host = hostProvider()
        val socket = ServerSocket()
        try {
            socket.bind(InetSocketAddress(InetAddress.getByName(host), 0), 8)
            synchronized(lock) {
                check(!closed.get() && listener == null) { "扫码会话已关闭，请重新打开。" }
                listener = socket
                deadlineNanos = System.nanoTime() + ttlMillis * 1_000_000
            }
            scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    if (closed.get() || clients.size >= 8) { client.close(); continue }
                    clients.add(client)
                    if (closed.get()) { clients.remove(client); client.close(); break }
                    launch { handle(client) }
                }
            }
            scope.launch {
                delay(ttlMillis)
                try { onExpired() } finally { stop() }
            }
            return PhoneSearchSession("http://$host:${socket.localPort}/?token=$token")
        } catch (error: Throwable) {
            socket.close()
            stop()
            throw error
        }
    }

    fun stop() {
        closed.set(true)
        synchronized(lock) { runCatching { listener?.close() }; listener = null }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        scope.cancel()
    }

    private fun valid(candidate: String?): Boolean =
        isSessionActive && !claimed.get() && candidate == token

    private suspend fun handle(socket: Socket) {
        var delivered = false
        try {
            socket.soTimeout = 5_000
            run {
                val it = socket
                val input = it.getInputStream()
                val parts = readLine(input, 2_048).split(' ')
                if (parts.size != 3) throw BadRequest()
                val method = parts[0]
                val target = parts[1]
                val path = target.substringBefore('?')
                val headers = mutableMapOf<String, String>()
                var headerBytes = 0
                while (true) {
                    val line = readLine(input, 2_048)
                    if (line.isEmpty()) break
                    headerBytes += line.length
                    if (headers.size >= 32 || headerBytes > 8_192) throw BadRequest()
                    val separator = line.indexOf(':')
                    if (separator <= 0) throw BadRequest()
                    val key = line.substring(0, separator).lowercase()
                    if (headers.put(key, line.substring(separator + 1).trim()) != null) throw BadRequest()
                }
                when {
                    method == "GET" && path == "/" -> {
                        val query = form(target.substringAfter('?', ""))
                        if (!valid(query["token"])) respond(it, 403, "二维码已失效，请回到电视重新打开。")
                        else respond(it, 200, page())
                    }
                    method == "POST" && path == "/search" -> {
                        if (headers["transfer-encoding"] != null) throw BadRequest()
                        val length = headers["content-length"]?.toIntOrNull() ?: throw BadRequest()
                        if (length !in 0..4_096) { respond(it, 413, "输入内容过长。"); return }
                        if (headers["content-type"]?.substringBefore(';')?.trim() != "application/x-www-form-urlencoded") {
                            respond(it, 415, "不支持的提交格式。"); return
                        }
                        val bytes = ByteArray(length)
                        var offset = 0
                        while (offset < length) {
                            val count = input.read(bytes, offset, length - offset)
                            if (count < 0) throw BadRequest()
                            offset += count
                        }
                        val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString()
                        val values = form(body)
                        if (!valid(values["token"])) {
                            respond(it, 403, "二维码已失效，请回到电视重新打开。"); return
                        }
                        val query = values["query"].orEmpty().trim()
                        if (query.isBlank() || query.codePointCount(0, query.length) > 100) {
                            respond(it, 400, page("请输入 1–100 个字的影片名称。")); return
                        }
                        if (!claimed.compareAndSet(false, true) || closed.get() || System.nanoTime() >= deadlineNanos) {
                            respond(it, 403, "二维码已失效，请重新扫码。"); return
                        }
                        val error = onSubmit(query)
                        if (error != null) respond(it, 410, escape(error))
                        else {
                            delivered = true
                            respond(it, 200, "<main class=panel><h1>已发送</h1><p>请在电视上查看搜索结果。</p><p>再次搜索请在电视上重新打开二维码。</p></main>")
                        }
                    }
                    method != "GET" && method != "POST" -> respond(it, 405, "不支持的请求。")
                    else -> respond(it, 404, "页面不存在。")
                }
            }
        } catch (_: CancellationException) {
            // 退出页面或后台时立即终止，不能继续触发搜索。
        } catch (_: Exception) {
            runCatching { respond(socket, 400, "请求无效，请重新扫码输入。") }
        } finally {
            clients.remove(socket)
            runCatching { socket.close() }
        }
        // 尽量先写完响应再关闭会话。若手机断线，电视也应关闭已受理的窗口，避免重复搜索。
        if (delivered) onDelivered()
    }

    private fun respond(socket: Socket, status: Int, content: String) {
        val html = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TVBox 手机输入</title><style>
            body{margin:0;background:#101614;color:#f3f6f4;font:20px system-ui,sans-serif;padding:24px 16px} .panel{max-width:480px;margin:8vh auto;padding:24px;border:1px solid #496358;border-radius:18px;background:#1b2520}h1{font-size:26px}p{line-height:1.6}input,button{box-sizing:border-box;width:100%;padding:16px;font:inherit;border-radius:10px;margin-top:16px}input{border:1px solid #809b8e;background:#101614;color:white}button{background:#7ae2ba;border:0;color:#10251b;font-weight:bold}.error{color:#ffb4ab}
            </style></head><body>$content</body></html>""".trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = when(status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 410 -> "Gone"; 413 -> "Payload Too Large"; else -> "Unsupported Media Type" }
        val headers = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    private fun page(error: String? = null): String = """
        <main class="panel" role="dialog" aria-labelledby="title"><h1 id="title">在电视上搜索</h1>
        <p>请输入要在电视上搜索的影片名称。</p>${error?.let { "<p class=error>${escape(it)}</p>" }.orEmpty()}
        <form method="post" action="/search"><input type="hidden" name="token" value="$token">
        <label for="query">影片名称</label><input id="query" name="query" placeholder="例如：西游记" maxlength="100" required autofocus autocomplete="off">
        <button type="submit">在电视上搜索</button></form><p>请保持电视停留在扫码窗口。二维码 5 分钟有效。</p></main>
    """.trimIndent()

    private fun readLine(input: InputStream, max: Int): String {
        val line = StringBuilder()
        while (line.length <= max) {
            val c = input.read()
            if (c < 0) throw BadRequest()
            if (c == 10) return line.toString().removeSuffix("\r")
            if (c > 127) throw BadRequest()
            line.append(c.toChar())
        }
        throw BadRequest()
    }

    private fun form(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (body.isEmpty()) return result
        body.split('&').forEach {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            if (result.put(key, value) != null) throw BadRequest()
        }
        return result
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private class BadRequest : Exception()
}

private fun phoneSearchLocalAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { it.inetAddresses.toList() }
    .filterIsInstance<Inet4Address>()
    .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }?.hostAddress
    ?: error("未找到局域网地址，请连接家庭网络；手机和盒子需处于可互通的同一局域网。")
