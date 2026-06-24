package com.tvbox.app.domain

class PlaybackAttemptTracker {
    private var attemptKey: String? = null
    private val recordedOutcomes = mutableSetOf<PlaybackAttemptOutcome>()

    fun reset() {
        attemptKey = null
        recordedOutcomes.clear()
    }

    fun onPlaybackChanged(key: String?) {
        if (attemptKey == key) return
        attemptKey = key
        recordedOutcomes.clear()
    }

    fun shouldRecordSuccess(key: String?): Boolean {
        return shouldRecord(key, PlaybackAttemptOutcome.Success)
    }

    fun shouldRecordIssue(key: String?, issueType: PlaybackIssueType): Boolean {
        val outcome = when (issueType) {
            PlaybackIssueType.Error -> PlaybackAttemptOutcome.Error
            PlaybackIssueType.SlowBuffer -> PlaybackAttemptOutcome.SlowBuffer
        }
        return shouldRecord(key, outcome)
    }

    private fun shouldRecord(key: String?, outcome: PlaybackAttemptOutcome): Boolean {
        if (key.isNullOrBlank()) return false
        onPlaybackChanged(key)
        return recordedOutcomes.add(outcome)
    }
}

private enum class PlaybackAttemptOutcome {
    Success,
    Error,
    SlowBuffer,
}
