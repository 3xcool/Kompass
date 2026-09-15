package com.tekmoon.kompass

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Maps the Android system predictive Back gesture onto the Kompass callbacks.
 *
 * From API 34 the system streams the drag, so [onProgress] runs many times and the user can abandon
 * the gesture. Below API 34 the system sends no drag: the flow completes on a plain back press, so
 * [onStart] and [onCommit] run back to back and the pop animates at full speed. That matches what
 * [PlatformBackHandler] already did, so an older device loses nothing.
 *
 * Turning [enabled] off during a gesture cancels the preview here. AndroidX keeps the gesture
 * running in that case, and the system still delivers the completion to the handler that owns the
 * gesture, so without this the user would let go and get a pop from a handler that is off.
 */
// runBackGesture collects the flow synchronously; the lint check cannot follow delegation.
@SuppressLint("NoCollectCallFound")
@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val gate = remember { BackGestureGate() }
    val latestCancel by rememberUpdatedState(onCancel)
    SideEffect {
        if (!enabled && gate.finish()) latestCancel()
    }
    PredictiveBackHandler(enabled) { events ->
        runBackGesture(events, gate, onStart, onProgress, onCommit, onCancel)
    }
}

/**
 * Tracks which caller owns the end of one gesture.
 *
 * A gesture ends once. The end comes from the user, or from the handler being turned off while the
 * finger is still down. Whichever happens first reports it, and the other one reports nothing.
 */
internal class BackGestureGate {

    /** True between the start of a gesture and the call that ends it. */
    var isLive: Boolean = false
        private set

    fun start() {
        isLive = true
    }

    /** Ends the gesture. Returns true only for the call that owns the end. */
    fun finish(): Boolean {
        if (!isLive) return false
        isLive = false
        return true
    }
}

/**
 * Turns one back gesture into the Kompass callbacks.
 *
 * This is separate from the composable so it can be tested without a composition: the gesture rules
 * live here, and [PlatformPredictiveBackHandler] only supplies the event source and the gate.
 *
 * A completed gesture reports every drag step and then commits once. An abandoned gesture cancels
 * the collecting coroutine, so this reports the cancellation and lets it travel. Swallowing it would
 * leave the caller's scope alive after the user let go. A gesture that [gate] already ended reports
 * nothing further, even though the events keep arriving.
 */
internal suspend fun runBackGesture(
    events: Flow<BackEventCompat>,
    gate: BackGestureGate,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    gate.start()
    onStart()
    try {
        events.collect { event -> if (gate.isLive) onProgress(event.progress) }
        if (gate.finish()) onCommit()
    } catch (cause: CancellationException) {
        if (gate.finish()) onCancel()
        throw cause
    }
}

@Composable
internal actual fun Modifier.platformPredictiveBackInput(): Modifier = this
