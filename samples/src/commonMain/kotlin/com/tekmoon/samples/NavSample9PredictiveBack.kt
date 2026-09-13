package com.tekmoon.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassPredictiveBackHandler
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.SceneLayout
import com.tekmoon.kompass.SceneLayoutPredictive
import com.tekmoon.kompass.SceneTransition
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

/*
 * Sample 9 — predictive Back.
 *
 * Drag from the edge of the screen and hold. The screen below moves in while you drag, and the back
 * stack does not change. Let go to finish the pop. Drag back to the edge to cancel it.
 *
 * Android API 34+ and iOS stream native edge gestures. Desktop and web support a mouse or touch drag
 * from the left edge of the layout and ESC for an ordinary back. Below Android API 34 a back press
 * starts and finishes the gesture at once.
 */

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample9Dest : Destination {
    Level1,
    Level2,
    Level3;

    override val id: String get() = "kompass/sample9/$name"
}

/* -------------------------------------------
 * Graph
 * ------------------------------------------- */

private object Sample9Graph : NavigationGraph {

    // The layout reads the gesture from the controller, so the graph stays an object with no state.
    // A slide transition makes the drag easy to see. Without a seekable layout the gesture still
    // pops, but it does not follow the finger.
    override val sceneLayout: SceneLayout = SceneLayoutPredictive(transition = SlideGraphTransition)

    override val sceneTransition: SceneTransition = SlideGraphTransition

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample9Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        Sample9Dest.entries.first { it.id == destinationId }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            Sample9Dest.Level1 -> Sample9Screen(
                title = "Level 1",
                body = "This is the root of the sample. Press back here to leave the sample.",
                navController = navController
            )

            Sample9Dest.Level2 -> Sample9Screen(
                title = "Level 2",
                body = "Drag from the edge and hold. Level 1 moves in behind this screen.",
                navController = navController
            )

            Sample9Dest.Level3 -> Sample9Screen(
                title = "Level 3",
                body = "Drag back to the edge and let go. The gesture cancels and nothing pops.",
                navController = navController
            )
        }
    }
}

/* -------------------------------------------
 * Root
 * ------------------------------------------- */

@Composable
fun Sample9_PredictiveBack(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navController = rememberNavController(Sample9Dest.Level1)

    // This handler replaces KompassBackHandler for the pop. Do not install both for the same
    // controller, because enabled handlers compete for the same event.
    KompassPredictiveBackHandler(navController)

    // The gesture handler turns itself off at the root of this stack. Take over there, so back
    // leaves the sample instead of closing the app.
    KompassBackHandler(
        enabled = !navController.canGoBack(),
        backPressedChannel = backPressedChannel,
    ) {
        onDismiss()
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample9Graph)
    )
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun Sample9Screen(
    title: String,
    body: String,
    navController: NavController
) {
    val depth = navController.backStack.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(text = "Back stack depth: $depth")

        GestureReadout(navController)

        Button(
            enabled = depth < 3,
            onClick = {
                val next = Sample9Dest.entries[depth]
                navController.navigate(entry = next.toBackStackEntry())
            }
        ) {
            Text("Push next level")
        }

        OutlinedButton(
            enabled = navController.canGoBack(),
            onClick = { navController.popIfCan() }
        ) {
            Text("Pop from a button")
        }
    }
}

/**
 * Live readout of the gesture.
 *
 * [com.tekmoon.kompass.NavController.predictiveBack] is plain Compose state, so this recomposes on
 * every drag step. Read it to drive your own motion beside the scene, such as a dim layer or a
 * shrinking card.
 */
@Composable
private fun GestureReadout(navController: NavController) {
    val gesture = navController.predictiveBack
    val progress = gesture.progress

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (gesture.isActive) "Gesture: active" else "Gesture: idle",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Progress: " + (progress?.let { formatProgress(it) } ?: "—"),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Target entry: " + (gesture.targetEntryId ?: "—"),
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Formats the fraction with two decimals, without a platform number formatter. */
private fun formatProgress(value: Float): String {
    val hundredths = (value * 100f).toInt().coerceIn(0, 100)
    val whole = hundredths / 100
    val rest = hundredths % 100
    return "$whole." + rest.toString().padStart(2, '0')
}
