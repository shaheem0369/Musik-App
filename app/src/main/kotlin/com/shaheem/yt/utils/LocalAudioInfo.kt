package com.shaheem.yt.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class LocalAudioInfo(
    val codec: String,
    val container: String,
    val durationMs: Long?,
    val title: String?,
    val artist: String?,
) {
    val needsConversion: Boolean
        get() = codec == "alac" || codec.startsWith("dsd_") ||
            container.split(',').any { it in setOf("aiff", "dsf", "iff") } ||
            codec !in setOf("mp3", "flac", "aac", "vorbis", "opus") &&
            codec !in setOf("pcm_u8", "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm_f32le")

    companion object {
        fun parse(output: String): LocalAudioInfo {
            val root = Json.parseToJsonElement(output).jsonObject
            val streams = (root["streams"] as? JsonArray).orEmpty().map { it.jsonObject }
            require(streams.none {
                it.text("codec_type") == "video" &&
                    (it["disposition"] as? JsonObject)?.text("attached_pic") != "1"
            }) { "Choose an audio file, not a video" }
            val audio = streams.firstOrNull { it.text("codec_type") == "audio" }
                ?: error("No audio stream found")
            val format = root["format"] as? JsonObject ?: JsonObject(emptyMap())
            val tags = (format["tags"] as? JsonObject).orEmpty() +
                (audio["tags"] as? JsonObject).orEmpty()
            fun tag(name: String) = tags.entries.firstOrNull { it.key.equals(name, true) }
                ?.value?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
            val duration = (audio.text("duration")?.toDoubleOrNull() ?: format.text("duration")?.toDoubleOrNull())
                ?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
            return LocalAudioInfo(
                codec = audio.text("codec_name") ?: error("Unknown audio codec"),
                container = format.text("format_name").orEmpty(),
                durationMs = duration,
                title = tag("title"),
                artist = tag("artist") ?: tag("album_artist"),
            )
        }
    }
}

private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
