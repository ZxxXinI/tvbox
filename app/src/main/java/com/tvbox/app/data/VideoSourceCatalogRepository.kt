package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.ApiLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val CATALOG_URL = "http://120.46.147.127/tvbox/sources.json"
private const val MAX_RESPONSE_BYTES = 128 * 1024L
private const val RETRY_DELAY_MS = 15 * 60_000L
private val catalogZone = ZoneId.of("Asia/Shanghai")
private val catalogJson = Json { ignoreUnknownKeys = true }
private val sourceIdPattern = Regex("[a-z0-9_-]{1,48}")

@Serializable
internal data class VideoSourceCatalog(val revision: Long, val sources: List<ApiLine>)

internal fun decodeVideoSourceCatalog(raw: String): VideoSourceCatalog {
    val catalog = catalogJson.decodeFromString<VideoSourceCatalog>(raw.trimStart('\uFEFF'))
    require(catalog.revision > 0 && catalog.sources.size in 1..100)
    require(catalog.sources.map { it.id }.distinct().size == catalog.sources.size)
    require(catalog.sources.all { source ->
        sourceIdPattern.matches(source.id) && !source.id.startsWith("custom_") &&
            source.name.isNotBlank() && source.name.length <= 60 && source.name.none(Char::isISOControl) &&
            source.baseUrls.size in 1..4 && source.baseUrls.all { rawUrl ->
                val url = rawUrl.toHttpUrlOrNull()
                rawUrl.length <= 512 && url != null && url.isHttps && url.username.isEmpty() &&
                    url.encodedPassword.isEmpty() && url.query == null && url.fragment == null && rawUrl.endsWith("/")
            }
    })
    return catalog
}

@Serializable
private data class SignedCatalog(val payload: String, val signature: String)

@Serializable
internal data class CachedVideoSources(
    val envelope: String,
    val etag: String?,
    val checkedDay: String,
)

internal fun verifyVideoSourceCatalog(envelope: String, publicKey: PublicKey): VideoSourceCatalog {
    require(envelope.length <= MAX_RESPONSE_BYTES)
    val signed = catalogJson.decodeFromString<SignedCatalog>(envelope)
    val payload = Base64.getDecoder().decode(signed.payload)
    val signature = Base64.getDecoder().decode(signed.signature)
    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(publicKey)
    verifier.update(payload)
    require(verifier.verify(signature)) { "Invalid video source catalog signature" }
    return decodeVideoSourceCatalog(payload.toString(Charsets.UTF_8))
}

internal data class VideoSourceCatalogResponse(
    val status: Int,
    val etag: String? = null,
    val envelope: String? = null,
)

interface VideoSourceCatalogRepository {
    suspend fun loadCachedSources(): List<ApiLine>?
    suspend fun refreshSources(): List<ApiLine>?
}

internal class DefaultVideoSourceCatalogRepository(
    private val load: () -> CachedVideoSources?,
    private val save: (CachedVideoSources) -> Unit,
    private val fetch: (String?) -> VideoSourceCatalogResponse,
    private val publicKey: PublicKey,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : VideoSourceCatalogRepository {
    private val mutex = Mutex()
    private var lastAttemptMs: Long? = null

    override suspend fun loadCachedSources(): List<ApiLine>? = withContext(Dispatchers.IO) {
        readVerified()?.second?.sources
    }

    override suspend fun refreshSources(): List<ApiLine>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = readVerified()
            val now = nowMs()
            val day = Instant.ofEpochMilli(now).atZone(catalogZone).toLocalDate().toString()
            if (cached?.first?.checkedDay == day) return@withLock null
            if (lastAttemptMs?.let { now - it in 0 until RETRY_DELAY_MS } == true) return@withLock null
            lastAttemptMs = now

            val result = fetch(cached?.first?.etag)
            if (result.status == 304) {
                val record = cached?.first ?: throw IOException("304 without a verified local list")
                save(record.copy(checkedDay = day))
                return@withLock null
            }
            val envelope = result.envelope?.takeIf { result.status == 200 }
                ?: throw IOException("Video source catalog HTTP ${result.status}")
            val catalog = verifyVideoSourceCatalog(envelope, publicKey)
            val previous = cached?.second
            require(previous == null || catalog.revision > previous.revision || catalog == previous) {
                "Rejected older or conflicting video source catalog revision"
            }

            if (catalog == previous) {
                save(checkNotNull(cached).first.copy(etag = result.etag, checkedDay = day))
                return@withLock null
            }
            save(CachedVideoSources(envelope, result.etag, day))
            catalog.sources.takeUnless { it == previous?.sources }
        }
    }

    private fun readVerified(): Pair<CachedVideoSources, VideoSourceCatalog>? {
        val record = runCatching(load).getOrNull() ?: return null
        val catalog = runCatching { verifyVideoSourceCatalog(record.envelope, publicKey) }.getOrNull() ?: return null
        return record to catalog
    }
}

internal fun videoSourceCatalogRepository(context: Context): VideoSourceCatalogRepository {
    val preferences = context.applicationContext.getSharedPreferences("video_source_catalog", Context.MODE_PRIVATE)
    val key = KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(CATALOG_PUBLIC_KEY)),
    )
    val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    return DefaultVideoSourceCatalogRepository(
        load = { preferences.getString("snapshot", null)?.let { catalogJson.decodeFromString<CachedVideoSources>(it) } },
        save = { check(preferences.edit().putString("snapshot", catalogJson.encodeToString(it)).commit()) },
        fetch = { etag ->
            val request = Request.Builder().url(CATALOG_URL).header("Cache-Control", "no-cache").apply {
                if (etag != null) header("If-None-Match", etag)
            }.build()
            client.newCall(request).execute().use { response ->
                val envelope = if (response.code == 200) {
                    val stream = response.body.source()
                    if (stream.request(MAX_RESPONSE_BYTES + 1)) throw IOException("Video source catalog too large")
                    stream.readUtf8()
                } else null
                VideoSourceCatalogResponse(response.code, response.header("ETag"), envelope)
            }
        },
        publicKey = key,
    )
}

private const val CATALOG_PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAp+JLogMIIWMjh16O+cH+fetXeCtxHEn3G//nzhOr80YNN61u1SFIJzfbyeaY9PCk1wk4hu9NcrBNJvD/UJOxWOIWOTsy+XdHEdjrgXAymhCJ2ZujZNog93Lr2vql+Cd59tAOAn1ZpeflJAPmxIZbJkVxIO9gZlPYaGedwmGofpiQxdmDGBBuS0uWSFAZPMEkUHQ2enHsT1If+dPvbYg37mGRQqxWAGD7yZfnfhf6yEdsuCBRlHBQwMvw+j6h9Q5FAaC2c5Z56hLw7+L8kkRn3cA+lfhE18ECBxrOiEE+E9Ba4bGtHkEjRoATAxIUZ31yzsk1rH5wz9iu1CiRwC9cZG2L6+6kNEIQfj1PGN4AxsqV9mWDt2V/Gu58H+bgBuiMhYEjzqRDGybrkwCLkjl9o3yZHnHDfBtJl1Ud2W5uawKRWtSFB1/UFG0UcZiIkSmLW+upSXbFNOfWP/0vTN+H4foooX9pPihA6gL7ZOaypgrvRcEpM5/hLL8r0qsxdGqBAgMBAAE="
