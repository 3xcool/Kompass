package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList


/**
 *
 * Root composable responsible for orchestrating navigation rendering.
 *
 * [KompassNavigationHost] does not implement navigation rules or UI itself.
 * Its responsibility is limited to:
 * - Observing the current [NavigationState]
 * - Resolving destinations through [KompassNavigationGraph]s
 * - Delegating rendering to the active [SceneLayout]
 *
 * This separation ensures that:
 * - Navigation logic remains testable and platform-agnostic
 * - Layout and animation strategies are pluggable
 * - Screen content remains unaware of navigation orchestration
 *
 * @param navController The [KompassNavController] driving navigation state and commands.
 *
 * @param graphs List of [KompassNavigationGraph]s responsible for resolving and rendering
 * destinations present in the back stack.
 */
@Composable
fun KompassNavigationHost(
    navController: KompassNavController,
    graphs: ImmutableList<KompassNavigationGraph>
) {
    val ownedGraphs = rememberOwnedGraphs(navController, graphs)
    val router = remember(ownedGraphs) {
        NavigationGraphRouter(ownedGraphs)
    }

    val direction = navController.direction

    val activeEntry = navController.state.backStack.last()
    val activeGraph = router.resolve(activeEntry).graph

    val layout = activeGraph.sceneLayout ?: SceneLayoutDefaultAnimatedSinglePane

    layout.Render(
        backStack = navController.backStack,
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
 * Internal router responsible for mapping [KompassEntry] instances
 * to their owning [KompassNavigationGraph] and resolved [Destination].
 *
 * This class encapsulates graph lookup logic and ensures that:
 * - Each back stack entry is resolved by exactly one graph
 * - Destination resolution is centralized and consistent
 *
 * It is intentionally kept private to prevent leaking graph
 * resolution logic into consumer-facing APIs.
 */
private class NavigationGraphRouter(
    private val graphs: List<KompassNavigationGraph>
) {

    /**
     * Represents the result of resolving a [KompassEntry].
     *
     * @param entry The original back stack entry being resolved.
     *
     * @param graph The [KompassNavigationGraph] responsible for the entry.
     *
     * @param destination The resolved [Destination] instance.
     */
    data class Resolved(
        val entry: KompassEntry,
        val graph: KompassNavigationGraph,
        val destination: Destination
    )

    /**
     * Resolves a [KompassEntry] to its owning graph and destination.
     *
     * @param entry The back stack entry to resolve.
     *
     * @return A [Resolved] instance containing the graph and destination.
     *
     * @throws IllegalStateException if no graph can resolve the destination ID.
     */
    fun resolve(entry: KompassEntry): Resolved {
        val graph = graphs.firstOrNull { it.canResolveDestination(entry.destinationId) }
            ?: error("No graph can resolve ${entry.destinationId}")

        return Resolved(
            entry = entry,
            graph = graph,
            destination = graph.resolveDestination(entry.destinationId, entry.args)
        )
    }
}
