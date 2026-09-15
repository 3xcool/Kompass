@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, ExperimentalKompassCompositeLayoutApi::class)

package com.tekmoon.kompass.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.SceneLayout
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassBackStackEntry
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour of the optional composite layout inside a real composition.
 *
 * The pure arrangement rules live in `CompositeLayoutTest`. This class covers what only a
 * composition can show: the rendered panes, the resize handle, the drag and dock gestures, the
 * floating preview, and the arrangement that survives a state restore.
 */
class SceneLayoutCompositeTest {

    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private class Graph(
        override val sceneLayout: SceneLayout,
        val content: @Composable (BackStackEntry) -> Unit,
    ) : NavigationGraph {
        override fun canResolveDestination(destinationId: String) = true
        override fun resolveDestination(destinationId: String, args: String?) =
            if (destinationId == "a") A else B

        @Composable
        override fun Content(
            entry: BackStackEntry,
            destination: Destination,
            navController: NavController,
        ) = content(entry)
    }

    @Test
    fun every_visible_entry_gets_its_own_pane() = runComposeUiTest {
        val state = CompositeLayoutState()
        val nav = twoPaneHost(state)

        onNodeWithText("content:a:0").assertExists()
        onNodeWithText("content:b:0").assertExists()
        runOnIdle { assertEquals(nav.backStack.map { it.id }, state.layout.paneIds()) }
    }

    @Test
    fun popping_an_entry_removes_only_its_pane() = runComposeUiTest {
        val state = CompositeLayoutState()
        val nav = twoPaneHost(state)

        runOnIdle { nav.pop() }
        waitForIdle()

        onNodeWithText("content:a:0").assertExists()
        onNodeWithText("content:b:0").assertDoesNotExist()
        runOnIdle { assertEquals(listOf(nav.currentEntry.id), state.layout.paneIds()) }
    }

    @Test
    fun the_edit_action_reveals_a_resize_handle_that_changes_the_split() = runComposeUiTest {
        val state = CompositeLayoutState()
        twoPaneHost(state)
        val initialFraction = runOnIdle { splitFraction(state) }

        enterEditMode()
        dragResizeHandle(between = paneBounds("body-a"), and = paneBounds("body-b"))

        runOnIdle {
            assertTrue(state.isEditMode)
            assertNotEquals(initialFraction, splitFraction(state))
        }
    }

    @Test
    fun dragging_a_pane_onto_another_docks_it_and_keeps_its_ui_state() = runComposeUiTest {
        val state = CompositeLayoutState()
        val nav = twoPaneHost(state)
        onNodeWithText("content:a:0").performClick()
        waitForIdle()
        val initialOrder = runOnIdle { state.layout.paneIds() }

        enterEditMode()
        dragPaneTo(title = "pane-a", target = paneBounds("body-b").center)

        runOnIdle {
            assertEquals(initialOrder.reversed(), state.layout.paneIds())
            assertEquals(listOf("b", "a"), destinationOrder(nav, state))
        }
        // The pane keeps its composition identity, so its counter moves with it.
        onNodeWithText("content:a:1").assertExists()
    }

    @Test
    fun dragging_to_the_scene_edge_docks_against_the_outermost_pane() = runComposeUiTest {
        val state = CompositeLayoutState()
        val nav = twoPaneHost(state)
        enterEditMode()
        val scene = paneBounds("body-a")

        dragPaneTo(title = "pane-b", target = Offset(scene.left + 2f, scene.center.y))

        runOnIdle {
            val root = state.layout.root
            assertTrue(root is CompositeLayoutNode.Split)
            assertEquals(CompositeOrientation.Horizontal, root.orientation)
            assertEquals(listOf("b", "a"), destinationOrder(nav, state))
        }
    }

    @Test
    fun a_drag_shows_a_floating_preview_that_disappears_on_drop() = runComposeUiTest {
        val state = CompositeLayoutState()
        twoPaneHost(state)
        enterEditMode()
        val handle = onNodeWithContentDescription("Move pane pane-a")
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val target = paneBounds("body-b").center

        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveTo(center + Offset(40f, 0f)) }
        handle.performTouchInput { moveTo(center + (target - start)) }
        waitForIdle()

        // The pane header and the floating preview both show the pane title.
        onAllNodesWithText("pane-a").assertCountEquals(2)
        runOnIdle { assertNotNull(state.draggedPaneId) }

        handle.performTouchInput { up() }
        waitForIdle()

