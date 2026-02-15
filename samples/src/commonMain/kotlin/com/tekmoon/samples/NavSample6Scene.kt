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
import com.tekmoon.kompass.newScope
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample6Dest : Destination {
    List,
    Profile;

    override val id: String get() = "kompass/sample6/$name"
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
        Sample6Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        Sample6Dest.entries.first { it.id == destinationId }

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
                        navController.navigate(
                            entry = Sample6Dest.Profile.toBackStackEntry(
                                args = Json.encodeToString(
                                    Sample6ProfileArgs(id)
                                ),
                                scopeId = newScope()
                            )
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
    val args =
        entry.args?.let {
            Json.decodeFromString<Sample6ProfileArgs>(it)
        }

    Column(Modifier.padding(16.dp)) {
        BasicText("Profile Detail")
        BasicText("ID = ${args?.id}")

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
