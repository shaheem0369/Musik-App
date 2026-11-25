package com.shaheem.yt.playback

import androidx.media3.extractor.DefaultExtractorsFactory

// Use the complete set for both online streams and local MP3/FLAC/WAV/AAC/Ogg files.
internal fun createAudioExtractorsFactory() = DefaultExtractorsFactory()
