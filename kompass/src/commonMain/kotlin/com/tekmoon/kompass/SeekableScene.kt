@file:OptIn(ExperimentalKompassSharedTransitionApi::class)

package com.tekmoon.kompass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * Shared body of the single-pane layouts that seek.
 *
 * [SceneLayoutSeekable] and [SceneLayoutPredictive] differ only in where the target entry and the
 * progress come from. Everything after that is the same, so it lives here and neither layout can
 * drift away from the other.
 *
 * @param target The entry the motion moves toward. It is not always the top of the back stack: a
 * predictive Back gesture targets the entry below the top while the top is still on the stack.
 * @param progress Visual progress from 0 to 1, or null to complete the motion automatically.
 */
@Composable
internal fun SeekableScene(
    target: KompassEntry,
    progress: Float?,
    backStack: ImmutableList<KompassEntry>,
    resolve: (KompassEntry) -> Pair<KompassNavigationGraph, Destination>,
    navController: KompassNavController,
    direction: NavDirection,
    transition: SceneTransition?,
    label: String,
    completeAtFullProgress: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Animate occurrence keys: consuming results or changing arguments must not restart motion.
    val seekable = remember(navController) { SeekableTransitionState(target.id) }
    val animation = rememberTransition(seekable, label = label)
    val entries = remember(navController) { mutableMapOf<String, KompassEntry>() }
    val rendered = remember(navController) { mutableSetOf<String>() }
    backStack.forEach { entries[it.id] = it }
    val spec = transition ?: resolve(target).first.sceneTransition ?: SceneTransitionDefault()
    // A cancellation rewinds the original segment, including its direction.
    var segmentDirection by remember(navController) { mutableStateOf(direction) }
    val rewinding = progress == null && target.id == seekable.currentState &&
        seekable.currentState != seekable.targetState
    val motionDirection = if (rewinding) segmentDirection else direction
    SideEffect { segmentDirection = motionDirection }
    LaunchedEffect(target.id, progress) {
        when (progress) {
            null -> {
                if (target.id == seekable.currentState && seekable.currentState != seekable.targetState) {
                    val duration = (seekable.fraction * animation.totalDurationNanos / 1_000_000)
                        .toInt().coerceAtLeast(0)
                    coroutineScope {
                        // The callback of animate does not suspend, and seekTo does, so each frame
                        // launches instead of calling. Do not turn this into a direct call. One
                        // launch per frame keeps the order, because they all resume on the frame
                        // dispatcher of this scope, and a late frame only loses to the next value.
                        animate(seekable.fraction, 0f, animationSpec = tween(duration)) { value, _ ->
                            launch { seekable.seekTo(value) }
                        }
                    }
                    seekable.snapTo(target.id)
                } else {
                    seekable.animateTo(target.id)
                }
            }
            1f -> {
                // Previewing 100% of a gesture is not a committed navigation.
                if (completeAtFullProgress) seekable.snapTo(target.id)
                else seekable.seekTo(progress, target.id)
            }
            else -> seekable.seekTo(progress, target.id)
        }
    }
    animation.AnimatedContent(
        modifier = modifier,
        contentKey = { it },
        transitionSpec = {
            spec.transition(SceneTransitionContext(entries.getValue(initialState), entries.getValue(targetState), motionDirection))
        },
    ) { id ->
        val entry = entries.getValue(id)
        DisposableEffect(id) {
            rendered.add(id)
            onDispose { rendered.remove(id) }
        }
        val (graph, destination) = resolve(entry)
        CompositionLocalProvider(LocalKompassAnimatedVisibilityScope provides this) {
            graph.Content(entry, destination, navController)
        }
    }
    SideEffect {
        entries.keys.retainAll(backStack.map { it.id }.toSet() + rendered + seekable.currentState + seekable.targetState)
    }
}
