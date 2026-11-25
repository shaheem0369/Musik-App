package com.shaheem.yt.playback

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import org.junit.After
import androidx.compose.ui.unit.dp
import com.shaheem.yt.ui.component.BottomSheet
import com.shaheem.yt.ui.component.BottomSheetState
import com.shaheem.yt.ui.component.COLLAPSED_ANCHOR
import com.shaheem.yt.ui.component.rememberBottomSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBackNavigationTest {
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @After fun closeActivity() { if (::scenario.isInitialized) scenario.close() }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 8000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            onMain { ready = predicate() }
            if (ready) return
            Thread.sleep(20)
        }
        throw AssertionError("Back navigation did not reach the expected sheet state")
    }
    private lateinit var player: BottomSheetState
    private lateinit var queue: BottomSheetState
    private lateinit var lyrics: BottomSheetState
    private var underlyingBackCount = 0

    private fun showPlayer() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { host ->
            activity = host
            host.onBackPressedDispatcher.addCallback(host, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { underlyingBackCount++ }
            })
            host.setContent {
            MaterialTheme {
                player = rememberBottomSheetState(0.dp, 600.dp, 80.dp, COLLAPSED_ANCHOR)
                queue = rememberBottomSheetState(40.dp, 600.dp, 41.dp, COLLAPSED_ANCHOR)
                lyrics = rememberBottomSheetState(0.dp, 600.dp, 0.dp, COLLAPSED_ANCHOR)
                Box(Modifier.fillMaxSize()) {
                    BottomSheet(player, backgroundColor = Color.Black, collapsedContent = {
                        Button(onClick = player::expandSoft) { Text("Expand player") }
                    }) {
                        BottomSheet(queue, backgroundColor = Color.DarkGray, collapsedContent = {
                            Button(onClick = queue::expandSoft) { Text("Open queue") }
                        }) { Text("Queue content") }
                        BottomSheet(lyrics, backgroundColor = Color.DarkGray, collapsedContent = {}) {
                            Text("Lyrics content")
                        }
                    }
                }
            }
        }
        }
        waitUntil { ::player.isInitialized && player.isCollapsed }
        onMain { player.expandSoft() }
        waitUntil { player.isExpanded }
    }

    private fun pressBack() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
    }

    @Test fun androidEdgeBackGestureCollapsesPlayer() {
        showPlayer()
        val metrics = activity.resources.displayMetrics
        val command = "input swipe ${metrics.widthPixels - 2} ${metrics.heightPixels / 2} ${metrics.widthPixels / 2} ${metrics.heightPixels / 2} 350"
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        waitUntil { player.isCollapsed }
        onMain { assertEquals(0, underlyingBackCount) }
    }

    @Test fun backCollapsesPlayerWithoutLeavingLibrary() {
        showPlayer()
        pressBack()
        waitUntil { player.isCollapsed }
        onMain {
            assertTrue(player.isCollapsed)
            assertEquals(0, underlyingBackCount)
        }
        pressBack()
        onMain { assertEquals(1, underlyingBackCount) }
    }

    @Test fun backClosesQueueBeforePlayerAndStillWorksAfterReopening() {
        showPlayer()
        repeat(2) {
            onMain { queue.expandSoft() }
            waitUntil { queue.isExpanded }
            pressBack()
            waitUntil { queue.isCollapsed }
            onMain {
                assertTrue(queue.isCollapsed)
                assertTrue(player.isExpanded)
            }
        }
        pressBack()
        waitUntil { player.isCollapsed }
        onMain { assertTrue(player.isCollapsed); assertEquals(0, underlyingBackCount) }
    }

    @Test fun backClosesLyricsBeforeQueueAndPlayer() {
        showPlayer()
        onMain { queue.expandSoft(); lyrics.expandSoft() }
        waitUntil { queue.isExpanded && lyrics.isExpanded }
        pressBack()
        waitUntil { lyrics.isCollapsed }
        onMain { assertTrue(lyrics.isCollapsed); assertTrue(queue.isExpanded); assertTrue(player.isExpanded) }
        pressBack()
        waitUntil { queue.isCollapsed }
        onMain { assertTrue(queue.isCollapsed); assertTrue(player.isExpanded) }
        pressBack()
        waitUntil { player.isCollapsed }
        onMain { assertTrue(player.isCollapsed); assertEquals(0, underlyingBackCount) }
    }
}
