/*
 * Musik - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.shaheem.yt.extensions

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }
