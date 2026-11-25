/*
 * Musik - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.shaheem.yt.lyrics

import android.content.Context
import com.shaheem.yt.kugou.KuGou
import com.shaheem.yt.constants.EnableKugouKey
import com.shaheem.yt.utils.dataStore
import com.shaheem.yt.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = KuGou.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}
