package com.shaheem.yt.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shaheem.yt.constants.TogetherServerTokenKey
import com.shaheem.yt.constants.TogetherServerUrlKey
import kotlinx.coroutines.flow.first
import java.net.URI

object TogetherOnlineEndpoint {
    fun normalizeBaseUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim().trimEnd('/'))
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        // The relay exposes its API at the origin; reverse proxies must preserve /v1.
        require(uri.path.isNullOrEmpty() || uri.path == "/v1")
        uri.toString().removeSuffix("/v1")
    }.getOrNull()

    suspend fun baseUrlOrNull(dataStore: DataStore<Preferences>): String? =
        normalizeBaseUrl(dataStore.data.first()[TogetherServerUrlKey].orEmpty())

    suspend fun bearerTokenOrNull(dataStore: DataStore<Preferences>): String? =
        dataStore.data.first()[TogetherServerTokenKey]?.trim()?.takeIf { it.isNotBlank() }

    fun onlineWebSocketUrlOrNull(rawWsUrl: String, baseUrl: String): String? = runCatching {
        val base = URI(requireNotNull(normalizeBaseUrl(baseUrl)))
        val expectedScheme = if (base.scheme == "https") "wss" else "ws"
        val candidate = if (rawWsUrl.isBlank()) {
            URI(expectedScheme, null, base.host, base.port, "/v1/together/ws", null, null)
        } else URI(rawWsUrl.trim())
        // Never send the server access token to a host supplied by an API response.
        require(candidate.scheme == expectedScheme && candidate.host.equals(base.host, true))
        fun effectivePort(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme in listOf("https", "wss")) 443 else 80
        require(effectivePort(candidate) == effectivePort(base) && candidate.rawUserInfo == null && candidate.rawFragment == null)
        candidate.toString()
    }.getOrNull()
}
