package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** Bridges the Compose window's native gestures and keyboard back events to Kompass. */
@Composable
internal fun NavigationEventPredictiveBackHandler(
    enabled: Boolean,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "Kompass requires a Compose window or a LocalNavigationEventDispatcherOwner"
    }.navigationEventDispatcher
    val latestStart by rememberUpdatedState(onStart)
    val latestProgress by rememberUpdatedState(onProgress)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestCancel by rememberUpdatedState(onCancel)
    // Keep registration order stable when callbacks or gesture progress recompose the caller.
    val handler = remember(dispatcher) {
        KompassNavigationEventHandler(
            onStart = { latestStart() },
            onProgress = { latestProgress(it) },
            onCommit = { latestCommit() },
            onCancel = { latestCancel() },
        )
    }
    SideEffect {
        handler.isBackEnabled = enabled
        if (!enabled) handler.cancelGesture()
    }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose {
            handler.remove()
            handler.cancelGesture()
        }
    }
}

internal class KompassNavigationEventHandler(
    private val onStart: () -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onCommit: () -> Unit,
    private val onCancel: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false) {
    private var gestureStarted = false
    private var gestureCancelled = false

    override fun onBackStarted(event: NavigationEvent) {
        gestureCancelled = false
        if (!isBackEnabled) return
        startGesture()
        onProgress(event.progress)
    }

    override fun onBackProgressed(event: NavigationEvent) {
        if (!isBackEnabled || gestureCancelled) return
        startGesture()
        onProgress(event.progress)
    }

    override fun onBackCompleted() {
        if (!isBackEnabled || gestureCancelled) {
            gestureCancelled = false
            return
        }
        // ESC dispatches completion without a preceding start or progress event.
        startGesture()
        gestureStarted = false
        onCommit()
    }

    override fun onBackCancelled() {
        cancelGesture()
        gestureCancelled = false
    }

    fun cancelGesture() {
        if (!gestureStarted) return
        gestureStarted = false
        // Disabling may leave a physical gesture in flight. Ignore its remaining events even if
        // this handler becomes enabled again before the user releases the pointer.
        gestureCancelled = true
        onCancel()
    }

    private fun startGesture() {
        if (gestureStarted) return
        gestureStarted = true
        onStart()
    }
}
