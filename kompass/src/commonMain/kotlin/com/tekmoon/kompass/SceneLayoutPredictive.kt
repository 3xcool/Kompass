package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * Single-pane layout that follows a predictive Back gesture.
 *
 * While the user drags, the entry below the top slides in and the top slides out, and the back stack
 * does not change. The layout reads [KompassNavController.predictiveBack], so the graph holds no state and
 * can stay an `object`. Install [KompassPredictiveBackHandler] to feed the gesture:
 *
 * ```
 * object MyGraph : KompassNavigationGraph {
 *     override val sceneLayout = SceneLayoutPredictive()
 *     // ...
 * }
 *
 * @Composable
 * fun MyApp() {
 *     val navController = rememberKompassNavController(Home)
 *     KompassPredictiveBackHandler(navController)
 *     KompassNavigationHost(navController, persistentListOf(MyGraph))
 * }
 * ```
 *
 * Without a gesture in progress this behaves the same as [SceneLayoutDefaultAnimatedSinglePane]: it
 * animates toward the top of the back stack with the graph's own [SceneTransition].
 *
 * Android and iOS feed native gestures. Desktop and web recognize a primary mouse or touch drag
 * starting in the leftmost 24 dp of this layout. Drag back to cancel, or release past 35% of the
 * width (or with a quick forward swipe) to commit. ESC completes an ordinary animated back.
 * The in-content gesture does not read or modify browser history.
 *
 * @param transition Motion to apply. Defaults to the transition of the graph that owns the target.
 */
data class SceneLayoutPredictive(
    val transition: SceneTransition? = null,
) : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<KompassEntry>,
        resolve: (KompassEntry) -> Pair<KompassNavigationGraph, Destination>,
        navController: KompassNavController,
        direction: NavDirection,
    ) {
        val gesture = navController.predictiveBack
        // The top entry stays on the stack during the gesture, so the motion targets the entry below
        // it. A gesture that outlives its target falls back to the top, rather than animating nowhere.
        val target = gesture.targetEntryId
            ?.let { id -> backStack.firstOrNull { it.id == id } }
            ?: backStack.last()
        // No command has run yet, so the controller still reports the direction that pushed the top
        // entry. Without this the gesture would play the push animation backwards.
        val motion = if (gesture.isActive) NavDirection.Pop else direction

        SeekableScene(
            target = target,
            progress = gesture.progress,
            backStack = backStack,
            resolve = resolve,
            navController = navController,
            direction = motion,
            transition = transition,
            label = "KompassPredictive",
            completeAtFullProgress = false,
            modifier = Modifier.platformPredictiveBackInput(),
        )
    }
}
