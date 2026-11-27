package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64

class VideoSourceCatalogRepositoryTest {
    private val first = source("first")
    private val second = source("second")

    @Test
    fun keepsCachedListAndSendsItsEtagOnlyOncePerShanghaiDay() = runBlocking {
        val fixture = Fixture()
        fixture.responses += response(1, first, etag = "v1")
        fixture.responses += VideoSourceCatalogResponse(304)
        fixture.responses += response(2, second, etag = "v2")
        assertEquals(listOf(first), fixture.repository.refreshSources())
        assertNull(fixture.repository.refreshSources())
        fixture.nextDay()
        assertNull(fixture.repository.refreshSources())
        assertEquals(listOf(first), fixture.repository.loadCachedSources())
        assertNull(fixture.repository.refreshSources())
        fixture.nextDay()
        assertEquals(listOf(second), fixture.repository.refreshSources())
        assertEquals(listOf(null, "v1", "v1"), fixture.validators)
    }

    @Test
    fun invalidNetworkOrOlderCatalogNeverReplacesVerifiedCache() = runBlocking {
        val fixture = Fixture()
        fixture.responses += response(2, second, etag = "v2")
        fixture.repository.refreshSources()
        fixture.nextDay()
        fixture.responses += response(1, first, etag = "v1")
        assertTrue(runCatching { fixture.repository.refreshSources() }.isFailure)
        assertEquals(listOf(second), fixture.repository.loadCachedSources())
        assertNull(fixture.repository.refreshSources())
        fixture.now += 16 * 60_000L

        val original = response(3, first, etag = "v3")
        fixture.responses += original.copy(envelope = original.envelope!!.replace("\"signature\":\"", "\"signature\":\"AAAA"))
        assertTrue(runCatching { fixture.repository.refreshSources() }.isFailure)
        assertEquals(listOf(second), fixture.repository.loadCachedSources())
        assertEquals("v2", fixture.saved!!.etag)
    }

    @Test
    fun networkFailureKeepsCachedSourcesAndCanRetryLaterThatDay() = runBlocking {
        val fixture = Fixture()
        fixture.responses += response(1, first, etag = "v1")
        fixture.repository.refreshSources()
        fixture.nextDay()
        assertTrue(runCatching { fixture.repository.refreshSources() }.exceptionOrNull() is IOException)
        assertEquals(listOf(first), fixture.repository.loadCachedSources())
        fixture.now += 16 * 60_000L
        fixture.responses += response(2, second, etag = "v2")
        assertEquals(listOf(second), fixture.repository.refreshSources())
    }

    @Test
    fun rejectsUnverifiedLocalDataAndDoesNotSendItsEtag() = runBlocking {
        val fixture = Fixture()
        fixture.saved = CachedVideoSources("{\"payload\":\"a\",\"signature\":\"b\"}", "incorrect", "2026-11-27")
        assertNull(fixture.repository.loadCachedSources())
        fixture.responses += response(1, first, etag = "v1")
        assertEquals(listOf(first), fixture.repository.refreshSources())
        assertEquals(listOf<String?>(null), fixture.validators)
    }

    @Test
    fun rejectsDuplicateAndInvalidSources() {
        assertTrue(runCatching {
            decodeVideoSourceCatalog(Json.encodeToString(VideoSourceCatalog(1, listOf(first, first))))
        }.isFailure)
        assertTrue(runCatching {
            decodeVideoSourceCatalog(Json.encodeToString(VideoSourceCatalog(1, listOf(source("custom_example")))))
        }.isFailure)
        assertTrue(runCatching {
            decodeVideoSourceCatalog(Json.encodeToString(VideoSourceCatalog(1, listOf(
                first.copy(baseUrls = listOf("https://example.test/?value=1")),
            ))))
        }.isFailure)
    }

    private fun response(revision: Long, source: ApiLine, etag: String): VideoSourceCatalogResponse {
        val payload = Json.encodeToString(VideoSourceCatalog(revision, listOf(source))).toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keys.private)
        signer.update(payload)
        val base64 = Base64.getEncoder()
        val envelope = """{"payload":"${base64.encodeToString(payload)}","signature":"${base64.encodeToString(signer.sign())}"}"""
        return VideoSourceCatalogResponse(200, etag, envelope)
    }

    private class Fixture {
        var now = Instant.parse("2026-11-27T15:59:00Z").toEpochMilli()
        var saved: CachedVideoSources? = null
        val validators = mutableListOf<String?>()
        val responses = ArrayDeque<VideoSourceCatalogResponse>()
        val repository = DefaultVideoSourceCatalogRepository(
            load = { saved },
            save = { saved = it },
            fetch = { etag ->
                validators += etag
                responses.removeFirstOrNull() ?: throw IOException("Controlled network failure")
            },
            publicKey = keys.public,
            nowMs = { now },
        )
        fun nextDay() {
            now += if (now == Instant.parse("2026-11-27T15:59:00Z").toEpochMilli()) 16 * 60_000L else 24 * 60 * 60_000L
        }
    }

    private companion object {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        fun source(id: String) = ApiLine(id, id, listOf("https://$id.example.test/api/"))
    }
}
