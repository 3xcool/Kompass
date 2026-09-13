package com.tekmoon.samples

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf
import kompasskmp.samples.generated.resources.Res
import kompasskmp.samples.generated.resources.sample_list_auth_login
import kompasskmp.samples.generated.resources.sample_list_deeplink
import kompasskmp.samples.generated.resources.sample_list_expense_tracker_auth_login
import kompasskmp.samples.generated.resources.sample_list_inner_graphs
import kompasskmp.samples.generated.resources.sample_list_predictive_back
import kompasskmp.samples.generated.resources.sample_list_returning_result
import kompasskmp.samples.generated.resources.sample_list_scene
import kompasskmp.samples.generated.resources.sample_list_scope
import kompasskmp.samples.generated.resources.sample_list_shared_element_transition
import kompasskmp.samples.generated.resources.sample_list_tabs
import kompasskmp.samples.generated.resources.sample_list_title
import kompasskmp.samples.generated.resources.sample_list_transitions
import org.jetbrains.compose.resources.stringResource


private enum class KompassSampleDestinations : Destination {
    SampleList,
    Sample1ReturningResult,
    Sample2InnerGraphs,
    Sample3Scope,
    Sample4Transitions,
    Sample5Deeplink,
    Sample6Scene,
    Sample7AuthLogin,
    Sample8ExpenseTrackerAuthLogin,
    Sample9PredictiveBack,
    Sample10SharedElementTransition,
    Sample11Tabs;

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

            KompassSampleDestinations.Sample9PredictiveBack -> {
                Sample9_PredictiveBack(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample10SharedElementTransition -> {
                Sample10_SharedElementTransition(
                    backPressedChannel = backPressedChannel,
                    onDismiss = {
                        navController.popIfCan()
                    }
                )
            }

            KompassSampleDestinations.Sample11Tabs -> {
                Sample11_Tabs(
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

    KompassBackHandler(
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
        item {
            Text(
                text = stringResource(Res.string.sample_list_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
        items(
            items = samples,
            key = { it.id }
        ) { destination ->
            SampleRow(
                title = destination.label(),
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
private fun KompassSampleDestinations.label(): String = when (this) {
    KompassSampleDestinations.SampleList -> error("The sample list is not a selectable sample")
    KompassSampleDestinations.Sample1ReturningResult -> stringResource(Res.string.sample_list_returning_result)
    KompassSampleDestinations.Sample2InnerGraphs -> stringResource(Res.string.sample_list_inner_graphs)
    KompassSampleDestinations.Sample3Scope -> stringResource(Res.string.sample_list_scope)
    KompassSampleDestinations.Sample4Transitions -> stringResource(Res.string.sample_list_transitions)
    KompassSampleDestinations.Sample5Deeplink -> stringResource(Res.string.sample_list_deeplink)
    KompassSampleDestinations.Sample6Scene -> stringResource(Res.string.sample_list_scene)
    KompassSampleDestinations.Sample7AuthLogin -> stringResource(Res.string.sample_list_auth_login)
    KompassSampleDestinations.Sample8ExpenseTrackerAuthLogin ->
        stringResource(Res.string.sample_list_expense_tracker_auth_login)
    KompassSampleDestinations.Sample9PredictiveBack -> stringResource(Res.string.sample_list_predictive_back)
    KompassSampleDestinations.Sample10SharedElementTransition ->
        stringResource(Res.string.sample_list_shared_element_transition)
    KompassSampleDestinations.Sample11Tabs -> stringResource(Res.string.sample_list_tabs)
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
