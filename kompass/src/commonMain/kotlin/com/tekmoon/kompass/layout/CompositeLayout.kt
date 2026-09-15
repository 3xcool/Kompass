package com.tekmoon.kompass.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Opt-in marker for the advanced, user-controlled pane arrangement APIs. */
@RequiresOptIn(
    message = "Composite pane layouts are an optional advanced use case and may evolve.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalKompassCompositeLayoutApi

/** The axis on which two panes are split. */
@Serializable
enum class CompositeOrientation {
    Horizontal,
    Vertical,
}

/** The edge of a target pane where a dragged pane should be docked. */
@Serializable
enum class CompositeDockEdge {
    Left,
    Right,
    Top,
    Bottom,
}

/** A serializable pane arrangement tree. */
@Serializable
sealed interface CompositeLayoutNode {

    /** A leaf that identifies one visible navigation occurrence. */
    @Serializable
    @SerialName("pane")
    data class Pane(val paneId: String) : CompositeLayoutNode {
        init {
            require(paneId.isNotBlank()) { "paneId must not be blank" }
        }
    }

    /** Two child nodes sharing the available space on [orientation]. */
    @Serializable
    @SerialName("split")
    data class Split(
        val orientation: CompositeOrientation,
        val first: CompositeLayoutNode,
        val second: CompositeLayoutNode,
        val firstFraction: Float = 0.5f,
    ) : CompositeLayoutNode {
        init {
            require(firstFraction in 0.1f..0.9f) {
                "firstFraction must be between 0.1 and 0.9"
            }
        }
    }
}

/**
 * Persistable arrangement state for the optional composite layout.
 *
 * Pane IDs are application-owned. The default layout uses [KompassEntry.id], while a server
 * driven shell can provide deterministic IDs through [SceneLayoutComposite.paneId].
 */
@Serializable
data class CompositeLayoutSpec(
    val root: CompositeLayoutNode? = null,
) {
    init {
        val paneIds = root?.paneIds().orEmpty()
        require(paneIds.size == paneIds.toSet().size) {
            "A composite layout cannot contain duplicate pane IDs"
        }
    }

    /** Returns pane IDs in visual tree order. */
    fun paneIds(): List<String> = root?.paneIds().orEmpty()

    /** Adds/removes navigation occurrences without changing the saved arrangement of survivors. */
    fun reconcile(visiblePaneIds: List<String>): CompositeLayoutSpec {
        require(visiblePaneIds.size == visiblePaneIds.toSet().size) {
            "visiblePaneIds must be unique"
        }

        val visible = visiblePaneIds.toSet()
        var reconciledRoot = root?.retainPanes(visible)
        val retained = reconciledRoot?.paneIds().orEmpty().toSet()

        visiblePaneIds.filterNot(retained::contains).forEach { paneId ->
            reconciledRoot = reconciledRoot.appendPane(paneId)
        }

        return CompositeLayoutSpec(reconciledRoot)
    }

    /** Returns a new arrangement with [paneId] docked at [targetPaneId]. */
    fun movePane(
        paneId: String,
        targetPaneId: String,
        edge: CompositeDockEdge,
    ): CompositeLayoutSpec {
        require(paneId != targetPaneId) { "A pane cannot be docked against itself" }
        val currentRoot = root ?: return this
        if (paneId !in paneIds() || targetPaneId !in paneIds()) return this

        val withoutDraggedPane = currentRoot.removePane(paneId) ?: return this
        val movedRoot = withoutDraggedPane.insertPane(
            paneId = paneId,
            targetPaneId = targetPaneId,
            edge = edge,
        )
        return CompositeLayoutSpec(movedRoot)
    }

    /** Returns a new arrangement with the selected split resized. */
    fun resizeSplit(path: List<SplitBranch>, firstFraction: Float): CompositeLayoutSpec {
        val clampedFraction = firstFraction.coerceIn(0.1f, 0.9f)
        return CompositeLayoutSpec(root?.resizeSplit(path, clampedFraction))
    }
}

/** Identifies a split while traversing the arrangement tree. */
@Serializable
enum class SplitBranch {
    First,
    Second,
}

private fun CompositeLayoutNode.paneIds(): List<String> = when (this) {
    is CompositeLayoutNode.Pane -> listOf(paneId)
    is CompositeLayoutNode.Split -> first.paneIds() + second.paneIds()
}

private fun CompositeLayoutNode.retainPanes(visible: Set<String>): CompositeLayoutNode? = when (this) {
    is CompositeLayoutNode.Pane -> takeIf { paneId in visible }
    is CompositeLayoutNode.Split -> {
        val retainedFirst = first.retainPanes(visible)
        val retainedSecond = second.retainPanes(visible)
        when {
            retainedFirst == null -> retainedSecond
            retainedSecond == null -> retainedFirst
            else -> copy(first = retainedFirst, second = retainedSecond)
        }
    }
}

private fun CompositeLayoutNode?.appendPane(paneId: String): CompositeLayoutNode = when (this) {
    null -> CompositeLayoutNode.Pane(paneId)
    else -> CompositeLayoutNode.Split(
        orientation = CompositeOrientation.Horizontal,
        first = this,
        second = CompositeLayoutNode.Pane(paneId),
        firstFraction = 0.7f,
    )
}

private fun CompositeLayoutNode.removePane(paneId: String): CompositeLayoutNode? = when (this) {
    is CompositeLayoutNode.Pane -> takeUnless { it.paneId == paneId }
    is CompositeLayoutNode.Split -> {
        val newFirst = first.removePane(paneId)
        val newSecond = second.removePane(paneId)
        when {
            newFirst == first && newSecond == second -> this
            newFirst == null -> newSecond
            newSecond == null -> newFirst
            else -> copy(first = newFirst, second = newSecond)
        }
    }
}

private fun CompositeLayoutNode.insertPane(
    paneId: String,
    targetPaneId: String,
    edge: CompositeDockEdge,
): CompositeLayoutNode = when (this) {
    is CompositeLayoutNode.Pane -> if (this.paneId == targetPaneId) {
        val orientation = when (edge) {
            CompositeDockEdge.Left,
            CompositeDockEdge.Right -> CompositeOrientation.Horizontal
            CompositeDockEdge.Top,
            CompositeDockEdge.Bottom -> CompositeOrientation.Vertical
        }
        val draggedFirst = edge == CompositeDockEdge.Left || edge == CompositeDockEdge.Top
        CompositeLayoutNode.Split(
            orientation = orientation,
            first = if (draggedFirst) CompositeLayoutNode.Pane(paneId) else this,
            second = if (draggedFirst) this else CompositeLayoutNode.Pane(paneId),
            firstFraction = if (draggedFirst) 0.35f else 0.65f,
        )
    } else {
        this
    }

    is CompositeLayoutNode.Split -> {
        val updatedFirst = first.insertPane(paneId, targetPaneId, edge)
        if (updatedFirst != first) {
            copy(first = updatedFirst)
        } else {
            copy(second = second.insertPane(paneId, targetPaneId, edge))
        }
    }
}

private fun CompositeLayoutNode.resizeSplit(
    path: List<SplitBranch>,
    firstFraction: Float,
): CompositeLayoutNode = when {
    path.isEmpty() && this is CompositeLayoutNode.Split -> copy(firstFraction = firstFraction)
    this is CompositeLayoutNode.Split -> when (path.first()) {
        SplitBranch.First -> copy(first = first.resizeSplit(path.drop(1), firstFraction))
        SplitBranch.Second -> copy(second = second.resizeSplit(path.drop(1), firstFraction))
    }
    else -> this
}
