package com.tekmoon.kompass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs

/** Android and iOS supply native gestures; desktop and web attach an in-content edge drag. */
@Composable
internal expect fun Modifier.platformPredictiveBackInput(): Modifier

@Composable
internal fun Modifier.predictiveBackEdgeSwipe(): Modifier {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
        ?: return this
    val input = remember(dispatcher) { EdgeSwipeNavigationInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose {
            input.cancel()
            if (input.isAttached) dispatcher.removeInput(input)
        }
    }
    return pointerInput(input) {
        val edgeWidth = 24.dp.toPx()
        val flingVelocity = 1000.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            if (!input.hasEnabledHandlers || down.position.x !in 0f..edgeWidth || size.width <= 0) {
                return@awaitEachGesture
            }
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            try {
                while (true) {
                    // Claim only horizontal drags from the edge, before descendants scroll.
                    // Taps and predominantly vertical motion keep their normal input path.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || !input.hasEnabledHandlers ||
                        event.changes.any { it.id != down.id && it.pressed }
                    ) break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - down.position
                    if (!dragging) {
                        if (!change.pressed || abs(delta.y) > viewConfiguration.touchSlop ||
                            delta.x < -viewConfiguration.touchSlop
                        ) break
                        if (delta.x <= viewConfiguration.touchSlop) continue
                        dragging = true
                        input.start()
                    }
                    val progress = (delta.x / size.width).coerceIn(0f, 1f)
                    change.consume()
                    input.progress(progress)
                    if (!change.pressed) {
                        val speed = velocity.calculateVelocity().x
                        val shouldCommit = speed > -flingVelocity &&
                            (progress >= 0.35f || (progress >= 0.05f && speed >= flingVelocity))
                        if (shouldCommit) input.commit() else input.cancel()
                        break
                    }
                }
            } finally {
                // Pointer cancellation, a second finger, or removal must never leave a preview active.
                input.cancel()
            }
        }
    }
}

private class EdgeSwipeNavigationInput : NavigationEventInput() {
    var hasEnabledHandlers: Boolean = false
        private set
    var isAttached: Boolean = false
        private set
    private var dragging = false

    override fun onAdded(dispatcher: NavigationEventDispatcher) {
        isAttached = true
    }

    override fun onRemoved() {
        isAttached = false
        dragging = false
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        this.hasEnabledHandlers = hasEnabledHandlers
    }

    fun start() {
        if (!isAttached || !hasEnabledHandlers || dragging) return
        dragging = true
        dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
    }

    fun progress(value: Float) {
        if (isAttached && dragging) {
            dispatchOnBackProgressed(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = value))
        }
    }

    fun commit() {
        if (!isAttached || !dragging) return
        dragging = false
        dispatchOnBackCompleted()
    }

    fun cancel() {
        if (!isAttached || !dragging) return
        dragging = false
        dispatchOnBackCancelled()
    }
}
