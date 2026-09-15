@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlatformPredictiveBackHandlerTest {
    private class BackInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(progress = 0f))
        fun progress(value: Float) = dispatchOnBackProgressed(NavigationEvent(progress = value))
        fun commit() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
    }

    private class WindowEvents : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val input = BackInput().also { navigationEventDispatcher.addInput(it) }

        @Composable
        fun Content(content: @Composable () -> Unit) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this, content = content)
        }
    }

    @Test fun gesture_callbacks_stay_ordered_across_recomposition_and_cancel_once() = runComposeUiTest {
        val window = WindowEvents()
        val calls = mutableListOf<String>()
        var callbackVersion by mutableStateOf(0)
        setContent {
            window.Content {
                val version = callbackVersion
                PlatformPredictiveBackHandler(
                    enabled = true,
                    onStart = { calls += "start" },
                    onProgress = { calls += "progress:$it" },
                    onCommit = { calls += "commit" },
                    onCancel = { calls += "cancel:$version" },
                )
            }
        }
        runOnIdle { window.input.start(); window.input.progress(0.4f); callbackVersion = 1 }
        runOnIdle { window.input.progress(0.2f); window.input.cancel() }
        runOnIdle {
            assertEquals(listOf("start", "progress:0.0", "progress:0.4", "progress:0.2", "cancel:1"), calls)
        }
    }

    @Test fun a_discrete_back_starts_then_commits_without_progress() = runComposeUiTest {
        val window = WindowEvents()
        val calls = mutableListOf<String>()
        setContent {
            window.Content {
                PlatformPredictiveBackHandler(true,
                    onStart = { calls += "start" },
                    onProgress = { calls += "progress" },
                    onCommit = { calls += "commit" },
                    onCancel = { calls += "cancel" },
                )
            }
        }
        runOnIdle { window.input.commit() }
        runOnIdle { assertEquals(listOf("start", "commit"), calls) }
    }

    @Test fun root_back_takes_over_only_after_the_predictive_pop() = runComposeUiTest {
        val window = WindowEvents()
        var dismisses = 0
        lateinit var nav: NavController
        setContent {
            window.Content {
                nav = rememberKompassNavController(TestDestination.A)
                KompassPredictiveBackHandler(nav)
                KompassBackHandler(enabled = !nav.canGoBack()) { dismisses++ }
            }
        }
        runOnIdle { nav.navigate(TestDestination.B.toKompassBackStackEntry()) }
        runOnIdle { window.input.start(); window.input.progress(0.6f) }
        runOnIdle {
            assertEquals(2, nav.backStack.size)
            assertEquals(0.6f, nav.predictiveBack.progress)
            window.input.cancel()
        }
        runOnIdle {
            assertEquals(2, nav.backStack.size)
            assertFalse(nav.predictiveBack.isActive)
            window.input.commit()
        }
        runOnIdle {
            assertEquals(1, nav.backStack.size)
            assertEquals(0, dismisses)
            window.input.commit()
        }
        runOnIdle { assertEquals(1, dismisses) }
    }

    @Test fun removing_a_handler_cancels_its_preview() = runComposeUiTest {
        val window = WindowEvents()
        var mounted by mutableStateOf(true)
        var cancels = 0
        var commits = 0
        setContent {
            window.Content {
                if (mounted) PlatformPredictiveBackHandler(true, {}, {}, { commits++ }, { cancels++ })
            }
        }
        runOnIdle { window.input.start(); window.input.progress(0.5f); mounted = false }
        runOnIdle { assertEquals(1, cancels); assertEquals(0, commits) }
    }

    @Test fun disabling_a_handler_cancels_its_preview() = runComposeUiTest {
        val window = WindowEvents()
        var enabled by mutableStateOf(true)
        var cancels = 0
        var commits = 0
        setContent {
            window.Content { PlatformPredictiveBackHandler(enabled, {}, {}, { commits++ }, { cancels++ }) }
        }
        runOnIdle { window.input.start(); enabled = false }
        runOnIdle { window.input.progress(0.8f); window.input.commit() }
        runOnIdle { assertEquals(1, cancels); assertEquals(0, commits) }
    }

    @Test fun re_enabling_does_not_commit_an_already_cancelled_gesture() = runComposeUiTest {
        val window = WindowEvents()
        var enabled by mutableStateOf(true)
        var commits = 0
        var cancels = 0
        setContent {
            window.Content { PlatformPredictiveBackHandler(enabled, {}, {}, { commits++ }, { cancels++ }) }
        }
        runOnIdle { window.input.start(); enabled = false }
        runOnIdle { enabled = true }
        runOnIdle { window.input.progress(0.9f); window.input.commit() }
        runOnIdle { assertEquals(0, commits); assertEquals(1, cancels); window.input.commit() }
        runOnIdle { assertEquals(1, commits) }
    }

    @Test fun callback_recomposition_does_not_steal_priority_from_a_later_handler() = runComposeUiTest {
        val window = WindowEvents()
        var version by mutableStateOf(0)
        val calls = mutableListOf<String>()
        setContent {
            window.Content {
                val currentVersion = version
                PlatformPredictiveBackHandler(true, {}, {}, { calls += "outer:$currentVersion" }, {})
                KompassBackHandler { calls += "inner" }
            }
        }
        runOnIdle { version = 1 }
        runOnIdle { window.input.commit() }
        runOnIdle { assertEquals(listOf("inner"), calls) }
    }

    @Test fun a_commit_does_not_pop_a_stack_that_changed_under_the_gesture() = runComposeUiTest {
        val window = WindowEvents()
        lateinit var nav: NavController
        setContent {
            window.Content {
                nav = rememberKompassNavController(TestDestination.A)
                KompassPredictiveBackHandler(nav)
            }
        }
        runOnIdle { nav.navigate(TestDestination.B.toKompassBackStackEntry()) }
        runOnIdle { nav.navigate(TestDestination.C.toKompassBackStackEntry()) }

        // The drag starts on [A, B, C], so the preview targets B.
        runOnIdle { window.input.start(); window.input.progress(0.6f) }
        runOnIdle {
            assertEquals(nav.backStack[1].id, nav.predictiveBack.targetEntryId)
            // Another action pops C while the finger is still down.
            nav.popIfCan()
        }
        runOnIdle {
            assertEquals(listOf("A", "B"), nav.backStack.map { it.destinationId })
            window.input.commit()
        }
        runOnIdle {
            // The preview showed B arriving, so releasing must land on B and never skip to A.
            assertEquals(listOf("A", "B"), nav.backStack.map { it.destinationId })
            assertFalse(nav.predictiveBack.isActive)
        }
    }

    @Test fun a_gesture_does_not_pop_a_replacement_with_the_same_predecessor() = runComposeUiTest {
        val window = WindowEvents()
        lateinit var nav: NavController
        setContent {
            window.Content {
                nav = rememberKompassNavController(TestDestination.A)
                KompassPredictiveBackHandler(nav)
            }
        }
        runOnIdle { nav.navigate(TestDestination.B.toKompassBackStackEntry()) }
        runOnIdle { window.input.start(); window.input.progress(0.6f) }
        runOnIdle {
            // Keep A as the predecessor, but replace the screen the gesture started on.
            nav.pop()
            nav.navigate(TestDestination.C.toKompassBackStackEntry())
        }
        runOnIdle { window.input.commit() }
        runOnIdle {
            assertEquals(listOf("A", "C"), nav.backStack.map { it.destinationId })
            assertFalse(nav.predictiveBack.isActive)
        }
    }

    private enum class TestDestination : Destination {
        A, B, C;
        override val id: String get() = name
    }
}
