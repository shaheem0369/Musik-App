package com.shaheem.yt.together

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class MusikJamInvite(val code: String, val serverUrl: String? = null) {
    fun toLink(): String = serverUrl?.let { "${it.trimEnd('/')}/?code=${encode(code)}" }
        ?: "musik://jam?code=${encode(code)}"

    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        fun parse(raw: String): MusikJamInvite? = runCatching {
            val trimmed = raw.trim()
            if (trimmed.matches(Regex("[A-Za-z0-9]{6}"))) return MusikJamInvite(trimmed.uppercase(Locale.ROOT))
            val link = Regex("(?:musik://jam\\?|https?://)[^\\s]+").find(trimmed)?.value ?: return null
            val uri = URI(link)
            require(uri.rawUserInfo == null && uri.rawFragment == null)
            val isAppLink = uri.scheme == "musik" && uri.host == "jam"
            val isWebInvite = uri.scheme in listOf("http", "https") && (uri.path.isNullOrEmpty() || uri.path == "/")
            require(isAppLink || isWebInvite)
            val pairs = uri.rawQuery.orEmpty().split('&').map {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }.toMap()
            val code = pairs["code"]?.uppercase(Locale.ROOT) ?: return null
            require(code.matches(Regex("[A-Z0-9]{6}")))
            val server = if (isWebInvite) {
                requireNotNull(TogetherOnlineEndpoint.normalizeBaseUrl(URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString()))
            } else pairs["server"]?.let { requireNotNull(TogetherOnlineEndpoint.normalizeBaseUrl(it)) }
            MusikJamInvite(code, server)
        }.getOrNull()
    }
}
