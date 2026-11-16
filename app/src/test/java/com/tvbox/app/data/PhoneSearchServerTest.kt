package com.tvbox.app.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PhoneSearchServerTest {
    private data class Response(val code: Int, val body: String)
    private fun request(url: String, body: String? = null): Response {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 2_000
        c.readTimeout = 2_000
        try {
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(code, text)
        } finally { c.disconnect() }
    }
    private fun submitUrl(session: PhoneSearchSession) = session.url.substringBefore("/?") + "/search"
    private fun form(session: PhoneSearchSession, query: String) =
        "token=${URI(session.url).rawQuery.substringAfter("token=")}&query=${URLEncoder.encode(query, "UTF-8")}"

    private fun fixture(
        ttl: Long = 60_000,
        submit: suspend (String) -> String? = { null },
        delivered: suspend () -> Unit = {},
        expired: suspend () -> Unit = {},
        test: (PhoneSearchServer, PhoneSearchSession) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PhoneSearchServer(scope, submit, delivered, expired, { "127.0.0.1" }, ttl)
        try { test(server, server.start()) } finally { server.stop(); scope.cancel() }
    }

    @Test fun pageRequiresTokenAndOnlyProvidesKeywordInput() = fixture { _, session ->
        val invalid = request(session.url.substringBefore('?'))
        assertEquals(403, invalid.code)
        assertFalse(invalid.body.contains(URI(session.url).rawQuery.substringAfter('=')))
        val page = request(session.url)
        assertEquals(200, page.code)
        assertTrue(page.body.contains("请输入要在电视上搜索的影片名称"))
        assertTrue(page.body.contains("role=\"dialog\""))
        assertFalse(page.body.contains("getUserMedia"))
        assertEquals(404, request(session.url.substringBefore("/?") + "/save").code)
    }

    @Test fun chineseInputIsTrimmedAndSuccessfulResponsePrecedesDelivery() {
        val received = AtomicReference<String>()
        val delivered = CountDownLatch(1)
        fixture(submit = { received.set(it); null }, delivered = { delivered.countDown() }) { _, session ->
            val r = request(submitUrl(session), form(session, "  西游记 😀  "))
            assertEquals(200, r.code)
            assertTrue(r.body.contains("已发送"))
            assertEquals("西游记 😀", received.get())
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            assertEquals(403, request(submitUrl(session), form(session, "第二次")).code)
        }
    }

    @Test fun invalidInputDoesNotConsumeSession() = fixture { _, session ->
        assertEquals(403, request(submitUrl(session), "token=wrong&query=test").code)
        assertEquals(400, request(submitUrl(session), form(session, "   ")).code)
        assertEquals(400, request(submitUrl(session), form(session, "字".repeat(101))).code)
        assertEquals(400, request(submitUrl(session), "token=%GG&query=test").code)
        assertEquals(413, request(submitUrl(session), "x".repeat(4_097)).code)
        assertEquals(200, request(submitUrl(session), form(session, "有效输入")).code)
    }

    @Test fun concurrentDuplicateRequestsDispatchExactlyOnce() {
        val calls = AtomicInteger()
        fixture(submit = { calls.incrementAndGet(); delay(50); null }) { _, session ->
            val start = CountDownLatch(1)
            val results = java.util.Collections.synchronizedList(mutableListOf<Int>())
            val threads = List(4) { Thread {
                start.await()
                results.add(request(submitUrl(session), form(session, "西游记")).code)
            }.apply { start() } }
            start.countDown()
            threads.forEach { it.join(5_000); assertFalse(it.isAlive) }
            assertEquals(1, calls.get())
            assertEquals(1, results.count { it == 200 })
            assertEquals(3, results.count { it == 403 })
        }
    }

    @Test fun inactiveTelevisionRejectsWithoutSuccessPage() = fixture(submit = { "电视已离开扫码窗口" }) { _, session ->
        val r = request(submitUrl(session), form(session, "西游记"))
        assertEquals(410, r.code)
        assertFalse(r.body.contains("<h1>已发送</h1>"))
    }

    @Test fun expiryClosesPortAndNotifiesDialog() {
        val expired = CountDownLatch(1)
        fixture(ttl = 100, expired = { expired.countDown() }) { server, session ->
            assertTrue(expired.await(3, TimeUnit.SECONDS))
            assertFalse(server.isSessionActive)
            // 回调之后的 finally 会立即释放监听口。
            Thread.sleep(50)
            try { request(session.url); fail("expired server still accepts connections") } catch (_: IOException) { }
        }
    }

    @Test fun phoneDisconnectAfterAcceptanceStillCompletesTelevisionFlow() {
        val submitted = CountDownLatch(1)
        val completed = CountDownLatch(1)
        fixture(submit = { submitted.countDown(); delay(100); null }, delivered = { completed.countDown() }) { _, session ->
            val uri = URI(session.url)
            val body = form(session, "西游记").toByteArray(Charsets.UTF_8)
            Socket(uri.host, uri.port).use { socket ->
                val header = "POST /search HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.size}\r\n\r\n"
                socket.getOutputStream().apply { write(header.toByteArray()); write(body); flush() }
                assertTrue(submitted.await(2, TimeUnit.SECONDS))
                socket.setSoLinger(true, 0)
            }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(403, request(submitUrl(session), form(session, "重复")).code)
        }
    }

    @Test fun cancellationClosesIdleConnectionsAndFreshSessionUsesNewCredential() = fixture { server, session ->
        val uri = URI(session.url)
        Socket(uri.host, uri.port).use { idle ->
            idle.soTimeout = 2_000
            server.stop()
            try { assertEquals(-1, idle.getInputStream().read()) } catch (e: java.net.SocketTimeoutException) {
                throw AssertionError("idle client was not closed", e)
            } catch (_: IOException) { }
        }
        fixture { _, next ->
            assertNotEquals(URI(session.url).rawQuery, URI(next.url).rawQuery)
        }
    }

    @Test fun missingLanAddressReportsFailureAndDoesNotListen() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = PhoneSearchServer(scope, { null }, {}, hostProvider = { error("no LAN") })
        try { server.start(); fail("expected failure") } catch (e: IllegalStateException) {
            assertEquals("no LAN", e.message)
        } finally { server.stop(); scope.cancel() }
    }
}
