package com.shaheem.yt.together

/** The measured offset is remote minus local; subtract it to map a remote sample to local time. */
internal fun togetherPlaybackPosition(positionMs: Long, isPlaying: Boolean, sentAtRemoteMs: Long,
    nowLocalMs: Long, clock: TogetherClockSnapshot): Long {
    if (!isPlaying || !clock.hasEstimate) return positionMs.coerceAtLeast(0)
    val sampleAge = (nowLocalMs - sentAtRemoteMs + clock.estimatedOffsetMs).coerceIn(0, 5000)
    return (positionMs.coerceAtLeast(0) + sampleAge).coerceAtLeast(0)
}
