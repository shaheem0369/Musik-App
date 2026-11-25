package com.shaheem.yt.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.shaheem.yt.R
import com.shaheem.yt.db.MusicDatabase
import com.shaheem.yt.models.MediaMetadata
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LocalMusicImportResult(val added: Int, val duplicates: Int, val failed: Int)

object LocalMusicImporter {
    suspend fun import(context: Context, database: MusicDatabase, uris: List<Uri>): LocalMusicImportResult =
        withContext(Dispatchers.IO) {
            var added = 0
            var duplicates = 0
            var failed = 0
            val directory = File(context.filesDir, "local_music")
            val importContext = currentCoroutineContext()
            for (uri in uris.distinct()) {
                currentCoroutineContext().ensureActive()
                var audio: File? = null
                var retained = false
                try {
                    val displayName = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "Audio"
                    audio = context.contentResolver.openInputStream(uri)?.use {
                        storeLocalAudio(it, directory) { importContext.ensureActive() }
                    }
                        ?: error("Cannot open selected audio")
                    val id = Uri.fromFile(audio).toString()
                    val existing = database.getSongByIdBlocking(id)?.song
                    if (existing != null) {
                        retained = true
                        if (existing.inLibrary == null) {
                            database.withTransaction { update(existing.copy(inLibrary = LocalDateTime.now())) }
                            added++
                        } else duplicates++
                        continue
                    }
                    val audioInfo = LocalAudioDecoder.probe(audio)
                    val playable = LocalAudioDecoder.prepare(audio, audioInfo)
                    // Album artwork is optional: Android's tag reader does not understand every codec.
                    val artwork = runCatching {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(audio.absolutePath)
                            retriever.embeddedPicture?.takeIf { it.size <= 10 * 1024 * 1024 }?.let { bytes ->
                                File(directory, "${audio.nameWithoutExtension}.jpg").also { it.writeBytes(bytes) }
                            }
                        } finally {
                            retriever.release()
                        }
                    }.getOrNull()
                    val durationMs = audioInfo.durationMs ?: runCatching {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(playable.absolutePath)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        } finally {
                            retriever.release()
                        }
                    }.getOrNull()
                    val metadata = MediaMetadata(
                        id = id,
                        title = audioInfo.title ?: displayName.substringBeforeLast('.').ifBlank { displayName },
                        artists = listOf(MediaMetadata.Artist(
                            id = null,
                            name = audioInfo.artist ?: context.getString(R.string.local_music_unknown_artist),
                        )),
                        duration = durationMs?.let { (it / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt() } ?: -1,
                        thumbnailUrl = artwork?.let { Uri.fromFile(it).toString() },
                        inLibrary = LocalDateTime.now(),
                    )
                    database.withTransaction { insert(metadata) { it.copy(isLocal = true, dateDownload = null) } }
                    retained = true
                    added++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed++
                } finally {
                    if (!retained) {
                        audio?.delete()
                        audio?.let {
                            File(directory, "${it.nameWithoutExtension}.jpg").delete()
                            File(directory, "${it.nameWithoutExtension}.playback.flac").delete()
                        }
                    }
                }
            }
            LocalMusicImportResult(added, duplicates, failed)
        }
}
