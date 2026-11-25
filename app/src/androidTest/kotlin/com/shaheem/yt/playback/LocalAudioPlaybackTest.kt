package com.shaheem.yt.playback

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.shaheem.yt.utils.LocalAudioDecoder
import com.shaheem.yt.utils.prepareLocalAudioDataSpec
import com.shaheem.yt.utils.storeLocalAudio
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/** Generates real audio, imports it with the legacy .audio suffix, then decodes and seeks it. */
@Suppress("DEPRECATION")
class LocalAudioPlaybackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun testMp3() = checkFormat("mp3", "libmp3lame")
    @Test fun testFlac() = checkFormat("flac", "flac")
    @Test fun testAlac() = checkFormat("m4a", "alac")
    @Test fun testWav() = checkFormat("wav", "pcm_s24le")
    @Test fun testAiff() = checkFormat("aiff", "pcm_s24be")
    @Test fun testAac() = checkFormat("aac", "aac")
    @Test fun testOgg() = checkFormat("ogg", "libvorbis")

    @Test fun testDsd() {
        val directory = File(instrumentation.targetContext.cacheDir, "format-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val sampleCount = 2822400L * 2
            val blocks = ((sampleCount / 8 + 4095) / 4096).toInt()
            val data = ByteArray(blocks * 4096 * 2) { 0x69 }
            val header = ByteBuffer.allocate(92).order(ByteOrder.LITTLE_ENDIAN)
                .put("DSD ".toByteArray()).putLong(28).putLong(92L + data.size).putLong(0)
                .put("fmt ".toByteArray()).putLong(52).putInt(1).putInt(0).putInt(2).putInt(2)
                .putInt(2822400).putInt(1).putLong(sampleCount).putInt(4096).putInt(0)
                .put("data".toByteArray()).putLong(12L + data.size).array()
            val source = File(directory, "test.dsf").apply { outputStream().use { it.write(header); it.write(data) } }
            importAndPlay(source, directory)
        } finally { directory.deleteRecursively() }
    }

    private fun checkFormat(extension: String, codec: String) {
        val directory = File(instrumentation.targetContext.cacheDir, "format-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(directory, "test.$extension")
            val session = FFmpegKit.executeWithArguments(arrayOf(
                "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:a", codec, "-metadata", "title=Format regression", source.absolutePath,
            ))
            assertTrue("Fixture generation: ${session.output}", ReturnCode.isSuccess(session.returnCode))
            importAndPlay(source, directory)
        } finally { directory.deleteRecursively() }
    }

    private fun importAndPlay(source: File, directory: File) {
        val imported = source.inputStream().use { storeLocalAudio(it, directory) }
        val info = LocalAudioDecoder.probe(imported)
        val playable = runBlocking { LocalAudioDecoder.prepare(imported, info) }
        assertTrue(playable.isFile && playable.length() > 0)
        assertTrue("Original file retained", imported.exists())
        if (info.needsConversion) assertTrue(playable.name.endsWith(".flac"))
        val context = instrumentation.targetContext
        val ready = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val advancing = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException>()
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec ->
                runBlocking { prepareLocalAudioDataSpec(spec) }
            }
            player = ExoPlayer.Builder(context).setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSource, createAudioExtractorsFactory()),
            ).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) ready.countDown()
                    if (state == Player.STATE_ENDED) ended.countDown()
                }
                override fun onPlayerError(error: PlaybackException) {
                    failure.set(error); ready.countDown(); ended.countDown()
                }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                    advancing.countDown()
                }
            })
            player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(imported)).setCustomCacheKey("legacy-local-key").build())
            player.prepare()
        }
        try {
            assertTrue("Player ready for ${source.extension}", ready.await(20, TimeUnit.SECONDS))
            assertNull("Source error for ${source.extension}: ${failure.get()}", failure.get())
            instrumentation.runOnMainSync { player.seekTo(500); player.play() }
            assertTrue("Audio output for ${source.extension}", advancing.await(15, TimeUnit.SECONDS))
            assertTrue("Playback completed for ${source.extension}", ended.await(15, TimeUnit.SECONDS))
            assertNull("Playback error for ${source.extension}: ${failure.get()}", failure.get())
        } finally { instrumentation.runOnMainSync { player.release() } }
    }
}
