package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList


/**
 *
 * Root composable responsible for orchestrating navigation rendering.
 *
 * [KompassNavigationHost] does not implement navigation rules or UI itself.
 * Its responsibility is limited to:
 * - Observing the current [NavigationState]
 * - Resolving destinations through [NavigationGraph]s
 * - Delegating rendering to the active [SceneLayout]
 *
 * This separation ensures that:
 * - Navigation logic remains testable and platform-agnostic
 * - Layout and animation strategies are pluggable
 * - Screen content remains unaware of navigation orchestration
 *
 * @param navController The [NavController] driving navigation state and commands.
 *
 * @param graphs List of [NavigationGraph]s responsible for resolving and rendering
 * destinations present in the back stack.
 */
@Composable
fun KompassNavigationHost(
    navController: NavController,
    graphs: ImmutableList<NavigationGraph>
) {
    val router = remember(graphs) {
        NavigationGraphRouter(graphs)
    }

    val previousState = remember { mutableStateOf<NavigationState?>(null) }

    val direction =
        previousState.value?.let { prev ->
            navController.state.directionFrom(prev)
        } ?: NavDirection.Push

    SideEffect {
        previousState.value = navController.state
    }

    val activeEntry = navController.state.backStack.last()
    val activeGraph = router.resolve(activeEntry).graph

    val layout = activeGraph.sceneLayout ?: SceneLayoutDefaultAnimatedSinglePane

    layout.Render(
        backStack = navController.state.backStack.toImmutableList(),
        resolve = { entry ->
            val resolved = router.resolve(entry)
            resolved.graph to resolved.destination
        },
        navController = navController,
        direction = direction
    )
}


/**
 * For multi pane where the master is static see sample6
 * @Composable
 * override fun SceneLayout(...) {
 *     Row {
 *
 *         // Master -> static
 *         Box { content(masterEntry, masterDestination) }
 *
 *         // Detail -> animated
 *         AnimatedContent(
 *             targetState = detailEntry,
 *             label = "DetailPane"
 *         ) {
 *             content(it, detailDestination)
 *         }
 *     }
 * }
 */


/**
 * Internal router responsible for mapping [BackStackEntry] instances
 * to their owning [NavigationGraph] and resolved [Destination].
 *
 * This class encapsulates graph lookup logic and ensures that:
 * - Each back stack entry is resolved by exactly one graph
 * - Destination resolution is centralized and consistent
 *
 * It is intentionally kept private to prevent leaking graph
 * resolution logic into consumer-facing APIs.
 */
private class NavigationGraphRouter(
    private val graphs: List<NavigationGraph>
) {

    /**
     * Represents the result of resolving a [BackStackEntry].
     *
     * @param entry The original back stack entry being resolved.
     *
     * @param graph The [NavigationGraph] responsible for the entry.
     *
     * @param destination The resolved [Destination] instance.
     */
    data class Resolved(
        val entry: BackStackEntry,
        val graph: NavigationGraph,
        val destination: Destination
    )

    /**
     * Resolves a [BackStackEntry] to its owning graph and destination.
     *
     * @param entry The back stack entry to resolve.
     *
     * @return A [Resolved] instance containing the graph and destination.
     *
     * @throws IllegalStateException if no graph can resolve the destination ID.
     */
    fun resolve(entry: BackStackEntry): Resolved {
        val graph = graphs.firstOrNull { it.canResolveDestination(entry.destinationId) }
            ?: error("No graph can resolve ${entry.destinationId}")

        return Resolved(
            entry = entry,
            graph = graph,
            destination = graph.resolveDestination(entry.destinationId, entry.args)
        )
    }
}
