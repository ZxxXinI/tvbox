package com.tvbox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAttemptTrackerTest {
    @Test
    fun recordsEachOutcomeOncePerPlaybackAttempt() {
        val tracker = PlaybackAttemptTracker()
        val key = "movie|source|episode|url"

        assertTrue(tracker.shouldRecordSuccess(key))
        assertFalse(tracker.shouldRecordSuccess(key))
        assertTrue(tracker.shouldRecordIssue(key, PlaybackIssueType.Error))
        assertFalse(tracker.shouldRecordIssue(key, PlaybackIssueType.Error))
        assertTrue(tracker.shouldRecordIssue(key, PlaybackIssueType.SlowBuffer))
        assertFalse(tracker.shouldRecordIssue(key, PlaybackIssueType.SlowBuffer))
    }

    @Test
    fun changingPlaybackKeyStartsNewAttempt() {
        val tracker = PlaybackAttemptTracker()

        assertTrue(tracker.shouldRecordSuccess("first"))
        assertFalse(tracker.shouldRecordSuccess("first"))
        assertTrue(tracker.shouldRecordSuccess("second"))
    }

    @Test
    fun resetAllowsSamePlaybackKeyToRecordAgain() {
        val tracker = PlaybackAttemptTracker()
        val key = "same"

        assertTrue(tracker.shouldRecordIssue(key, PlaybackIssueType.SlowBuffer))
        assertFalse(tracker.shouldRecordIssue(key, PlaybackIssueType.SlowBuffer))
        tracker.reset()

        assertTrue(tracker.shouldRecordIssue(key, PlaybackIssueType.SlowBuffer))
    }
}
