package com.shaheem.yt.utils

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import android.net.Uri
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal suspend fun prepareLocalAudioDataSpec(dataSpec: DataSpec): DataSpec {
    if (dataSpec.uri.scheme != "file") return dataSpec
    val playable = LocalAudioDecoder.prepare(File(requireNotNull(dataSpec.uri.path)))
    return dataSpec.buildUpon().setUri(Uri.fromFile(playable))
        .setKey("local-audio-v2:${playable.absolutePath}").build()
}

/** Keeps the imported source intact and prepares a compatible playback copy when necessary. */
internal object LocalAudioDecoder {
    private val prepared = ConcurrentHashMap<String, File>()
    private val conversionMutex = Mutex()

    fun probe(file: File): LocalAudioInfo {
        val session = FFprobeKit.executeWithArguments(arrayOf(
            "-v", "error", "-show_streams", "-show_format", "-of", "json", file.absolutePath,
        ))
        if (!ReturnCode.isSuccess(session.returnCode)) throw IOException("Cannot read this audio file")
        return LocalAudioInfo.parse(session.output)
    }

    suspend fun prepare(file: File, info: LocalAudioInfo? = null): File = withContext(Dispatchers.IO) {
        prepared[file.absolutePath]?.takeIf { it.isFile && it.length() > 0 }?.let { return@withContext it }
        conversionMutex.withLock {
            prepared[file.absolutePath]?.takeIf { it.isFile && it.length() > 0 }?.let { return@withLock it }
            require(file.isFile && file.length() > 0) { "Imported audio is missing or empty" }
            val converted = File(file.parentFile, "${file.nameWithoutExtension}.playback.flac")
            val result = if (converted.isFile && converted.length() > 0) converted else {
                val audio = info ?: probe(file)
                if (!audio.needsConversion) file else {
                    val temporary = File.createTempFile("decode-", ".flac", file.parentFile)
                    try {
                        val arguments = mutableListOf(
                            "-nostdin", "-v", "error", "-y", "-i", file.absolutePath,
                            "-map", "0:a:0", "-vn", "-c:a", "flac", "-sample_fmt", "s32",
                        )
                        // DSD is decoded to PCM for Android output; this is not DSD passthrough.
                        if (audio.codec.startsWith("dsd_")) arguments.addAll(listOf("-ar", "88200"))
                        arguments.addAll(listOf("-f", "flac", temporary.absolutePath))
                        convert(arguments.toTypedArray())
                        currentCoroutineContext().ensureActive()
                        check(temporary.length() > 0 && temporary.renameTo(converted)) { "Cannot save decoded audio" }
                        converted
                    } finally {
                        temporary.delete()
                    }
                }
            }
            prepared[file.absolutePath] = result
            result
        }
    }

    private suspend fun convert(arguments: Array<String>): Unit = suspendCancellableCoroutine { continuation ->
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, { completed ->
            if (continuation.isActive) {
                if (ReturnCode.isSuccess(completed.returnCode)) continuation.resume(Unit)
                else continuation.resumeWithException(IOException("Cannot decode this audio file"))
            }
        })
        continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
    }
}
