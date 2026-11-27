package com.tvbox.app.data

import com.tvbox.app.domain.AiProviders
import com.tvbox.app.domain.AiModelConfig
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.CustomAiConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PhoneConfigServerTest {
    @Test
    fun bothConfigFormsRequireTheCurrentQrSession() = withScope { scope ->
        val ai = aiConfigServer(scope, AiProviders.default, "test-model",
            onSubmit = { _, _ -> error("A read must never save settings") }, hostProvider = ::loopback)
        val video = videoApiConfigServer(scope,
            onSubmit = { _, _ -> error("A read must never save settings") }, hostProvider = ::loopback)
        for (server in listOf(ai, video)) {
            try {
                val url = server.start()
                val missing = request(url.substringBefore('?'))
                assertEquals(403, missing.code)
                assertFalse(missing.body.contains("name=\"token\""))
                val form = request(url)
                assertEquals(200, form.code)
                assertEquals("no-store", form.cacheControl)
                assertTrue(form.body.contains("name=\"token\""))
            } finally {
                server.stop()
            }
        }
    }

    @Test
    fun aiConfigAppliesOneValidSubmissionAndCloses() = withScope { scope ->
        val received = AtomicReference<Pair<String, String>>()
        val calls = AtomicInteger()
        val server = aiConfigServer(
            scope, AiProviders.default, "test-model",
            onSubmit = { model, key ->
                received.set(model to key)
                calls.incrementAndGet()
                PhoneFormSubmission.Accepted
            },
            hostProvider = ::loopback,
        )
        try {
            val url = server.start()
            val submitUrl = saveUrl(url)
            val fields = "modelName=new-model&apiKey=placeholder"
            assertEquals(403, request(submitUrl, fields).code)
            assertEquals(0, calls.get())
            assertEquals(200, request(submitUrl, "${tokenForm(url)}&$fields").code)
            assertEquals("new-model" to "placeholder", received.get())
            awaitClosed(server)
            assertFalse(isReachable(submitUrl, "${tokenForm(url)}&$fields"))
            assertEquals(1, calls.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun customFormListsModelsAndSavesAnExplicitSelection() = withScope { scope ->
        val selected = AtomicReference<CustomAiConfig>()
        val upstreamCalls = AtomicInteger()
        val authorization = AtomicReference<String>()
        val upstream = HttpServer.create(InetSocketAddress(loopback(), 0), 0).apply {
            createContext("/compatible/v1/models") { exchange ->
                upstreamCalls.incrementAndGet()
                authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                val bytes = """{"data":[{"id":"model/one"},{"id":"model/one"},{"id":"model-two"},{"id":1}]}""".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val base = "http://127.0.0.1:${upstream.address.port}/compatible/v1"
        val server = customAiConfigServer(scope, null, onSubmit = {
            selected.set(it)
            PhoneFormSubmission.Accepted
        }, hostProvider = ::loopback)
        try {
            val url = server.start()
            val page = request(url)
            val nonce = Regex("""<script nonce="([^"]+)">""").find(page.body)!!.groupValues[1]
            assertTrue(page.contentSecurityPolicy.orEmpty().contains("'nonce-$nonce'"))
            val listUrl = url.substringBefore("/?") + "/models"
            val fields = "apiBaseUrl=${encode("$base/chat/completions/")}&apiKey=fixture-key"
            assertEquals(403, request(listUrl, fields).code)
            assertEquals(0, upstreamCalls.get())
            val listed = request(listUrl, "${tokenForm(url)}&$fields")
            assertEquals(200, listed.code)
            assertTrue(listed.contentType.orEmpty().startsWith("application/json"))
            assertEquals("""{"models":["model/one","model-two"]}""", listed.body)
            assertEquals("Bearer fixture-key", authorization.get())
            assertEquals(null, selected.get())
            assertTrue(server.isSessionActive)
            assertEquals(200, request(saveUrl(url), "${tokenForm(url)}&$fields&modelId=${encode("model/one")}").code)
            assertEquals(CustomAiConfig(base, "model/one", "fixture-key"), selected.get())
            awaitClosed(server)
        } finally {
            server.stop()
            upstream.stop(0)
        }
    }

    @Test
    fun failedDiscoveryAllowsManualModelAndNeverReusesAKeyAtAnotherAddress() = withScope { scope ->
        val upstreamCalls = AtomicInteger()
        val upstream = HttpServer.create(InetSocketAddress(loopback(), 0), 0).apply {
            createContext("/") { exchange ->
                upstreamCalls.incrementAndGet()
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            start()
        }
        val base = "http://127.0.0.1:${upstream.address.port}/v1"
        val saved = CustomAiConfig(base, "prior-model", "saved-fixture-key")
        val selected = AtomicReference<CustomAiConfig>()
        val server = customAiConfigServer(scope, saved, onSubmit = {
            selected.set(it)
            PhoneFormSubmission.Accepted
        }, hostProvider = ::loopback)
        try {
            val url = server.start()
            val token = tokenForm(url)
            val listUrl = url.substringBefore("/?") + "/models"
            assertFalse(request(url).body.contains(saved.apiKey))

            val changed = "$token&apiBaseUrl=${encode("$base/other")}"
            assertTrue(request(listUrl, changed).body.contains("请填写此地址的有效 API Key"))
            assertEquals(400, request(saveUrl(url), "$changed&modelId=manual-model").code)
            assertEquals(0, upstreamCalls.get())

            val unchanged = "$token&apiBaseUrl=${encode(base)}"
            assertTrue(request(listUrl, unchanged).body.contains("404"))
            assertEquals(1, upstreamCalls.get())
            assertEquals(200, request(saveUrl(url), "$unchanged&modelId=manual-model").code)
            assertEquals(saved.copy(modelId = "manual-model"), selected.get())
        } finally {
            server.stop()
            upstream.stop(0)
        }
    }

    @Test
    fun chatRequestsUseOnlyTheSelectedProviderSettings() {
        val customConfig = CustomAiConfig("https://example.invalid/prefix/v1", "custom/model", "custom-fixture")
        val settings = AppSettings(
            aiProviderId = AiProviders.custom.id,
            aiModelConfigs = mapOf(AiProviders.default.id to AiModelConfig("preset-model", "preset-fixture")),
            customAiConfig = customConfig,
        )
        assertEquals(
            AiRequestConfig("https://example.invalid/prefix/v1/chat/completions", "custom/model", "custom-fixture", isCustom = true),
            settings.userAiRequestConfigOrNull(),
        )
        assertEquals(
            AiRequestConfig(AiProviders.default.chatCompletionsUrl, "preset-model", "preset-fixture"),
            settings.copy(aiProviderId = AiProviders.default.id).userAiRequestConfigOrNull(),
        )
        assertEquals(null, settings.copy(aiProviderId = "deepseek").userAiRequestConfigOrNull())
        assertTrue(runCatching { settings.copy(customAiConfig = null).userAiRequestConfigOrNull() }.isFailure)
    }

    @Test
    fun videoConfigAllowsCorrectionAndSavesValidFields() = withScope { scope ->
        val received = AtomicReference<String>()
        val server = videoApiConfigServer(
            scope,
            onSubmit = { name, address ->
                if (name.isBlank()) PhoneFormSubmission.Invalid("接口名称需要填写。")
                else {
                    received.set("$name:$address")
                    PhoneFormSubmission.Accepted
                }
            },
            hostProvider = ::loopback,
        )
        try {
            val url = server.start()
            val token = tokenForm(url)
            val address = "https://example.invalid/api/"
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val invalid = request(saveUrl(url), "$token&lineName=&apiUrl=$encodedAddress")
            assertEquals(400, invalid.code)
            assertTrue(invalid.body.contains("接口名称需要填写"))
            assertEquals(200, request(saveUrl(url), "$token&lineName=test&apiUrl=$encodedAddress").code)
            assertEquals("test:$address", received.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun expiredAndStoppedSessionsCloseTheirListeners() = withScope { scope ->
        val expired = CountDownLatch(1)
        val server = videoApiConfigServer(
            scope,
            onSubmit = { _, _ -> error("Expired session must never save settings") },
            onExpired = { expired.countDown() },
            hostProvider = ::loopback,
            ttlMillis = 80,
        )
        try {
            val original = server.start()
            assertTrue(expired.await(2, TimeUnit.SECONDS))
            assertFalse(isReachable(original))
        } finally {
            server.stop()
        }
        val replacement = videoApiConfigServer(
            scope, onSubmit = { _, _ -> error("Stopped session must never save settings") }, hostProvider = ::loopback,
        )
        try {
            val url = replacement.start()
            assertEquals(200, request(url).code)
            replacement.stop()
            assertFalse(isReachable(url))
        } finally {
            replacement.stop()
        }
    }

    private data class Response(
        val code: Int,
        val body: String,
        val cacheControl: String?,
        val contentType: String?,
        val contentSecurityPolicy: String?,
    )

    private fun request(url: String, body: String? = null): Response {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 1_000
        connection.readTimeout = 2_000
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return Response(
                code,
                stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
                connection.getHeaderField("Cache-Control"),
                connection.getHeaderField("Content-Type"),
                connection.getHeaderField("Content-Security-Policy"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isReachable(url: String, body: String? = null): Boolean = try {
        request(url, body).code in 200..299
    } catch (_: IOException) {
        false
    }

    private fun awaitClosed(server: PhoneFormServer) = runBlocking {
        withTimeout(2_000) {
            while (server.isSessionActive) delay(10)
        }
    }

    private fun tokenForm(url: String): String = URI(url).rawQuery
    private fun saveUrl(url: String): String = url.substringBefore("/?") + "/save"
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun loopback() = "127.0.0.1"

    private fun withScope(test: (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            test(scope)
        } finally {
            scope.cancel()
        }
    }
}
