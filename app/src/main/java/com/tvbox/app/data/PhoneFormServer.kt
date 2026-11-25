package com.tvbox.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

internal sealed interface PhoneFormSubmission {
    data object Accepted : PhoneFormSubmission
    data class Invalid(val message: String) : PhoneFormSubmission
    data class Inactive(val message: String) : PhoneFormSubmission
}

internal class PhoneFormServer(
    parentScope: CoroutineScope,
    private val submitPath: String,
    private val renderForm: (token: String, values: Map<String, String>, error: String?) -> String,
    private val savedPage: () -> String,
    private val onSubmit: suspend (Map<String, String>) -> PhoneFormSubmission,
    private val onDelivered: suspend () -> Unit = {},
    private val onExpired: suspend () -> Unit = {},
    private val closeAfterDelivery: Boolean = true,
    private val hostProvider: () -> String = ::phoneFormLocalAddress,
    private val ttlMillis: Long = PHONE_FORM_TTL_MILLIS,
) {
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.IO,
    )
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean(false)
    private val claimed = AtomicBoolean(false)
    private val accepted = AtomicBoolean(false)
    private val lock = Any()
    private var listener: ServerSocket? = null
    private val token = UUID.randomUUID().toString().replace("-", "")
    @Volatile private var deadlineNanos = 0L
    val isSessionActive: Boolean get() = !closed.get() && System.nanoTime() < deadlineNanos

    fun start(): String {
        val socket = ServerSocket()
        try {
            val host = hostProvider()
            socket.bind(InetSocketAddress(InetAddress.getByName(host), 0), 8)
            synchronized(lock) {
                check(scope.isActive && !closed.get() && listener == null) { EXPIRED_MESSAGE }
                listener = socket
                deadlineNanos = System.nanoTime() + ttlMillis * 1_000_000
            }
            scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    if (closed.get() || clients.size >= 8) {
                        client.close()
                        continue
                    }
                    clients.add(client)
                    if (closed.get()) {
                        clients.remove(client)
                        client.close()
                        break
                    }
                    launch { handle(client) }
                }
            }
            scope.launch {
                delay(ttlMillis)
                try {
                    if (!accepted.get()) onExpired()
                } finally {
                    stop()
                }
            }
            return "http://$host:${socket.localPort}/?token=$token"
        } catch (error: Throwable) {
            runCatching { socket.close() }
            stop()
            throw error
        }
    }

    fun stop() {
        closed.set(true)
        synchronized(lock) {
            runCatching { listener?.close() }
            listener = null
        }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        scope.cancel()
    }

    private fun valid(candidate: String?): Boolean = isSessionActive && !claimed.get() && candidate == token

    private suspend fun handle(socket: Socket) {
        var delivered = false
        try {
            socket.soTimeout = 5_000
            val input = socket.getInputStream()
            val parts = readLine(input, 2_048).split(' ')
            if (parts.size != 3) throw BadRequest()
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore('?')
            val headers = readHeaders(input)

            when {
                method == "GET" && path == "/" -> {
                    val query = parseForm(target.substringAfter('?', ""))
                    if (valid(query["token"])) respond(socket, 200, renderForm(token, emptyMap(), null))
                    else respondMessage(socket, 403, EXPIRED_MESSAGE)
                }
                method == "POST" && path == submitPath -> {
                    if (headers["transfer-encoding"] != null) throw BadRequest()
                    val length = headers["content-length"]?.toIntOrNull() ?: throw BadRequest()
                    if (length !in 0..4_096) {
                        respondMessage(socket, 413, "输入内容过长。")
                        return
                    }
                    if (headers["content-type"]?.substringBefore(';')?.trim() != "application/x-www-form-urlencoded") {
                        respondMessage(socket, 415, "不支持的提交格式。")
                        return
                    }
                    val values = readForm(input, length)
                    if (!valid(values["token"]) || !claimed.compareAndSet(false, true) || !isSessionActive) {
                        respondMessage(socket, 403, EXPIRED_MESSAGE)
                        return
                    }
                    when (val result = onSubmit(values)) {
                        PhoneFormSubmission.Accepted -> {
                            accepted.set(true)
                            delivered = true
                            respond(socket, 200, savedPage())
                        }
                        is PhoneFormSubmission.Invalid -> {
                            claimed.set(false)
                            if (isSessionActive) respond(socket, 400, renderForm(token, values, result.message))
                            else respondMessage(socket, 403, EXPIRED_MESSAGE)
                        }
                        is PhoneFormSubmission.Inactive -> respondMessage(socket, 410, result.message)
                    }
                }
                method != "GET" && method != "POST" -> respondMessage(socket, 405, "不支持的请求。")
                else -> respondMessage(socket, 404, "页面不存在。")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runCatching { respondMessage(socket, 400, "请求无效，请重新扫码。") }
        } finally {
            clients.remove(socket)
            runCatching { socket.close() }
            if (delivered) {
                try {
                    onDelivered()
                } finally {
                    if (closeAfterDelivery) stop()
                }
            }
        }
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var headerBytes = 0
        while (true) {
            val line = readLine(input, 2_048)
            if (line.isEmpty()) return headers
            headerBytes += line.length
            if (headers.size >= 32 || headerBytes > 8_192) throw BadRequest()
            val separator = line.indexOf(':')
            if (separator <= 0) throw BadRequest()
            val key = line.substring(0, separator).lowercase()
            if (headers.put(key, line.substring(separator + 1).trim()) != null) throw BadRequest()
        }
    }

    private fun readForm(input: InputStream, length: Int): Map<String, String> {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw BadRequest()
            offset += count
        }
        val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        return parseForm(body)
    }

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

    private fun parseForm(body: String): Map<String, String> {
        if (body.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        body.split('&').forEach {
            val key = URLDecoder.decode(it.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            if (result.put(key, value) != null) throw BadRequest()
        }
        return result
    }

    private fun respondMessage(socket: Socket, status: Int, message: String) =
        respond(socket, status, phoneFormDocument("<main class=panel><p>${message.phoneFormEscape()}</p></main>"))

    private fun respond(socket: Socket, status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            410 -> "Gone"
            413 -> "Payload Too Large"
            else -> "Unsupported Media Type"
        }
        val headers = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; " +
            "form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    private class BadRequest : Exception()
}

internal fun phoneFormDocument(content: String): String = """
    <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1"><title>TVBox 手机输入</title><style>
    body{margin:0;background:#101614;color:#f3f6f4;font:20px system-ui,sans-serif;padding:24px 16px} .panel{max-width:480px;margin:8vh auto;padding:24px;border:1px solid #496358;border-radius:18px;background:#1b2520}h1{font-size:26px}p{line-height:1.6}input,button{box-sizing:border-box;width:100%;padding:16px;font:inherit;border-radius:10px;margin-top:16px}input{border:1px solid #809b8e;background:#101614;color:white}button{background:#7ae2ba;border:0;color:#10251b;font-weight:bold}.error{color:#ffb4ab}
    </style></head><body>$content</body></html>
""".trimIndent()

internal fun String.phoneFormEscape(): String = replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

internal fun phoneFormLocalAddress(): String = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { it.inetAddresses.toList() }
    .filterIsInstance<Inet4Address>()
    .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }?.hostAddress
    ?: error("未找到局域网地址。手机和盒子需要处于可互通的同一局域网。")

private const val EXPIRED_MESSAGE = "二维码已失效，请回到电视重新打开。"
internal const val PHONE_FORM_TTL_MILLIS = 5 * 60_000L
