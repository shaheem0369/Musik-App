package com.shaheem.yt.utils

import com.shaheem.yt.models.MediaMetadata
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LocalAudioFilesTest {
    @Test
    fun duplicateAudioUsesOneFileEvenWhenImportedAgain() {
        val directory = Files.createTempDirectory("musik-import-test").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val first = storeLocalAudio(ByteArrayInputStream(bytes), directory)
            val second = storeLocalAudio(ByteArrayInputStream(bytes), directory)
            assertEquals(first, second)
            assertArrayEquals(bytes, first.readBytes())
            assertEquals(1, directory.listFiles()!!.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedCopyLeavesNoPartialFile() {
        val directory = Files.createTempDirectory("musik-import-test").toFile()
        try {
            val broken = object : InputStream() {
                var remaining = 10
                override fun read(): Int {
                    if (remaining-- > 0) return 1
                    throw IOException("Source disconnected")
                }
            }
            assertThrows(IOException::class.java) { storeLocalAudio(broken, directory) }
            assertEquals(0, directory.listFiles()!!.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun emptyFileIsRejectedAndCleanedUp() {
        val directory = Files.createTempDirectory("musik-import-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                storeLocalAudio(ByteArrayInputStream(byteArrayOf()), directory)
            }
            assertEquals(0, directory.listFiles()!!.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun localMetadataRemainsLocalWhenSavedToDatabase() {
        fun metadata(id: String) = MediaMetadata(id, "Track", emptyList(), 180)
        assertTrue(metadata("file:///data/user/0/com.shaheem.yt/files/local_music/track.audio").toSongEntity().isLocal)
        assertTrue(metadata("content://media/external/audio/media/123").toSongEntity().isLocal)
        assertFalse(metadata("dQw4w9WgXcQ").toSongEntity().isLocal)
        assertFalse(isLocalMediaId("https://music.youtube.com/watch?v=123"))
    }
}
