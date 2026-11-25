package com.shaheem.yt.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TogetherOnlineEndpointTest {
    @Test fun acceptsHostedAndLanOrigins() {
        assertEquals("http://192.168.1.10:8787", TogetherOnlineEndpoint.normalizeBaseUrl(" http://192.168.1.10:8787/ "))
        assertEquals("https://music.example", TogetherOnlineEndpoint.normalizeBaseUrl("https://music.example/v1"))
        for (raw in listOf("", "example.com", "ftp://example.com", "https://user:pass@example.com", "https://example.com?token=abc", "https://example.com/path", "http://localhost:0")) {
            assertNull(raw, TogetherOnlineEndpoint.normalizeBaseUrl(raw))
        }
    }

    @Test fun websocketUsesConfiguredServerAndTransport() {
        val base = "https://music.example"
        assertEquals("wss://music.example/v1/together/ws", TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("", base))
        assertEquals("ws://192.168.1.10:8787/v1/together/ws", TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("", "http://192.168.1.10:8787"))
        assertNull(TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("ws://music.example/v1/together/ws", base))
        assertNull(TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("wss://other.example/v1/together/ws", base))
        assertNull(TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("wss://user@music.example/v1/together/ws", base))
        assertEquals("wss://music.example/v1/together/ws", TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("wss://music.example/v1/together/ws", "https://music.example:443"))
        assertNull(TogetherOnlineEndpoint.onlineWebSocketUrlOrNull("wss://music.example:444/v1/together/ws", base))
    }
}
