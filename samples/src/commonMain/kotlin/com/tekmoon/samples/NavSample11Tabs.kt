package com.tekmoon.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tekmoon.kompass.KompassEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassNavController
import com.tekmoon.kompass.KompassNavigationGraph
import com.tekmoon.kompass.createKompassNavController
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kompasskmp.samples.generated.resources.Res
import kompasskmp.samples.generated.resources.sample11_back
import kompasskmp.samples.generated.resources.sample11_detail
import kompasskmp.samples.generated.resources.sample11_model_per_tab
import kompasskmp.samples.generated.resources.sample11_model_reorder
import kompasskmp.samples.generated.resources.sample11_open_detail
import kompasskmp.samples.generated.resources.sample11_stack
import kompasskmp.samples.generated.resources.sample11_tab_home
import kompasskmp.samples.generated.resources.sample11_tab_profile
import kompasskmp.samples.generated.resources.sample11_tab_search
import kompasskmp.samples.generated.resources.sample11_visits
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/*
 * Sample 11 — the two tab models.
 *
 * Reorder model: one controller, and a tab tap is navigate(reuseIfExists = true). The entry moves to
 * the top, keeps its occurrence ID, its ViewModel and its UI state. Back walks the visit history
 * across tabs, so it never lies about where the user came from. This is what YouTube and Instagram
 * do. Its limit: it moves one entry, not a segment, so a detail opened inside a tab does not follow
 * the tab.
 *
 * Per-tab model: one controller for each tab, created outside composition, each with its own host.
 * Every tab keeps its own depth.
 *
 * The trap, proved by TabNavigationTest: one host whose navController parameter changes is NOT the
 * same as unmounting a host. Swapping the controller clears the outgoing tab's ViewModels. Give each
 * tab its own host and compose only the active one, as below.
 */

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample11Dest : Destination {
    Home,
    Search,
    Profile,
    Detail;

    override val id: String get() = "kompass/sample11/$name"
}

private val Sample11Tabs = listOf(Sample11Dest.Home, Sample11Dest.Search, Sample11Dest.Profile)

/* -------------------------------------------
 * A counter, to prove state survives a tab change
 * ------------------------------------------- */

private class Sample11Visits : ViewModel() {
    var value: Int = 0
}

/* -------------------------------------------
 * Graph
 * ------------------------------------------- */

private object Sample11Graph : KompassNavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample11Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(destinationId: String, args: String?): Destination =
        Sample11Dest.entries.first { it.id == destinationId }

    @Composable
    override fun Content(entry: KompassEntry, destination: Destination, navController: KompassNavController) {
        when (destination) {
            Sample11Dest.Detail -> Sample11DetailScreen(navController)
            else -> Sample11TabScreen(entry, destination, navController)
        }
    }
}

/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun Sample11TabScreen(entry: KompassEntry, destination: Destination, navController: KompassNavController) {
    // Keyed by the occurrence, so each visit of each tab owns its counter.
    val visits = viewModel(key = entry.id) { Sample11Visits() }
    DisposableEffect(entry.id) {
        visits.value += 1
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = tabLabel(destination), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(Res.string.sample11_visits, visits.value),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = { navController.navigate(Sample11Dest.Detail.toKompassEntry()) }) {
            Text(stringResource(Res.string.sample11_open_detail))
        }
    }
}

@Composable
private fun Sample11DetailScreen(navController: KompassNavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(Res.string.sample11_detail), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { navController.popIfCan() }) {
            Text(stringResource(Res.string.sample11_back))
        }
    }
}

@Composable
private fun tabLabel(destination: Destination): String = when (destination) {
    Sample11Dest.Search -> stringResource(Res.string.sample11_tab_search)
    Sample11Dest.Profile -> stringResource(Res.string.sample11_tab_profile)
    else -> stringResource(Res.string.sample11_tab_home)
}

/* -------------------------------------------
 * Host
 * ------------------------------------------- */

@Composable
fun Sample11_Tabs(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {},
) {
    var reorder by remember { mutableStateOf(true) }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = reorder,
                        onClick = { reorder = true },
                        label = { Text(stringResource(Res.string.sample11_model_reorder)) },
                    )
                    FilterChip(
                        selected = !reorder,
                        onClick = { reorder = false },
                        label = { Text(stringResource(Res.string.sample11_model_per_tab)) },
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (reorder) {
                Sample11ReorderModel(backPressedChannel, onDismiss)
            } else {
                Sample11PerTabModel(backPressedChannel, onDismiss)
            }
        }
    }
}

/** One controller. A tab tap moves the entry to the top and keeps everything it owns. */
@Composable
private fun Sample11ReorderModel(backPressedChannel: BackPressedChannel?, onDismiss: () -> Unit) {
    val navController = rememberKompassNavController(Sample11Dest.Home)

    KompassBackHandler(backPressedChannel = backPressedChannel) {
        navController.popIfCan(onFailure = onDismiss)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            KompassNavigationHost(navController, persistentListOf(Sample11Graph))
        }
        Sample11StackReadout(navController)
        NavigationBar {
            Sample11Tabs.forEach { tab ->
                NavigationBarItem(
                    selected = navController.currentEntry.destinationId == tab.id,
                    // The whole bottom bar is this one line.
                    onClick = { navController.navigate(tab.toKompassEntry(), reuseIfExists = true) },
                    icon = { Text(tabLabel(tab).take(1)) },
                    label = { Text(tabLabel(tab)) },
                )
            }
        }
    }
}

/**
 * One controller for each tab, created outside composition, and one host for each.
 *
 * Only the active host is composed. The inactive tabs keep their stack, their ViewModels and their
 * UI state, because leaving composition does not release an externally owned controller.
 */
@Composable
private fun Sample11PerTabModel(backPressedChannel: BackPressedChannel?, onDismiss: () -> Unit) {
    val controllers = remember {
        Sample11Tabs.associateWith { createKompassNavController(it) }
    }
    DisposableEffect(controllers) {
        // An externally owned controller is released by close(), never by leaving composition.
        onDispose { controllers.values.forEach { it.close() } }
    }
    var active by remember { mutableStateOf(Sample11Tabs.first()) }
    val navController = controllers.getValue(active)

    KompassBackHandler(backPressedChannel = backPressedChannel) {
        navController.popIfCan(onFailure = onDismiss)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            // One host for each tab, and only the active one is composed. Do NOT write a single
            // host and swap its navController: that clears the outgoing tab.
            controllers.forEach { (tab, controller) ->
                if (tab == active) {
                    KompassNavigationHost(controller, persistentListOf(Sample11Graph))
                }
            }
        }
        Sample11StackReadout(navController)
        NavigationBar {
            Sample11Tabs.forEach { tab ->
                NavigationBarItem(
                    selected = tab == active,
                    onClick = { active = tab },
                    icon = { Text(tabLabel(tab).take(1)) },
                    label = { Text(tabLabel(tab)) },
                )
            }
        }
    }
}

/** Shows the stack, so the cross-tab Back history is obvious instead of being described. */
@Composable
private fun Sample11StackReadout(navController: KompassNavController) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(
                Res.string.sample11_stack,
                navController.backStack.joinToString(" > ") { it.destinationId.substringAfterLast('/') },
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
