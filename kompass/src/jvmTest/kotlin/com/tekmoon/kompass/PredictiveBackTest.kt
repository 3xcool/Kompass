@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.tekmoon.kompass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.*

class PredictiveBackTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private class Probe : ViewModel() {
        var clears = 0
        override fun onCleared() { clears++ }
    }

    /** Slow and opaque, so a half-finished gesture has a readable position. */
    private val motion = SceneTransitionDefault(durationMs = 1000, fadeEnabled = false)

    private class Graph(
        val probes: MutableMap<String, Probe>,
        override val sceneLayout: SceneLayout,
    ) : NavigationGraph {
        override fun canResolveDestination(destinationId: String) = true
        override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
        @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
            probes[entry.id] = viewModel { Probe() }
            Box(Modifier.size(100.dp)) { BasicText("screen:${entry.destinationId}") }
        }
    }

    private fun xOf(scope: ComposeUiTest, text: String): Float =
        scope.onNodeWithText(text).fetchSemanticsNode().positionInRoot.x

    @Test fun a_gesture_moves_the_previous_screen_in_without_touching_the_back_stack() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val graph = Graph(probes, SceneLayoutPredictive(motion))
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }

        lateinit var root: BackStackEntry
        mainClock.autoAdvance = false
        runOnIdle { root = nav.currentEntry; nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        mainClock.advanceTimeBy(2000)
        waitForIdle()

        runOnIdle {
            nav.predictiveBack.start(root.id)
            nav.predictiveBack.update(0.6f)
        }
        mainClock.advanceTimeBy(64)

        // The gesture is visual only. Nothing was dispatched, so the stack and the owners are intact.
        runOnIdle {
            assertEquals(2, nav.backStack.size)
            assertEquals("b", nav.currentEntry.destinationId)
            assertEquals(0, probes.getValue(root.id).clears)
        }

        onNodeWithText("screen:a").assertExists()
        // A Pop brings the previous screen in from the left and pushes the top one out to the right.
        // A Push would do the opposite, so these two signs prove the gesture forces NavDirection.Pop.
        assertTrue(xOf(this, "screen:a") < 0f, "The previous screen must enter from the left during a Back gesture")
        assertTrue(xOf(this, "screen:b") > 0f, "The top screen must leave to the right during a Back gesture")
    }

    @Test fun an_abandoned_gesture_restores_the_top_screen_and_clears_nothing() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val segments = mutableListOf<SceneTransitionContext>()
        val recordedMotion = object : SceneTransition {
            override fun transition(context: SceneTransitionContext): androidx.compose.animation.ContentTransform {
                segments.add(context)
                return motion.transition(context)
            }
        }
        val graph = Graph(probes, SceneLayoutPredictive(recordedMotion))
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }

        lateinit var root: BackStackEntry
        mainClock.autoAdvance = false
        runOnIdle { root = nav.currentEntry; nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        mainClock.advanceTimeBy(2000)
        waitForIdle()

        runOnIdle { nav.predictiveBack.start(root.id); nav.predictiveBack.update(0.6f) }
        mainClock.advanceTimeBy(64)
        val dragged = xOf(this, "screen:a")

        var previousTopX = xOf(this, "screen:b")
        runOnIdle { segments.clear(); nav.predictiveBack.finish() }
        repeat(45) {
            mainClock.advanceTimeByFrame()
            val topX = xOf(this, "screen:b")
            assertTrue(topX >= -0.5f, "Cancelling must not move the current screen past its resting position")
            assertTrue(topX <= previousTopX + 0.5f, "Cancelling must continuously return the current screen, without a jump")
            previousTopX = topX
            if (it < 10) runOnIdle {
                assertTrue(segments.filter { it.from.id != it.to.id }.all {
                    it.from.destinationId == "b" && it.to.destinationId == "a" && it.direction == NavDirection.Pop
            }, "Cancellation must rewind the original Pop, without introducing a replacement transition")
            }
        }
        mainClock.advanceTimeBy(2000)
        waitForIdle()

        runOnIdle {
            assertEquals(2, nav.backStack.size)
            assertEquals("b", nav.currentEntry.destinationId)
            // A cancel runs no command, so neither side loses its ViewModel.
            assertEquals(0, probes.getValue(root.id).clears)
            assertEquals(0, probes.getValue(nav.currentEntry.id).clears)
        }
        assertTrue(dragged < 0f, "The gesture must have moved the previous screen before it was abandoned")
        onNodeWithText("screen:b").assertExists()
    }

    @Test fun the_predictive_layout_wires_edge_drags_to_the_controller() = runComposeUiTest {
        lateinit var nav: NavController
        var sceneWidth = 0f
        val graph = Graph(mutableMapOf(), SceneLayoutPredictive(motion))
        setContent {
            sceneWidth = with(LocalDensity.current) { 100.dp.toPx() }
            nav = rememberNavController(A)
            KompassPredictiveBackHandler(nav)
            KompassNavigationHost(nav, persistentListOf(graph))
        }
        mainClock.autoAdvance = false
        runOnIdle { nav.navigate(B.toBackStackEntry()) }
        mainClock.advanceTimeBy(2000)
        // Skiko test injection runs on the caller thread; real window events arrive on the UI thread.
        runOnUiThread {
            onRoot().performTouchInput {
                down(Offset(1f, 10f))
                moveTo(Offset(sceneWidth * 0.6f, 10f), delayMillis = 500)
            }
        }
        mainClock.advanceTimeBy(64)
        runOnIdle {
            assertTrue(nav.predictiveBack.isActive)
            assertEquals(2, nav.backStack.size)
        }
        runOnUiThread {
            onRoot().performTouchInput {
                moveTo(Offset(1f, 10f), delayMillis = 500)
                up()
            }
        }
        mainClock.advanceTimeBy(2000)
        runOnIdle { assertFalse(nav.predictiveBack.isActive); assertEquals(2, nav.backStack.size) }
        runOnUiThread {
            onRoot().performTouchInput {
                swipe(Offset(1f, 10f), Offset(sceneWidth * 0.8f, 10f), durationMillis = 500)
            }
        }
        mainClock.advanceTimeBy(2000)
        runOnIdle { assertEquals(1, nav.backStack.size); assertEquals("a", nav.currentEntry.destinationId) }
    }

    @Test fun a_fully_previewed_gesture_can_still_rewind_before_commit() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val graph = Graph(probes, SceneLayoutPredictive(motion))
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        mainClock.autoAdvance = false
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        mainClock.advanceTimeBy(2000)
        runOnIdle { nav.predictiveBack.start(nav.backStack.first().id); nav.predictiveBack.update(1f) }
        mainClock.advanceTimeBy(64)
        runOnIdle { nav.predictiveBack.update(0.4f) }
        mainClock.advanceTimeBy(64)
        assertTrue(xOf(this, "screen:a") < 0f, "Even a full preview must remain seekable until commit")
        runOnIdle { nav.predictiveBack.finish() }
        mainClock.advanceTimeBy(2000)
        runOnIdle { assertEquals("b", nav.currentEntry.destinationId); assertEquals(2, nav.backStack.size) }
        onNodeWithText("screen:b").assertExists()
        onNodeWithText("screen:a").assertDoesNotExist()
    }

    @Test fun a_completed_gesture_pops_once_and_clears_the_outgoing_owner() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val graph = Graph(probes, SceneLayoutPredictive(motion))
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }

        lateinit var root: BackStackEntry
        lateinit var top: BackStackEntry
        mainClock.autoAdvance = false
        runOnIdle { root = nav.currentEntry; nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        runOnIdle { top = nav.currentEntry }

        runOnIdle { nav.predictiveBack.start(root.id); nav.predictiveBack.update(0.6f) }
        mainClock.advanceTimeBy(64)

        // What KompassPredictiveBackHandler does on commit: pop, then release the gesture.
        runOnIdle { nav.popIfCan(); nav.predictiveBack.finish() }
        mainClock.advanceTimeBy(2000)
        waitForIdle()

        runOnIdle {
            assertEquals(1, nav.backStack.size)
            assertEquals("a", nav.currentEntry.destinationId)
            assertEquals(root.id, nav.currentEntry.id)
            assertEquals(1, probes.getValue(top.id).clears)
            assertEquals(0, probes.getValue(root.id).clears)
        }
        onNodeWithText("screen:a").assertExists()
        onNodeWithText("screen:b").assertDoesNotExist()
    }

    @Test fun the_layout_animates_ordinary_navigation_when_no_gesture_runs() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val graph = Graph(probes, SceneLayoutPredictive(motion))
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }

        mainClock.autoAdvance = false
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        mainClock.advanceTimeBy(64)
        // A push with no gesture keeps the ordinary direction: the new screen comes in from the right.
        assertTrue(xOf(this, "screen:b") > 0f, "A push must bring the new screen in from the right")

        mainClock.advanceTimeBy(2000)
        waitForIdle()
        runOnIdle { assertEquals("b", nav.currentEntry.destinationId) }
        onNodeWithText("screen:a").assertDoesNotExist()
    }
}
