package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList

/**
 * Single-pane animation with externally controlled visual progress (0..1), or automatic
 * completion when progress is null. Assign this to KompassNavigationGraph.sceneLayout.
 *
 * Progress controls an already committed navigation transition, not the back stack. Moving
 * back to zero does not undo navigation. Gesture handling is separate. For the system Back
 * gesture use [SceneLayoutPredictive] with [KompassPredictiveBackHandler] instead.
 * Keep the same layout type mounted while seeking; null resumes from the current fraction.
 * A progress of 1 completes the transition and releases outgoing content.
 */
data class SceneLayoutSeekable(
    val progress: Float? = null,
    val transition: SceneTransition? = null,
) : SceneLayout {
    init { require(progress == null || (progress.isFinite() && progress in 0f..1f)) { "Progress must be between 0 and 1" } }

    @Composable
    override fun Render(
        backStack: ImmutableList<KompassEntry>,
        resolve: (KompassEntry) -> Pair<KompassNavigationGraph, Destination>,
        navController: KompassNavController,
        direction: NavDirection,
    ) {
        SeekableScene(
            target = backStack.last(),
            progress = progress,
            backStack = backStack,
            resolve = resolve,
            navController = navController,
            direction = direction,
            transition = transition,
            label = "KompassSeekable",
        )
    }
}
