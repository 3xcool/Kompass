package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/** Wrap graphs, rather than changing SceneLayout: existing custom layouts get owners automatically. */
internal class OwnedNavigationGraph(
    private val graph: NavigationGraph,
    private val stateHolder: SaveableStateHolder,
) : NavigationGraph by graph {
    @Composable
    override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
        val owner = remember(navController, entry.id) { navController.entryOwners.owner(entry) }
        // Declared outside the saveable island: release follows its children's disposal.
        DisposableEffect(navController, entry.id, entry.scopeId) {
            navController.entryOwners.retain(entry)
            onDispose {
                navController.entryOwners.release(entry)
                if (navController.backStack.none { it.id == entry.id }) stateHolder.removeState(entry.id)
            }
        }
        stateHolder.SaveableStateProvider(entry.id) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalLifecycleOwner provides owner,
                LocalSavedStateRegistryOwner provides owner,
            ) {
                graph.Content(entry, destination, navController)
            }
        }
    }
}

@Composable
internal fun rememberOwnedGraphs(navController: NavController, graphs: List<NavigationGraph>): List<NavigationGraph> {
    navController.entryOwners.platformExtras = kompassPlatformCreationExtras()
    val holder = rememberSaveableStateHolder()
    val knownIds = remember(navController) { navController.backStack.map { it.id }.toMutableSet() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(navController, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            navController.entryOwners.updateHostLifecycle(lifecycle.currentState)
        }
        navController.entryOwners.updateHostLifecycle(lifecycle.currentState)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val liveIds = navController.backStack.map { it.id }.toSet()
    SideEffect {
        // removeState also suppresses saving if an outgoing provider is still active.
        (knownIds - liveIds).forEach(holder::removeState)
        knownIds.clear()
        knownIds.addAll(liveIds)
        navController.entryOwners.reconcile(navController.backStack)
    }
    return remember(graphs, holder) { graphs.map { OwnedNavigationGraph(it, holder) } }
}
