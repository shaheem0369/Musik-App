package com.shaheem.yt.utils

import org.junit.Assert.*
import org.junit.Test

class LocalAudioInfoTest {
    private fun info(codec: String, container: String = "mp3") = LocalAudioInfo.parse("""
        {"streams":[{"codec_type":"audio","codec_name":"$codec"}],
         "format":{"format_name":"$container","duration":"12.345","tags":{"TITLE":"Track","artist":"Artist"}}}
    """.trimIndent())

    @Test fun standardAudioPlaysWithoutConversion() {
        for ((codec, container) in listOf("mp3" to "mp3", "flac" to "flac", "aac" to "aac", "vorbis" to "ogg", "pcm_s24le" to "wav")) {
            assertFalse(info(codec, container).needsConversion)
        }
    }

    @Test fun alacAiffAndDsdUseCompatiblePlaybackCopy() {
        for ((codec, container) in listOf("alac" to "mov,mp4,m4a", "pcm_s24be" to "aiff", "dsd_lsbf_planar" to "dsf", "dsd_msbf" to "iff")) {
            assertTrue(info(codec, container).needsConversion)
        }
    }

    @Test fun readsTagsCaseInsensitivelyAndDurationFromContainer() {
        val audio = info("flac")
        assertEquals("Track", audio.title)
        assertEquals("Artist", audio.artist)
        assertEquals(12345L, audio.durationMs)
    }

    @Test fun missingDurationDoesNotRejectValidAudio() {
        val audio = LocalAudioInfo.parse("""{"streams":[{"codec_type":"audio","codec_name":"mp3"}]}""")
        assertNull(audio.durationMs)
    }

    @Test fun unsupportedPcmVariantIsConvertedInsteadOfSentToWavReader() {
        assertTrue(info("pcm_f64le", "wav").needsConversion)
        assertTrue(info("pcm_s24be", "aiff").needsConversion)
    }

    @Test fun unavailableStreamDurationFallsBackToContainer() {
        val audio = LocalAudioInfo.parse("""{"streams":[{"codec_type":"audio","codec_name":"mp3","duration":"N/A"}],"format":{"duration":"3.5"}}""")
        assertEquals(3500L, audio.durationMs)
    }

    @Test fun rejectsNonAudioFilesButAllowsAttachedAlbumArtwork() {
        assertThrows(IllegalStateException::class.java) { LocalAudioInfo.parse("""{"streams":[]}""") }
        assertThrows(IllegalArgumentException::class.java) {
            LocalAudioInfo.parse("""{"streams":[{"codec_type":"video","codec_name":"h264"},{"codec_type":"audio","codec_name":"aac"}]}""")
        }
        assertFalse(LocalAudioInfo.parse("""{"streams":[{"codec_type":"video","disposition":{"attached_pic":1}},{"codec_type":"audio","codec_name":"mp3"}]}""").needsConversion)
    }
}