        onAllNodesWithText("pane-a").assertCountEquals(1)
        runOnIdle { assertNull(state.draggedPaneId) }
    }

    @Test
    fun leaving_composition_cancels_an_active_drag() = runComposeUiTest {
        val state = CompositeLayoutState()
        var visible by mutableStateOf(true)
        lateinit var nav: NavController
        setContent {
            nav = rememberKompassNavController(A)
            if (visible) KompassNavigationHost(nav, persistentListOf(graphOf(state)))
        }
        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()
        enterEditMode()

        val handle = onNodeWithContentDescription("Move pane pane-a")
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveTo(center + Offset(80f, 0f)) }
        waitForIdle()
        runOnIdle { assertNotNull(state.draggedPaneId) }

        visible = false
        waitForIdle()

        runOnIdle { assertNull(state.draggedPaneId) }
    }

    @Test
    fun a_custom_pane_header_keeps_the_built_in_drag_gesture() = runComposeUiTest {
        val state = CompositeLayoutState()
        val layout = SceneLayoutComposite(
            state = state,
            paneTitle = { entry -> "pane-${entry.destinationId}" },
            labels = CompositeLayoutLabels(dragPane = { title -> "grab $title" }),
            paneHeaderContent = { header ->
                Row {
                    BasicText("header:${header.entry.destinationId}", Modifier.weight(1f))
                    BasicText("::", header.dragHandleModifier)
                    BasicText("edit", Modifier.clickable(onClick = header.enterEditMode))
                }
            },
        )
        lateinit var nav: NavController
        setContent {
            nav = rememberKompassNavController(A)
            KompassNavigationHost(nav, persistentListOf(graphOf(state, layout)))
        }
        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()
        val initialOrder = runOnIdle { state.layout.paneIds() }

        onNodeWithText("header:a").assertExists()
        onAllNodesWithText("edit")[0].performClick()
        waitForIdle()
        dragPaneTo(title = "pane-a", target = paneBounds("body-b").center, description = "grab pane-a")

        runOnIdle { assertEquals(initialOrder.reversed(), state.layout.paneIds()) }
    }

    @Test
    fun a_saved_arrangement_survives_process_death() = runComposeUiTest {
        val arrangement = CompositeLayoutSpec(
            root = CompositeLayoutNode.Split(
                orientation = CompositeOrientation.Vertical,
                first = CompositeLayoutNode.Pane("left"),
                second = CompositeLayoutNode.Pane("right"),
                firstFraction = 0.3f,
            ),
        )
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        lateinit var state: CompositeLayoutState
        setContent {
            if (mounted) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    state = rememberCompositeLayoutState()
                }
            }
        }
        runOnIdle { state.restore(arrangement) }

        lateinit var saved: Map<String, List<Any?>>
        runOnIdle { saved = registry.performSave(); mounted = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; mounted = true }

        runOnIdle { assertEquals(arrangement, state.layout) }
    }

    @Test
    fun a_corrupted_saved_arrangement_falls_back_to_an_empty_one() = runComposeUiTest {
        val corrupted = mapOf<String, List<Any?>>("rememberCompositeLayoutState" to listOf("not json"))
        lateinit var state: CompositeLayoutState
        setContent {
            CompositionLocalProvider(
                LocalSaveableStateRegistry provides SaveableStateRegistry(corrupted) { true },
            ) {
                state = rememberCompositeLayoutState()
            }
        }

        runOnIdle { assertEquals(CompositeLayoutSpec(), state.layout) }
    }

    @Test
    fun a_long_press_starts_a_drag_without_entering_edit_mode() = runComposeUiTest {
        val state = CompositeLayoutState()
        val nav = twoPaneHost(state)
        val initialOrder = runOnIdle { state.layout.paneIds() }

        val handle = onNodeWithContentDescription("Move pane pane-a")
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        val target = paneBounds("body-b").center
        handle.performTouchInput { down(center) }
        // The long press needs both the event time and the frame clock to pass the timeout.
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        handle.performTouchInput {
            advanceEventTime(1000)
            moveTo(center + Offset(40f, 0f))
        }
        handle.performTouchInput { moveTo(center + (target - start)) }
        handle.performTouchInput { up() }
        waitForIdle()

        runOnIdle {
            assertEquals(initialOrder.reversed(), state.layout.paneIds())
            assertEquals(listOf("b", "a"), destinationOrder(nav, state))
            assertFalse(state.isEditMode)
        }
    }

    /** Renders a host with two panes and returns its controller. */
    private fun ComposeUiTest.twoPaneHost(state: CompositeLayoutState): NavController {
        lateinit var nav: NavController
        setContent {
            nav = rememberKompassNavController(A)
            KompassNavigationHost(nav, persistentListOf(graphOf(state)))
        }
        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()
        return nav
    }

    private fun graphOf(
        state: CompositeLayoutState,
        layout: SceneLayout = SceneLayoutComposite(
            state = state,
            paneTitle = { entry -> "pane-${entry.destinationId}" },
        ),
    ) = Graph(layout) { entry ->
        var count by rememberSaveable { mutableStateOf(0) }
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "body-${entry.destinationId}" },
        ) {
            BasicText(
                text = "content:${entry.destinationId}:$count",
                modifier = Modifier.clickable { count++ },
            )
        }
    }

    private fun ComposeUiTest.enterEditMode() {
        onAllNodesWithContentDescription("Enter pane editing")[0].performClick()
        waitForIdle()
    }

    /** Drags a pane by its handle until the pointer reaches [target] in root coordinates. */
    private fun ComposeUiTest.dragPaneTo(
        title: String,
        target: Offset,
        description: String = "Move pane $title",
    ) {
        val handle = onNodeWithContentDescription(description)
        val start = handle.fetchSemanticsNode().boundsInRoot.center
        // The first move only passes the touch slop; the second one carries the real distance.
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveTo(center + Offset(40f, 0f)) }
        handle.performTouchInput { moveTo(center + (target - start)) }
        handle.performTouchInput { up() }
        waitForIdle()
    }

    /** Drags the resize handle that sits between two horizontally split panes. */
    private fun ComposeUiTest.dragResizeHandle(between: Rect, and: Rect) {
        val x = (between.right + and.left) / 2f
        val y = between.center.y
        onRoot().performTouchInput {
            down(Offset(x, y))
            moveTo(Offset(x + 40f, y))
            moveTo(Offset(x + 120f, y))
            up()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.paneBounds(description: String): Rect =
        onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    private fun splitFraction(state: CompositeLayoutState): Float =
        (state.layout.root as CompositeLayoutNode.Split).firstFraction

    private fun destinationOrder(nav: NavController, state: CompositeLayoutState): List<String> =
        state.layout.paneIds().map { paneId ->
            nav.backStack.first { it.id == paneId }.destinationId
        }
}
