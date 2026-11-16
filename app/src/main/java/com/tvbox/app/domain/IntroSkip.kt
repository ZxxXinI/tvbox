package com.tvbox.app.domain

const val MAX_INTRO_SKIP_SECONDS = 600

fun introSkipKey(apiLineId: String, movieId: Int): String = "$apiLineId:$movieId"

fun AppSettings.introSkipSeconds(apiLineId: String, movieId: Int): Int =
    (introSkipOverrides[introSkipKey(apiLineId, movieId)] ?: defaultIntroSkipSeconds)
        .coerceIn(0, MAX_INTRO_SKIP_SECONDS)

fun AppSettings.withIntroSkipOverride(key: String, seconds: Int?): AppSettings = copy(
    introSkipOverrides = if (seconds == null) introSkipOverrides - key
        else introSkipOverrides + (key to seconds.coerceIn(0, MAX_INTRO_SKIP_SECONDS)),
)

// 每次开播拍下设置快照；中途修改不会改变当前集的位置。
class IntroSkipPolicy {
    private var pendingSeconds = 0

    fun begin(fromStart: Boolean, seconds: Int) {
        pendingSeconds = if (fromStart) seconds.coerceIn(0, MAX_INTRO_SKIP_SECONDS) else 0
    }

    fun cancel() { pendingSeconds = 0 }

    fun target(positionMs: Long, durationMs: Long, seekable: Boolean): Long? {
        val target = pendingSeconds * 1_000L
        if (target <= 0) return null
        if (positionMs >= target) { cancel(); return null }
        if (durationMs <= 0) return null
        if (target >= durationMs || !seekable) { cancel(); return null }
        cancel()
        return target
    }
}
