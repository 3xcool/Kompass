package com.tekmoon.samples.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavDirection
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationState
import com.tekmoon.kompass.SceneLayout
import com.tekmoon.kompass.defaultScope
import com.tekmoon.kompass.rememberNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview


//import org.jetbrains.compose.ui.tooling.preview.Preview

/* -------------------- Fake Destinations -------------------- */

@Serializable
private data object PreviewHome : Destination {
    override val id: String = "preview/home"
}

@Serializable
private data object PreviewDetails : Destination {
    override val id: String = "preview/details"
}

/* -------------------- Preview States -------------------- */

object DsNavigationPreviewStates {

    fun listOnly(): NavigationState =
        NavigationState(
            backStack = persistentListOf(
                BackStackEntry(
                    destinationId = PreviewHome.id,
                    scopeId = PreviewHome.defaultScope()
                )
            )
        )

    fun listDetail(): NavigationState =
        NavigationState(
            backStack = persistentListOf(
                BackStackEntry(
                    destinationId = PreviewHome.id,
                    scopeId = PreviewHome.defaultScope()
                ),
                BackStackEntry(
                    destinationId = PreviewDetails.id,
                    scopeId = PreviewDetails.defaultScope()
                ),
                BackStackEntry(
                    destinationId = PreviewDetails.id,
                    scopeId = PreviewDetails.defaultScope()
                )
            )
        )
}


/* -------------------- SCENE LAYOUT -------------------- */

private object PreviewMultiPaneSceneLayout : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    ) {
        BoxWithConstraints {
            val isCompact = maxWidth < 600.dp

            if (isCompact) {
                // Preview on phone -> only focused entry
                val entry = backStack.last()
                val (graph, destination) = remember(entry) { resolve(entry) }

                graph.Content(
                    entry = entry,
                    destination = destination,
                    navController = navController
                )
            } else {
                // Preview on wide -> SHOW ENTIRE STACK
                Row(Modifier.fillMaxSize()) {
                    backStack.forEach { entry ->
                        val (graph, destination) =
                            remember(entry) { resolve(entry) }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            graph.Content(
                                entry = entry,
                                destination = destination,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}


private object PreviewNavigationGraph : NavigationGraph {

    override val sceneLayout: SceneLayout = PreviewMultiPaneSceneLayout

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("preview/")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            PreviewHome.id -> PreviewHome
            PreviewDetails.id -> PreviewDetails
            else -> error("Unknown preview destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController,
    ) {
        when (destination) {
            PreviewHome -> TodoListScreen()
            PreviewDetails -> TodoDetailScreen(destination.id)
        }
    }
}

@Preview(
    name = "📱 Phone – List Only",
    widthDp = 360,
    heightDp = 640
)
@Preview(
    name = "💻 Tablet – List Only",
    widthDp = 1000,
    heightDp = 600
)
@Composable
fun NavigationPreview_ListOnly() {
//    val state = DsNavigationPreviewStates.listOnly()
//    val navController = remember(state) {
//        NavController(state, {})
//    }
    val navController = rememberNavController(DsNavigationPreviewStates.listOnly())
    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(PreviewNavigationGraph),
    )
}


@Preview(
    name = "📱 Phone – Navigation Stack",
    widthDp = 360,
    heightDp = 640
)
@Preview(
    name = "💻 Tablet – Navigation Stack",
    widthDp = 1000,
    heightDp = 600
)
@Composable
fun NavigationPreview_MultiPane() {
//    val state = DsNavigationPreviewStates.listDetail()
//    val navController = remember(state) {
//        NavController(state, {})
//    }
    val navController = rememberNavController(DsNavigationPreviewStates.listDetail())
    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(PreviewNavigationGraph)
    )
}


/* -------------------- PREVIEW SCREENS -------------------- */
@Composable
private fun TodoListScreen(){
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E7D32)) // list pane
            .padding(16.dp)
    ) {
        BasicText(
            text = "Tablet pane: Todo List",
            modifier = Modifier.background(Color.White)
        )
    }
}

@Composable
private fun TodoDetailScreen(id: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1565C0)) // list pane
            .padding(16.dp)
    ) {
        BasicText(
            text = "Tablet pane: Todo Detail List $id",
            modifier = Modifier.background(Color.White)
        )
    }
}
