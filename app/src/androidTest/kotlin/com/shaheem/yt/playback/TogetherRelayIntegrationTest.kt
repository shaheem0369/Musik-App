package com.shaheem.yt.playback

import androidx.test.platform.app.InstrumentationRegistry
import com.shaheem.yt.together.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises the real Android/Ktor serializer and host/guest clients against the runnable relay. */
class TogetherRelayIntegrationTest {
    @Test fun hostAndGuestShareQueuePlaybackAndClockThroughRelay() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("serverUrl").orEmpty()
        val token = args.getString("serverToken").orEmpty()
        assumeTrue("Provide serverUrl and serverToken to run the relay integration test", url.isNotBlank() && token.isNotBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val hostEvents = Channel<TogetherServerEvent>(Channel.UNLIMITED)
        val guestEvents = Channel<TogetherClientEvent>(Channel.UNLIMITED)
        val api = TogetherOnlineApi(url, token)
        val settings = TogetherRoomSettings(allowGuestsToControlPlayback = true)
        var host: TogetherOnlineHost? = null
        var guest: TogetherClient? = null
        var connection: Job? = null
        try {
            val created = api.createSession("Android test host", settings)
            val hostWs = requireNotNull(TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(created.wsUrl, url))
            val hostClient = TogetherOnlineHost(scope, created.sessionId, created.hostKey, "test-host", "Android test host", settings,
                bearerToken = token)
            host = hostClient
            hostClient.onEvent = { hostEvents.trySend(it) }
            connection = scope.launch { hostClient.connect(hostWs) }
            var resolved: TogetherOnlineResolveResponse? = null
            withTimeout(10_000) {
                while (resolved == null) {
                    try { resolved = api.resolveCode(created.code) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: TogetherOnlineApiException) { delay(100) }
                }
            }
            val roomInfo = requireNotNull(resolved)
            assertEquals(created.sessionId, roomInfo.sessionId)
            val guestClient = TogetherClient(scope, clientId = "android-test-guest")
            guest = guestClient
            scope.launch(start = CoroutineStart.UNDISPATCHED) { guestClient.events.collect { guestEvents.send(it) } }
            guestClient.connect(roomInfo.wsUrl, roomInfo.sessionId, roomInfo.guestKey, "Android test guest")
            val welcome = withTimeout(10_000) {
                var result: TogetherClientEvent.Welcome? = null
                while (result == null) result = guestEvents.receive() as? TogetherClientEvent.Welcome
                result
            }
            assertEquals(ServerRole.GUEST, welcome.welcome.role)
            withTimeout(10_000) { while (hostEvents.receive() !is TogetherServerEvent.ParticipantJoined) Unit }
            val room = TogetherRoomState(
                sessionId = created.sessionId, hostId = "test-host", settings = settings,
                queue = listOf(TogetherTrack("testVideo01", "Relay integration track", listOf("Musik"), 120)),
                queueHash = "test-queue", currentIndex = 0, isPlaying = true, positionMs = 3000,
                sentAtElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            )
            hostClient.broadcastRoomState(room)
            suspend fun nextState(playing: Boolean): TogetherRoomState = withTimeout(10_000) {
                var state: TogetherRoomState? = null
                while (state == null) {
                    val event = guestEvents.receive()
                    if (event is TogetherClientEvent.RoomState && event.state.isPlaying == playing) state = event.state
                }
                state
            }
            val playing = nextState(true)
            assertEquals("testVideo01", playing.queue.single().id)
            assertEquals(2, playing.participants.size)
            hostClient.broadcastRoomState(room.copy(isPlaying = false, positionMs = 12500))
            assertEquals(12500L, nextState(false).positionMs)
            // Exercise the Android client heartbeat against the relay's own monotonic clock.
            guestClient.sendHeartbeat(created.sessionId, 1, android.os.SystemClock.elapsedRealtime())
            val pong = withTimeout(10_000) {
                var result: TogetherClientEvent.HeartbeatPong? = null
                while (result == null) result = guestEvents.receive() as? TogetherClientEvent.HeartbeatPong
                result
            }
            assertEquals(1L, pong.pong.pingId)
        } finally {
            guest?.disconnect()
            host?.disconnect()
            connection?.cancelAndJoin()
            scope.cancel()
            api.close()
        }
    }
}
