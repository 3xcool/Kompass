package com.tekmoon.samples

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tekmoon.kompass.NavDirection
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationScopeId
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.SceneTransition
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.rememberScoped
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf


/* -------------------------------------------
 * Default transitions
 * ------------------------------------------- */

object SlideGraphTransition : SceneTransition {

    override fun transition(
        direction: NavDirection
    ): ContentTransform =
        when (direction) {
            NavDirection.Push ->
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it / 3 } + fadeOut()

            NavDirection.Pop ->
                slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
        }
}

object FadeGraphTransition : SceneTransition {

    override fun transition(
        direction: NavDirection
    ): ContentTransform =
        fadeIn() togetherWith fadeOut()
}

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample4Dest : Destination {
    Home,
    Details;

    override val id: String get() = "kompass/sample4/$name"
}

/* -------------------------------------------
 * Graph with per-graph transition
 * ------------------------------------------- */

private object Sample4Graph : NavigationGraph {

    override val sceneTransition: SceneTransition = SlideGraphTransition

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample4Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        Sample4Dest.entries.first { it.id == destinationId }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        val sharedState = rememberScoped(
            scopeId = NavigationScopeId("flow:sample4"),
            onCleared = { it.onCleared() }
        ) {
            Sample4SharedState()
        }

        when (destination) {
            Sample4Dest.Home ->
                HomeScreen(entry, navController, sharedState)

            Sample4Dest.Details ->
                DetailsScreen(entry, navController, sharedState)
        }
    }
}

/* -------------------------------------------
 * States
 * ------------------------------------------- */

class Screen4State {
    var counter by mutableStateOf(0)

    init {
    }

    fun onCleared() {
    }
}

private class Sample4SharedState {
    var counter by mutableStateOf(0)

    init {
    }

    fun onCleared() {
    }
}

/* -------------------------------------------
 * Root
 * ------------------------------------------- */

@Composable
fun Sample4_PerGraphTransitions(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {

    val navController = rememberNavController(Sample4Dest.Home)

    // Track previous state for direction
    val previousState =
        remember { mutableStateOf(navController.state) }

    LaunchedEffect(navController.state) {
        previousState.value = navController.state
    }

    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan{
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample4Graph)
    )
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun HomeScreen(
    entry: BackStackEntry,
    navController: NavController,
    sharedState: Sample4SharedState
) {
    val screenState = rememberScoped(
        scopeId = entry.scopeId,
        onCleared = { it.onCleared() }
    ) {
        Screen4State()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText("🏠 Home")
        BasicText("Screen counter: ${screenState.counter}")
        BasicText("Shared counter: ${sharedState.counter}")

        Button(onClick = { screenState.counter++ }) {
            BasicText("Inc screen")
        }

        Button(onClick = { sharedState.counter++ }) {
            BasicText("Inc shared")
        }

        Button(onClick = {
            navController.navigate(
                entry = Sample4Dest.Details.toBackStackEntry()
            )
        }) {
            BasicText("Go to Details →")
        }
    }
}

@Composable
private fun DetailsScreen(
    entry: BackStackEntry,
    navController: NavController,
    sharedState: Sample4SharedState
) {
    val screenState = rememberScoped(
        scopeId = entry.scopeId,
        onCleared = { it.onCleared() }
    ) {
        Screen4State()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BasicText("📄 Details")
        BasicText("Screen counter: ${screenState.counter}")
        BasicText("Shared counter: ${sharedState.counter}")

        Button(onClick = { screenState.counter++ }) {
            BasicText("Inc screen")
        }

        Button(onClick = { sharedState.counter++ }) {
            BasicText("Inc shared")
        }

        Button(onClick = {
            navController.pop()
        }) {
            BasicText("← Back")
        }
    }
}