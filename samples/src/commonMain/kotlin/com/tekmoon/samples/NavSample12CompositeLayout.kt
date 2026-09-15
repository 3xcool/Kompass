@file:OptIn(com.tekmoon.kompass.layout.ExperimentalKompassCompositeLayoutApi::class)

package com.tekmoon.samples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.NavigationState
import com.tekmoon.kompass.layout.CompositeLayoutLabels
import com.tekmoon.kompass.layout.CompositeLayoutState
import com.tekmoon.kompass.layout.SceneLayoutComposite
import com.tekmoon.kompass.layout.rememberCompositeLayoutState
import com.tekmoon.kompass.toKompassBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kompasskmp.samples.generated.resources.Res
import kompasskmp.samples.generated.resources.sample12_add_pane
import kompasskmp.samples.generated.resources.sample12_back_hint
import kompasskmp.samples.generated.resources.sample12_drag_pane
import kompasskmp.samples.generated.resources.sample12_enter_edit_mode
import kompasskmp.samples.generated.resources.sample12_enter_edit_mode_action
import kompasskmp.samples.generated.resources.sample12_exit_edit_mode
import kompasskmp.samples.generated.resources.sample12_exit_edit_mode_action
import kompasskmp.samples.generated.resources.sample12_navigation_level
import kompasskmp.samples.generated.resources.sample12_overview_body
import kompasskmp.samples.generated.resources.sample12_overview_title
import kompasskmp.samples.generated.resources.sample12_list_item
import kompasskmp.samples.generated.resources.sample12_level_body
import kompasskmp.samples.generated.resources.sample12_level_title
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/*
 * Sample 12 — optional composite layout.
 *
 * The controller owns the navigation order. CompositeLayoutState owns only the visual pane tree,
 * so resizing and docking are deliberately not navigation commands.
 */

private sealed class Sample12Dest : Destination {
    object Overview : Sample12Dest() {
        override val id: String = "kompass/sample12/Overview"
    }

    data class Level(val number: Int) : Sample12Dest() {
        init {
            require(number > 0) { "Sample level must be positive" }
        }

        override val id: String = "kompass/sample12/Level$number"
    }

    companion object {
        fun fromId(id: String): Sample12Dest? = when {
            id == Overview.id -> Overview
            id.startsWith("kompass/sample12/Level") -> id
                .removePrefix("kompass/sample12/Level")
                .toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(::Level)
            else -> null
        }
    }
}

private class Sample12Graph(
    layoutState: CompositeLayoutState,
    labels: CompositeLayoutLabels,
) : NavigationGraph {

    override val sceneLayout: SceneLayoutComposite = SceneLayoutComposite(
        state = layoutState,
        paneTitleContent = { entry ->
            Sample12Dest.fromId(entry.destinationId)?.title() ?: entry.destinationId
        },
        labels = labels,
    )

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample12Dest.fromId(destinationId) != null

    override fun resolveDestination(destinationId: String, args: String?): Destination =
        Sample12Dest.fromId(destinationId)
            ?: error("Unknown Sample 12 destination: $destinationId")

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController,
    ) {
        val stackPosition = navController.backStack.indexOf(entry) + 1
        val sampleDestination = destination as? Sample12Dest
            ?: error("Unexpected Sample 12 destination: ${destination.id}")
        val backTargetEntry = navController.backStack.lastOrNull()
        val backTargetDestination = backTargetEntry
            ?.let { Sample12Dest.fromId(it.destinationId) }
            ?: sampleDestination
        Sample12Pane(
            entryId = entry.id,
            destination = sampleDestination,
            stackPosition = stackPosition,
            stackSize = navController.backStack.size,
            backTargetPosition = navController.backStack.size,
            backTargetDestination = backTargetDestination,
            onAddPane = {
                val nextLevel = navController.backStack
                    .mapNotNull { Sample12Dest.fromId(it.destinationId) as? Sample12Dest.Level }
                    .maxOfOrNull { it.number }
                    ?.plus(1)
                    ?: 1
                navController.navigate(Sample12Dest.Level(nextLevel).toKompassBackStackEntry())
            },
        )
    }
}

@Composable
fun Sample12_CompositeLayout(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {},
) {
    val layoutState = rememberCompositeLayoutState()
    val labels = CompositeLayoutLabels(
        dragPane = { title -> stringResource(Res.string.sample12_drag_pane, title) },
        enterEditMode = { stringResource(Res.string.sample12_enter_edit_mode) },
        enterEditModeText = { stringResource(Res.string.sample12_enter_edit_mode_action) },
        exitEditMode = { stringResource(Res.string.sample12_exit_edit_mode) },
        exitEditModeText = { stringResource(Res.string.sample12_exit_edit_mode_action) },
    )
    val graph = remember(layoutState) { Sample12Graph(layoutState, labels) }
    val initialState = remember {
        NavigationState(
            backStack = persistentListOf(
                Sample12Dest.Overview.toKompassBackStackEntry(),
                Sample12Dest.Level(1).toKompassBackStackEntry(),
                Sample12Dest.Level(2).toKompassBackStackEntry(),
            ),
        )
    }
    val navController = com.tekmoon.kompass.rememberKompassNavController(initialState)

    KompassBackHandler(backPressedChannel = backPressedChannel) {
        if (layoutState.isEditMode) {
            layoutState.exitEditMode()
        } else {
            navController.popIfCan(onFailure = onDismiss)
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(graph),
    )
}

@Composable
private fun Sample12Pane(
    entryId: String,
    destination: Sample12Dest,
    stackPosition: Int,
    stackSize: Int,
    backTargetPosition: Int,
    backTargetDestination: Sample12Dest,
    onAddPane: () -> Unit,
) {
    val title = destination.title()
    val body = destination.body()
    val backTargetTitle = backTargetDestination.title()
    val listItems = List(100) { index ->
        stringResource(Res.string.sample12_list_item, title, index + 1)
    }
    val listState = rememberSaveable(entryId, saver = LazyListState.Saver) {
        LazyListState()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.sample12_navigation_level, stackPosition, stackSize))
                Text(stringResource(Res.string.sample12_back_hint, backTargetPosition, backTargetTitle))
                Button(onClick = onAddPane) {
                    Text(stringResource(Res.string.sample12_add_pane, title))
                }
            }
        }
        items(
            items = listItems,
            key = { it },
        ) { item ->
            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = item,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Sample12Dest.title(): String = when (this) {
    Sample12Dest.Overview -> stringResource(Res.string.sample12_overview_title)
    is Sample12Dest.Level -> stringResource(Res.string.sample12_level_title, number)
}

@Composable
private fun Sample12Dest.body(): String = when (this) {
    Sample12Dest.Overview -> stringResource(Res.string.sample12_overview_body)
    is Sample12Dest.Level -> stringResource(Res.string.sample12_level_body, number)
}
