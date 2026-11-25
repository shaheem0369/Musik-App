/*
 * Musik - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.shaheem.yt.models

import com.shaheem.yt.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
