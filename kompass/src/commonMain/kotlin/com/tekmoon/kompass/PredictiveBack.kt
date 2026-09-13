package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Visual state of a back gesture that the user started but did not complete.
 *
 * An unfinished gesture is not navigation. It never enters [NavigationState], it is never written by
 * [NavController.saveNavigationState], and it never reaches the reducer. Only a completed gesture
 * changes the back stack, and it does that through the ordinary [NavController.pop] path. A
 * cancelled gesture costs nothing: no command runs, no scope is cleared, and no owner is disposed.
 *
 * Each controller owns one instance. Read it at [NavController.predictiveBack] and render it with a
 * layout that can seek, such as [SceneLayoutPredictive].
 *
 * Mutation is confined to the UI thread, the same as the controller.
 */
@Stable
class PredictiveBackState internal constructor() {

    /**
     * Visual progress of the gesture, from 0 to 1, or null when no gesture is in progress.
     *
     * Read this to fade, scale or dim your own content while the user drags.
     */
    var progress: Float? by mutableStateOf(null)
        private set

    /**
     * Occurrence ID of the entry the gesture moves toward, or null when no gesture is in progress.
     *
     * This is the [BackStackEntry.id] of the entry below the top one, captured when the gesture
     * started. It stays fixed for the whole gesture, so a back stack change in the middle of a drag
     * cannot move the target.
     */
    var targetEntryId: String? by mutableStateOf(null)
        private set

    /** True while a gesture is in progress. */
    val isActive: Boolean get() = targetEntryId != null

    internal var sourceEntryId: String? = null
        private set

    internal fun start(targetEntryId: String, sourceEntryId: String? = null) {
        this.sourceEntryId = sourceEntryId
        this.targetEntryId = targetEntryId
        progress = 0f
    }

    internal fun update(fraction: Float) {
        if (targetEntryId == null) return
        progress = fraction.coerceIn(0f, 1f)
    }

    internal fun finish() {
        sourceEntryId = null
        targetEntryId = null
        progress = null
    }
}

/**
 * Platform source of the back gesture.
 *
 * Android uses the system predictive Back gesture. iOS uses Compose's native edge-swipe dispatcher.
 * Desktop and web receive ESC through the Compose dispatcher; [SceneLayoutPredictive] also provides
 * an in-content edge drag on those targets. Browser history remains the host application's concern.
 *
 * @param enabled Whether the handler listens for the gesture.
 * @param onStart Called once when the gesture starts.
 * @param onProgress Called with a fraction from 0 to 1 while the user drags.
 * @param onCommit Called once when the user completes the gesture.
 * @param onCancel Called once when the user abandons the gesture.
 */
@Composable
expect fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
)

/**
 * Drives [NavController.predictiveBack] from the platform back gesture, and pops the controller when
 * the user completes it.
 *
 * Use this **instead of** [KompassBackHandler] for the same back action on this controller.
 * Enabled handlers compete for the same event. A separate, root-only handler can dismiss the flow.
 * On desktop/web, let Compose receive ESC rather than intercepting and forwarding it to a channel.
 *
 * The handler turns itself off when the back stack cannot be popped, so the platform keeps its own
 * behaviour at the root. On Android that closes the activity. To run your own code at the root
 * instead, add a second handler for that case only:
 *
 * ```
 * KompassPredictiveBackHandler(navController)
 * KompassBackHandler(enabled = !navController.canGoBack()) { onDismiss() }
 * ```
 *
 * A gesture previews one step. If another action changes the back stack while the finger is down,
 * the preview no longer matches the stack, so completing the gesture pops nothing. The screen the
 * user saw arriving is the screen they get.
 *
 * Pair it with a layout that can seek. Without one the gesture still pops, but it does not animate:
 *
 * ```
 * object MyGraph : NavigationGraph {
 *     override val sceneLayout = SceneLayoutPredictive()
 *     // ...
 * }
 * ```
 *
 * @param navController The controller this gesture pops.
 * @param enabled Whether the handler listens for the gesture.
 */
@Composable
fun KompassPredictiveBackHandler(
    navController: NavController,
    enabled: Boolean = true,
) {
    val gesture = navController.predictiveBack
    PlatformPredictiveBackHandler(
        enabled = enabled && navController.canGoBack(),
        onStart = {
            // Read the stack at gesture time, so a stale composition cannot pick the wrong target.
            val stack = navController.backStack
            if (stack.size > 1) gesture.start(stack[stack.size - 2].id, stack.last().id)
        },
        onProgress = gesture::update,
        onCommit = {
            val stack = navController.backStack
            // Only pop when the stack still matches what the user previewed. Another action may have
            // changed it mid-gesture, and popping then would skip past the screen on screen.
            //
            // Pop before finish. The gesture target then equals the new top, so the layout keeps the
            // same target across the commit and the animation finishes from the fraction it reached.
            if (stack.size > 1 && stack.last().id == gesture.sourceEntryId &&
                stack[stack.size - 2].id == gesture.targetEntryId
            ) {
                navController.popIfCan()
            }
            gesture.finish()
        },
        onCancel = gesture::finish,
    )
}
