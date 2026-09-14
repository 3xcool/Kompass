package com.tekmoon.kompass.layout

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

@OptIn(ExperimentalKompassCompositeLayoutApi::class)
class CompositeLayoutTest {

    @Test
    fun reconciliation_removes_popped_panes_and_appends_new_occurrences() {
        val subject = layoutSpec()

        val reconciled = subject.reconcile(listOf("a", "c", "d"))

        assertEquals(listOf("a", "c", "d"), reconciled.paneIds())
        assertTrue(reconciled.root!!.containsPane("d"))
    }

    @Test
    fun moving_a_pane_changes_visual_order_without_changing_pane_identity() {
        val subject = layoutSpec()

        val moved = subject.movePane("a", "c", CompositeDockEdge.Right)

        assertEquals(listOf("b", "c", "a"), moved.paneIds())
        assertEquals(listOf("a", "b", "c"), subject.paneIds())
    }

    @Test
    fun resizing_a_split_clamps_the_fraction_to_usable_pane_bounds() {
        val subject = layoutSpec()

        val resized = subject.resizeSplit(emptyList(), 0.99f)

        val root = resized.root as CompositeLayoutNode.Split
        assertEquals(0.9f, root.firstFraction)
    }

    @Test
    fun layout_spec_survives_serialization_for_session_or_server_restoration() {
        val subject = layoutSpec()
        val json = Json.encodeToString(subject)

        val restored = Json.decodeFromString<CompositeLayoutSpec>(json)

        assertEquals(subject, restored)
    }

    @Test
    fun exiting_edit_mode_leaves_the_layout_ready_for_normal_interaction() {
        val state = CompositeLayoutState()

        state.enterEditMode()
        state.exitEditMode()

        assertFalse(state.isEditMode)
        assertEquals(null, state.draggedPaneId)
        assertEquals(null, state.dragTarget)
    }

    @Test
    fun cancelling_drag_clears_the_transient_preview_position() {
        val state = CompositeLayoutState()
        val position = Offset(120f, 240f)

        state.beginDrag("pane", position)

        assertEquals(position, state.dragPosition)

        state.cancelDrag()

        assertEquals(null, state.dragPosition)
    }

    private fun layoutSpec(): CompositeLayoutSpec = CompositeLayoutSpec(
        root = CompositeLayoutNode.Split(
            orientation = CompositeOrientation.Horizontal,
            first = CompositeLayoutNode.Pane("a"),
            second = CompositeLayoutNode.Split(
                orientation = CompositeOrientation.Vertical,
                first = CompositeLayoutNode.Pane("b"),
                second = CompositeLayoutNode.Pane("c"),
            ),
            firstFraction = 0.6f,
        ),
    )
}

private fun CompositeLayoutNode.containsPane(paneId: String): Boolean = when (this) {
    is CompositeLayoutNode.Pane -> this.paneId == paneId
    is CompositeLayoutNode.Split -> first.containsPane(paneId) || second.containsPane(paneId)
}
