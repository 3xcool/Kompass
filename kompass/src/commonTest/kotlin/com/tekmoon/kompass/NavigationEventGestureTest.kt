package com.tekmoon.kompass

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the shared event adapter on every test target, including the browser. */
class NavigationEventGestureTest {
    private class Input : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent())
        fun progress(value: Float) = dispatchOnBackProgressed(NavigationEvent(progress = value))
        fun commit() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
    }

    @Test fun a_gesture_can_cancel_then_a_discrete_back_can_commit() {
        val calls = mutableListOf<String>()
        val dispatcher = NavigationEventDispatcher()
        val input = Input()
        val handler = KompassNavigationEventHandler(
            onStart = { calls += "start" },
            onProgress = { calls += "progress:$it" },
            onCommit = { calls += "commit" },
            onCancel = { calls += "cancel" },
        )
        handler.isBackEnabled = true
        dispatcher.addHandler(handler)
        dispatcher.addInput(input)
        try {
            input.start()
            input.progress(0.7f)
            input.cancel()
            input.commit()
            assertEquals(listOf("start", "progress:0.0", "progress:0.7", "cancel", "start", "commit"), calls)
        } finally {
            dispatcher.dispose()
        }
    }
}
