package com.shaheem.yt.together

import org.junit.Assert.assertEquals
import org.junit.Test

class TogetherPlaybackTimingTest {
    @Test fun compensatesClockOffsetInBothDirections() {
        val remoteAhead = TogetherClockSnapshot(estimatedOffsetMs = 5000, hasEstimate = true)
        assertEquals(4100L, togetherPlaybackPosition(4000, true, 6000, 1100, remoteAhead))
        val remoteBehind = TogetherClockSnapshot(estimatedOffsetMs = -5000, hasEstimate = true)
        assertEquals(4100L, togetherPlaybackPosition(4000, true, 1000, 6100, remoteBehind))
    }
    @Test fun pausedAndUnsynchronizedClocksDoNotAdvanceTheTrack() {
        assertEquals(4000L, togetherPlaybackPosition(4000, false, 100, 999999, TogetherClockSnapshot(hasEstimate = true)))
        assertEquals(4000L, togetherPlaybackPosition(4000, true, 100, 999999, TogetherClockSnapshot()))
    }
    @Test fun clampsFutureAndStaleSamples() {
        val clock = TogetherClockSnapshot(hasEstimate = true)
        assertEquals(4000L, togetherPlaybackPosition(4000, true, 2000, 1000, clock))
        assertEquals(9000L, togetherPlaybackPosition(4000, true, 1000, 999999, clock))
    }
}
