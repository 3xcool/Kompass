package com.tekmoon.kompass.layout

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun updating_drag_position_tracks_the_pointer_until_drag_finishes() {
        val state = CompositeLayoutState()
        val initialPosition = Offset(120f, 240f)
        val updatedPosition = Offset(180f, 300f)

        state.beginDrag("pane", initialPosition)
        state.updateDragPosition(updatedPosition)

        assertEquals(updatedPosition, state.dragPosition)

        state.finishDrag()

        assertEquals(null, state.dragPosition)
    }

    @Test
    fun reconciling_a_removed_dragged_pane_cancels_the_transient_drag() {
        val state = CompositeLayoutState()

        state.beginDrag("removed", Offset(120f, 240f))
        state.reconcile(listOf("remaining"))

        assertEquals(null, state.draggedPaneId)
        assertEquals(null, state.dragPosition)
        assertEquals(null, state.dragTarget)
    }

    @Test
    fun restoring_an_arrangement_during_a_drag_fails_instead_of_losing_the_gesture() {
        val state = CompositeLayoutState()
        state.beginDrag("pane", Offset(10f, 10f))

        assertFailsWith<IllegalStateException> { state.restore(CompositeLayoutSpec()) }
    }

    @Test
    fun a_restored_arrangement_replaces_the_current_one() {
        val state = CompositeLayoutState()
        val restored = layoutSpec()

        state.restore(restored)

        assertEquals(restored, state.layout)
    }

    @Test
    fun an_arrangement_rejects_a_duplicated_pane() {
        assertFailsWith<IllegalArgumentException> {
            CompositeLayoutSpec(
                root = CompositeLayoutNode.Split(
                    orientation = CompositeOrientation.Horizontal,
                    first = CompositeLayoutNode.Pane("a"),
                    second = CompositeLayoutNode.Pane("a"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> { CompositeLayoutNode.Pane("") }
        assertFailsWith<IllegalArgumentException> {
            CompositeLayoutNode.Split(
                orientation = CompositeOrientation.Horizontal,
                first = CompositeLayoutNode.Pane("a"),
                second = CompositeLayoutNode.Pane("b"),
                firstFraction = 0.95f,
            )
        }
    }

    @Test
    fun moving_a_pane_that_is_not_on_screen_keeps_the_arrangement() {
        val subject = layoutSpec()

        assertEquals(subject, subject.movePane("missing", "a", CompositeDockEdge.Left))
        assertEquals(subject, subject.movePane("a", "missing", CompositeDockEdge.Left))
        assertFailsWith<IllegalArgumentException> {
            subject.movePane("a", "a", CompositeDockEdge.Left)
        }
        assertEquals(
            CompositeLayoutSpec(),
            CompositeLayoutSpec().movePane("a", "b", CompositeDockEdge.Left),
        )
    }

    @Test
    fun reconciliation_rejects_a_repeated_occurrence() {
        assertFailsWith<IllegalArgumentException> { layoutSpec().reconcile(listOf("a", "a")) }
    }

    @Test
    fun an_empty_arrangement_reports_no_panes() {
        val empty = CompositeLayoutSpec()

        assertEquals(emptyList(), empty.paneIds())
        assertEquals(listOf("a"), empty.reconcile(listOf("a")).paneIds())
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
