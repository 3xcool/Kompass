@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.tekmoon.kompass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictiveBackEdgeSwipeTest {
    private class Calls {
        var starts = 0
        val progress = mutableListOf<Float>()
        var commits = 0
        var cancels = 0
    }

    private fun ComposeUiTest.content(calls: Calls, enabled: Boolean = true, onClick: () -> Unit = {}) {
        setContent {
            PlatformPredictiveBackHandler(enabled, { calls.starts++ }, { calls.progress += it },
                { calls.commits++ }, { calls.cancels++ })
            Box(Modifier.size(300.dp).testTag("host").predictiveBackEdgeSwipe().clickable(onClick = onClick))
        }
    }

    @Test fun edge_drag_previews_then_commits_once() = runComposeUiTest {
        val calls = Calls()
        content(calls)
        onNodeWithTag("host").performTouchInput {
            down(Offset(1f, centerY))
            moveTo(Offset(width * 0.55f, centerY), delayMillis = 500)
        }
        runOnIdle {
            assertEquals(1, calls.starts)
            assertEquals(0, calls.commits)
            assertTrue(calls.progress.last() > 0.5f)
        }
        onNodeWithTag("host").performTouchInput { up() }
        runOnIdle { assertEquals(1, calls.commits); assertEquals(0, calls.cancels) }
    }

    @Test fun dragging_back_to_the_edge_cancels() = runComposeUiTest {
        val calls = Calls()
        content(calls)
        onNodeWithTag("host").performTouchInput {
            down(Offset(1f, centerY))
            moveTo(Offset(width * 0.65f, centerY), delayMillis = 500)
            moveTo(Offset(2f, centerY), delayMillis = 500)
            up()
        }
        runOnIdle { assertEquals(1, calls.starts); assertEquals(1, calls.cancels); assertEquals(0, calls.commits) }
    }

    @Test fun mouse_drag_uses_the_same_preview() = runComposeUiTest {
        val calls = Calls()
        content(calls)
        onNodeWithTag("host").performMouseInput {
            moveTo(Offset(1f, centerY))
            press()
            moveTo(Offset(width * 0.6f, centerY), delayMillis = 500)
            release()
        }
        runOnIdle { assertEquals(1, calls.starts); assertEquals(1, calls.commits) }
    }

    @Test fun touches_outside_the_edge_and_vertical_drags_do_not_navigate() = runComposeUiTest {
        val calls = Calls()
        content(calls)
        onNodeWithTag("host").performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.8f, centerY), durationMillis = 500)
            swipe(Offset(1f, height * 0.1f), Offset(1f, height * 0.8f), durationMillis = 500)
        }
        runOnIdle { assertEquals(0, calls.starts); assertEquals(0, calls.commits) }
    }

    @Test fun an_edge_tap_still_reaches_the_content() = runComposeUiTest {
        val calls = Calls()
        var clicks = 0
        content(calls, onClick = { clicks++ })
        onNodeWithTag("host").performTouchInput { click(Offset(1f, centerY)) }
        runOnIdle { assertEquals(1, clicks); assertEquals(0, calls.starts) }
    }

    @Test fun a_disabled_handler_leaves_content_interactive() = runComposeUiTest {
        val calls = Calls()
        content(calls, enabled = false)
        onNodeWithTag("host").performTouchInput {
            swipe(Offset(1f, centerY), Offset(width * 0.7f, centerY), durationMillis = 500)
        }
        runOnIdle { assertEquals(0, calls.starts); assertEquals(0, calls.commits) }
    }

    @Test fun a_second_finger_cancels_the_preview() = runComposeUiTest {
        val calls = Calls()
        content(calls)
        onNodeWithTag("host").performTouchInput {
            down(Offset(1f, centerY))
            moveTo(Offset(width * 0.5f, centerY), delayMillis = 500)
            down(1, Offset(width * 0.7f, centerY))
            up(1)
            up(0)
        }
        runOnIdle { assertEquals(1, calls.cancels); assertEquals(0, calls.commits) }
    }

    @Test fun removing_the_gesture_area_cancels_the_preview() = runComposeUiTest {
        val calls = Calls()
        var mounted by mutableStateOf(true)
        setContent {
            PlatformPredictiveBackHandler(true, { calls.starts++ }, {}, { calls.commits++ }, { calls.cancels++ })
            if (mounted) Box(Modifier.size(300.dp).testTag("host").predictiveBackEdgeSwipe())
        }
        onNodeWithTag("host").performTouchInput {
            down(Offset(1f, centerY))
            moveTo(Offset(width * 0.5f, centerY), delayMillis = 500)
        }
        runOnIdle { mounted = false }
        runOnIdle { assertEquals(1, calls.cancels); assertEquals(0, calls.commits) }
    }
}
