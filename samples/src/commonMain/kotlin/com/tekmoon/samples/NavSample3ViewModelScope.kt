package com.tekmoon.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationScopeId
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.defaultScope
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.rememberScoped
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample3Dest : Destination {
    First, Second;

    override val id: String get() = "kompass/sample3/$name"
}

/* -------------------------------------------
 * Navigation Graph
 * ------------------------------------------- */

private object Sample3Graph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample3Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        Sample3Dest.entries.first { it.id == destinationId }


    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        // Flow-scoped (shared across screens)
        val sharedState = rememberScoped(
            scopeId = NavigationScopeId("flow:sample"),
            onCleared = { state: SharedState ->
                state.onCleared()
            }
        ) {
            SharedState()
        }

        when (destination) {
            Sample3Dest.First ->
                FirstScreen(entry, navController, sharedState)

            Sample3Dest.Second ->
                SecondScreen(entry, navController, sharedState)
        }
    }
}

/* -------------------------------------------
 * States
 * ------------------------------------------- */

class ScreenState {

    var counter by mutableStateOf(0)

    init {
    }

    fun onCleared() {
    }
}

class SharedState {
    var counter by mutableStateOf(0)

    fun onCleared() {
    }
}

/* -------------------------------------------
 * Root
 * ------------------------------------------- */

@Composable
fun Sample3_WithScope(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {

    val navController = rememberNavController(Sample3Dest.First)

    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan{
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample3Graph)
    )
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun FirstScreen(
    entry: BackStackEntry,
    navController: NavController,
    sharedState: SharedState
) {
    // Screen-scoped (cleared on pop)
    val screenState = rememberScoped(
        scopeId = entry.scopeId,
        onCleared = { state: ScreenState ->
            state.onCleared()
        }
    ) {
        ScreenState()
    }

    Column {
        BasicText("First screen counter: ${screenState.counter}")
        BasicText("Shared counter: ${sharedState.counter}")

        Button(onClick = { screenState.counter++ }) {
            BasicText("Inc screen counter")
        }

        Button(onClick = { sharedState.counter++ }) {
            BasicText("Inc shared counter")
        }

        Button(onClick = {
            navController.navigate(
                entry = BackStackEntry(
                    destinationId = Sample3Dest.Second.id,
                    scopeId = Sample3Dest.Second.defaultScope()
                )
            )
        }) {
            BasicText("Go to Second")
        }
    }
}

@Composable
private fun SecondScreen(
    entry: BackStackEntry,
    navController: NavController,
    sharedState: SharedState
) {
    val screenState = rememberScoped(
        scopeId = entry.scopeId,
        onCleared = { it.onCleared() }
    ) {
        ScreenState()
    }

    Column {
        BasicText("Second screen counter: ${screenState.counter}")
        BasicText("Shared counter: ${sharedState.counter}")

        Button(onClick = { screenState.counter++ }) {
            BasicText("Inc screen counter")
        }

        Button(onClick = { sharedState.counter++ }) {
            BasicText("Inc shared counter")
        }

        Button(onClick = { navController.pop() }) {
            BasicText("Pop")
        }
    }
}
