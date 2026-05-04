package com.tekmoon.samples

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.SceneLayoutListDetail
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.TypedDestination
import com.tekmoon.kompass.navigateTo
import com.tekmoon.kompass.newScope
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.requireArgs
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

/* -------------------------------------------
 * Destinations
 *
 * List is parameterless (plain Destination); Profile carries typed args
 * (TypedDestination<Sample6ProfileArgs>) so navigation and arg-reading both
 * use the typed API — no manual Json.encodeToString at call sites.
 * ------------------------------------------- */

private sealed interface Sample6Dest : Destination {

    data object List : Sample6Dest {
        override val id: String = "kompass/sample6/List"
    }

    data object Profile : Sample6Dest, TypedDestination<Sample6ProfileArgs> {
        override val id: String = "kompass/sample6/Profile"
        override val argsSerializer = Sample6ProfileArgs.serializer()
    }
}

/* -------------------------------------------
 * Args
 * ------------------------------------------- */

@Serializable
private data class Sample6ProfileArgs(
    val id: String
)


/* -------------------------------------------
 * Graph
 * ------------------------------------------- */

object Sample6Graph : NavigationGraph {

    override val sceneLayout = SceneLayoutListDetail() // magic is here

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId == Sample6Dest.List.id ||
                destinationId == Sample6Dest.Profile.id

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            Sample6Dest.List.id -> Sample6Dest.List
            Sample6Dest.Profile.id -> Sample6Dest.Profile
            else -> error("Unknown Sample6 destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            Sample6Dest.List ->
                ProfileListScreen(navController)

            Sample6Dest.Profile ->
                ProfileDetailScreen(entry, navController)
        }
    }
}

/* -------------------------------------------
 * Root
 * ------------------------------------------- */

@Composable
fun Sample6_ListDetail(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {

    val navController = rememberNavController(Sample6Dest.List)

    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan {
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample6Graph)
    )
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun ProfileListScreen(
    navController: NavController
) {
    val profiles = remember {
        listOf("A", "B", "C", "D")
    }

    Column(Modifier.padding(16.dp)) {
        BasicText("Profiles")

        Spacer(Modifier.height(8.dp))

        profiles.forEach { id ->
            BasicText(
                text = "Profile $id",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigateTo(
                            destination = Sample6Dest.Profile,
                            args = Sample6ProfileArgs(id),
                            scopeId = newScope()
                        )
                    }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun ProfileDetailScreen(
    entry: BackStackEntry,
    navController: NavController
) {
    val args = navController.requireArgs(Sample6Dest.Profile, entry)

    Column(Modifier.padding(16.dp)) {
        BasicText("Profile Detail")
        BasicText("ID = ${args.id}")

        Spacer(Modifier.height(16.dp))

        BasicText(
            "Back",
            modifier = Modifier
                .clickable {
                    navController.pop()
                }
                .padding(8.dp)
        )
    }
}
