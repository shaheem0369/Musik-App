package com.shaheem.yt.utils

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

fun isLocalMediaId(id: String): Boolean = id.startsWith("file:") || id.startsWith("content:")

/** Copies audio atomically and uses its contents to avoid duplicate imports. */
internal fun storeLocalAudio(input: InputStream, directory: File, checkActive: () -> Unit = {}): File {
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create music directory" }
    val temporary = File.createTempFile("import-", ".tmp", directory)
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        temporary.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
            }
        }
        require(temporary.length() > 0) { "The selected file is empty" }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$hash.audio")
        if (!destination.exists()) check(temporary.renameTo(destination)) { "Cannot save music file" }
        return destination
    } finally {
        temporary.delete()
    }
}
