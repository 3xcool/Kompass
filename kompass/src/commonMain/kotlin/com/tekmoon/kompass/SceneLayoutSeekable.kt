package com.tekmoon.kompass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList

/**
 * Single-pane animation with externally controlled visual progress (0..1), or automatic
 * completion when progress is null. Assign this to NavigationGraph.sceneLayout.
 *
 * Progress controls an already committed navigation transition, not the back stack. Moving
 * back to zero does not undo navigation. Gesture handling and predictive Back are separate.
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
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection,
    ) {
        val target = backStack.last()
        // Animate occurrence keys: consuming results or changing arguments must not restart motion.
        val seekable = remember(navController) { SeekableTransitionState(target.id) }
        val animation = rememberTransition(seekable, label = "KompassSeekable")
        val entries = remember(navController) { mutableMapOf<String, BackStackEntry>() }
        val rendered = remember(navController) { mutableSetOf<String>() }
        backStack.forEach { entries[it.id] = it }
        val spec = transition ?: resolve(target).first.sceneTransition ?: SceneTransitionDefault()
        LaunchedEffect(target.id, progress) {
            when (progress) {
                null -> seekable.animateTo(target.id)
                1f -> seekable.snapTo(target.id)
                else -> seekable.seekTo(progress, target.id)
            }
        }
        animation.AnimatedContent(
            contentKey = { it },
            transitionSpec = {
                spec.transition(SceneTransitionContext(entries.getValue(initialState), entries.getValue(targetState), direction))
            },
        ) { id ->
            val entry = entries.getValue(id)
            DisposableEffect(id) {
                rendered.add(id)
                onDispose { rendered.remove(id) }
            }
            val (graph, destination) = resolve(entry)
            graph.Content(entry, destination, navController)
        }
        SideEffect {
            entries.keys.retainAll(backStack.map { it.id }.toSet() + rendered + seekable.currentState + seekable.targetState)
        }
    }
}
