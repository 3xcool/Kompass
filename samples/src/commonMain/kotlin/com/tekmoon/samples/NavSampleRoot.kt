package com.tekmoon.samples

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.DeepLinkChannel
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf


private enum class KompassSampleDestinations : Destination {
    SampleList,
    Sample1ReturningResult,
    Sample2InnerGraphs,
    Sample3Scope,
    Sample4Transitions,
    Sample5Deeplink,
    Sample6Scene,
    Sample7AuthLogin,
    Sample8ExpenseTrackerAuthLogin;

    override val id: String
        get() = "kompass/$name"
}

private data class KompassNavSampleGraph(
    val backPressedChannel: BackPressedChannel?,
    val deepLinkUri: String? = null,
    val deepLinkChannel: DeepLinkChannel? = null
) : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean {
        return KompassSampleDestinations.entries.any { it.id == destinationId }
    }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination {
        return KompassSampleDestinations.entries
            .firstOrNull { it.id == destinationId }
            ?: error("Unknown destinationId: $destinationId")
    }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            KompassSampleDestinations.SampleList -> {
                KompassSampleList(navController)
            }
            KompassSampleDestinations.Sample1ReturningResult -> {
                Sample1_ResultNavigation(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample2InnerGraphs -> {
                Sample2_InnerGraphs_SameScopeId(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample3Scope -> {
                Sample3_WithScope(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    })
            }

            KompassSampleDestinations.Sample4Transitions -> {
                Sample4_PerGraphTransitions(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample5Deeplink -> {
                Sample5_DeepLink(
                    deepLinkUri = deepLinkUri,
                    deepLinkChannel = deepLinkChannel,
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample6Scene -> {
                Sample6_ListDetail(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample7AuthLogin -> {
                Sample7_AuthLogin(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }
            KompassSampleDestinations.Sample8ExpenseTrackerAuthLogin -> {
                Sample8_ExpenseTracker(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }
        }
    }

}

@Composable
fun KompassNavSample(
    backPressedChannel: BackPressedChannel?,
    deepLinkUri: String? = null,
    deepLinkChannel: DeepLinkChannel? = null
) {

    val navController = rememberNavController(KompassSampleDestinations.SampleList)

    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan()
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(
            KompassNavSampleGraph(
                backPressedChannel = backPressedChannel,
                deepLinkUri = deepLinkUri,
                deepLinkChannel = deepLinkChannel
            )
        )
    )
}

@Composable
private fun KompassSampleList(
    navController: NavController
) {
    val samples = KompassSampleDestinations.entries
        .filter { it != KompassSampleDestinations.SampleList }

    LazyColumn {
        items(
            items = samples,
            key = { it.id }
        ) { destination ->
            SampleRow(
                title = destination.name,
                onClick = {
                    navController.navigate(
                        entry = destination.toBackStackEntry()
                    )
                }
            )
        }
    }
}


@Composable
private fun SampleRow(
    title: String,
    onClick: () -> Unit
) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
