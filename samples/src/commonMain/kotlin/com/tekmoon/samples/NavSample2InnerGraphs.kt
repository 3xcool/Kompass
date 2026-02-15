package com.tekmoon.samples

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationScopeId
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

private sealed interface MainDestination : Destination {
    data object Home : MainDestination { override val id = "kompass/sample2/main/home" }
    data object Feature : MainDestination { override val id = "kompass/sample2/main/feature" }
}

private object MainGraph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("kompass/sample2/main/")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            MainDestination.Home.id -> MainDestination.Home
            MainDestination.Feature.id -> MainDestination.Feature
            else -> error("Unknown main destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            is MainDestination.Home -> MainHome(navController)
            is MainDestination.Feature -> MainFeature(navController)
        }
    }
}


private sealed interface FeatureDestination : Destination {
    data object StepOne : FeatureDestination { override val id = "feature/one" }
    data object StepTwo : FeatureDestination { override val id = "feature/two" }
}

private object FeatureGraph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("feature/")

//    override val sceneStrategy = ListDetailSceneStrategy
//
//    @Composable
//    override fun SceneLayout(
//        destinations: List<DsDestination>,
//        content: @Composable (DsDestination) -> Unit
//    ) {
//        ListDetailLayout(destinations, content)
//    }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            FeatureDestination.StepOne.id -> FeatureDestination.StepOne
            FeatureDestination.StepTwo.id -> FeatureDestination.StepTwo
            else -> error("Unknown feature destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            is FeatureDestination.StepOne -> StepOne(navController)
            is FeatureDestination.StepTwo -> StepTwo(navController)
        }
    }
}


@Composable
fun Sample2_InnerGraphs_SameScopeId(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navController = rememberNavController(startDestination = MainDestination.Home)

    // Always enabled to avoid closing the app
    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan {
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(
            MainGraph,
            FeatureGraph,
        )
    )
}

// ---------- Screens ----------

@Composable
private fun MainHome(
    navController: NavController
) {
    Button(onClick = {
        navController.navigate(
            entry = BackStackEntry(
                destinationId = MainDestination.Feature.id,
                scopeId = NavigationScopeId("flow:feature")
            )
        )
    }) {
        BasicText("Open Feature")
    }
}

@Composable
private fun MainFeature(
    navController: NavController
) {
    Button(onClick = {
        navController.navigate(
            entry = BackStackEntry(
                destinationId = FeatureDestination.StepOne.id,
                scopeId = NavigationScopeId("flow:feature")
            )
        )
    }) {
        BasicText("Start Feature Flow")
    }
}

@Composable
private fun StepOne(
    navController: NavController
) {
    Button(onClick = {
        navController.navigate(
            entry = BackStackEntry(
                destinationId = FeatureDestination.StepTwo.id,
                scopeId = NavigationScopeId("flow:feature")
            )
        )
    }) {
        BasicText("Next")
    }
}

@Composable
private fun StepTwo(
    navController: NavController
) {
    Button(onClick = {
        navController.pop()
    }) {
        BasicText("Finish")
    }
}