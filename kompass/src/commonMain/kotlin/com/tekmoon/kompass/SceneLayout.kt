package com.tekmoon.kompass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList


/**
 * Defines how navigation back stack entries are laid out and rendered.
 *
 * A [SceneLayout] is responsible for deciding:
 * - Which back stack entries are visible
 * - How many panes are shown
 * - How transitions and animations are applied
 *
 * This is the only abstraction that:
 * - Knows about available screen size
 * - Chooses between single-pane and multi-pane layouts
 * - Controls how and when animations occur
 *
 * Navigation rules, destination resolution, and UI content
 * are intentionally handled elsewhere.
 */
interface SceneLayout {

    /**
     * Renders the visible portion of the navigation back stack.
     *
     * @param backStack Immutable list of current [BackStackEntry] instances.
     * The last entry represents the active destination.
     *
     * @param resolve Function used to resolve a [BackStackEntry] into
     * its owning [NavigationGraph] and [Destination].
     *
     * @param navController Controller used to perform navigation actions
     * from rendered destinations.
     *
     * @param direction Direction of navigation used to drive animations.
     */
    @Composable
    fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    )
}

/**
 * Simple single-pane layout with no animations.
 *
 * Only the top-most back stack entry is rendered.
 */
object SceneLayoutSinglePane : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    ) {
        val entry = backStack.last()
        val (graph, destination) = remember(entry) { resolve(entry) }

        graph.Content(
            entry = entry,
            destination = destination,
            navController = navController
        )
    }
}

/**
 * Single-pane layout with animated transitions.
 *
 * Uses [AnimatedContent] and the default scene transition
 * to animate between destinations.
 */
object SceneLayoutDefaultAnimatedSinglePane : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    ) {
        val entry = backStack.last()
        val (graph, destination) = remember(entry) { resolve(entry) }

        AnimatedContent(
            targetState = entry,
            transitionSpec = directionalTransition<BackStackEntry>(
                direction = direction,
                transition = SceneTransitionDefault()
            ),
            label = "SinglePane"
        ) {
            graph.Content(
                entry = entry,
                destination = destination,
                navController = navController
            )
        }
    }
}

/**
 * Adaptive list-detail layout.
 *
 * Behavior:
 * - In compact widths or when only one entry exists, renders a single pane
 * - In expanded widths, renders a static master pane and animated detail pane
 *
 * This layout is suitable for tablets, desktops, and foldables.
 */
data class SceneLayoutListDetail(
    val compactWidthThreshold: Dp = 600.dp,
    val transition: SceneTransition = SceneTransitionDefault(300)
) : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    ) {
        BoxWithConstraints {
            val isCompact = maxWidth < compactWidthThreshold

            if (isCompact || backStack.size == 1) {
                val entry = backStack.last()
//                val (graph, destination) = remember(entry) { resolve(entry) }

                AnimatedContent(
                    modifier = Modifier.fillMaxSize(),
                    targetState = entry,
                    transitionSpec = directionalTransition<BackStackEntry>(
                        direction = direction,
                        transition = transition
                    ),
                    label = "DetailOnly",
//                    contentKey = { it to it.destinationId }
                ) { animatedEntry ->
                    val (graph, destination) = remember(animatedEntry) { resolve(animatedEntry) }
                    graph.Content(
                        entry = animatedEntry,
                        destination = destination,
                        navController = navController
                    )
                }
            } else {
                val master = backStack.first()
                val detail = backStack.last()

                val (masterGraph, masterDest) = remember(master) { resolve(master) }

                // By calling here we will show the next screen before the transition animation is triggered
//                val (detailGraph, detailDest) = remember(detail) { resolve(detail) }

                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.weight(0.35f)) {
                        masterGraph.Content(
                            entry = master,
                            destination = masterDest,
                            navController = navController
                        )
                    }

                    Box(Modifier.weight(0.65f)) {
                        AnimatedContent(
                            targetState = detail,
                            transitionSpec = directionalTransition<BackStackEntry>(
                                direction = direction,
                                transition = transition
                            ),
                            label = "DetailPane",
//                            contentKey = { it.destinationId}
                        ) { animatedEntry ->
//                          // Important
                            // We must use animatedEntry, cause we need to render the new screen after the animation is finished
                            val (detailGraph, detailDest) = remember(animatedEntry) { resolve(animatedEntry) }
                            detailGraph.Content(
                                entry = animatedEntry,
                                destination = detailDest,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vertical slide transition.
 *
 * Useful for modal-style navigation or bottom-sheet-like flows.
 *
 * @param durationMs Duration of the animation in milliseconds.
 */
data class SceneTransitionVertical(
    val durationMs: Int = 300
) : SceneTransition {

    /**
     * Creates a vertical enter and exit animation based on navigation direction.
     *
     * @param direction Direction of navigation driving the animation.
     *
     * @return A [ContentTransform] describing the vertical transition.
     */
    override fun transition(direction: NavDirection): ContentTransform {
        return when (direction) {
            NavDirection.Push ->
                slideInVertically { fullHeight -> fullHeight } +
                        fadeIn() togetherWith
                        slideOutVertically { fullHeight -> -fullHeight / 3 } +
                        fadeOut()

            NavDirection.Pop ->
                slideInVertically { fullHeight -> -fullHeight / 3 } +
                        fadeIn() togetherWith
                        slideOutVertically { fullHeight -> fullHeight } +
                        fadeOut()
        }
    }
}
