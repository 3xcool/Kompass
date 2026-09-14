@file:OptIn(ExperimentalKompassCompositeLayoutApi::class)

package com.tekmoon.kompass.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavDirection
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.SceneLayout
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.Json

/** Mutable UI holder for [CompositeLayoutSpec] and transient edit gestures. */
@Stable
class CompositeLayoutState(
    initialLayout: CompositeLayoutSpec = CompositeLayoutSpec(),
) {
    var layout: CompositeLayoutSpec by mutableStateOf(initialLayout)
        private set

    var isEditMode: Boolean by mutableStateOf(false)
        private set

    var draggedPaneId: String? by mutableStateOf(null)
        private set

    internal var dragPosition: Offset? by mutableStateOf(null)
        private set

    var dragTarget: CompositeDropTarget? by mutableStateOf(null)
        private set

    /** Applies a server/session-provided arrangement without changing navigation state. */
    fun restore(layout: CompositeLayoutSpec) {
        check(draggedPaneId == null) { "Cannot restore a composite layout during a drag" }
        this.layout = layout
    }

    fun enterEditMode() {
        isEditMode = true
    }

    fun exitEditMode() {
        cancelDrag()
        isEditMode = false
    }

    internal fun reconcile(visiblePaneIds: List<String>) {
        val reconciled = layout.reconcile(visiblePaneIds)
        if (reconciled != layout) layout = reconciled
    }

    internal fun resize(path: List<SplitBranch>, firstFraction: Float) {
        layout = layout.resizeSplit(path, firstFraction)
    }

    /** Starts a transient drag without changing whether persistent edit mode is enabled. */
    internal fun beginDrag(paneId: String, position: Offset) {
        draggedPaneId = paneId
        dragPosition = position
        dragTarget = null
    }

    internal fun updateDragPosition(position: Offset) {
        if (draggedPaneId != null) dragPosition = position
    }

    internal fun updateDragTarget(target: CompositeDropTarget?) {
        if (draggedPaneId != null) dragTarget = target
    }

    internal fun finishDrag() {
        val dragged = draggedPaneId
        val target = dragTarget
        if (dragged != null && target != null) {
            layout = layout.movePane(dragged, target.paneId, target.edge)
        }
        cancelDrag()
    }

    internal fun cancelDrag() {
        draggedPaneId = null
        dragPosition = null
        dragTarget = null
    }
}

/** Creates a saveable state holder for an optional composite layout. */
@Composable
@ExperimentalKompassCompositeLayoutApi
fun rememberCompositeLayoutState(
    initialLayout: CompositeLayoutSpec = CompositeLayoutSpec(),
): CompositeLayoutState = rememberSaveable(saver = CompositeLayoutStateSaver) {
    CompositeLayoutState(initialLayout)
}

private val CompositeLayoutStateSaver = Saver<CompositeLayoutState, String>(
    save = { state -> Json.encodeToString(state.layout) },
    restore = { savedLayout ->
        runCatching {
            CompositeLayoutState(Json.decodeFromString<CompositeLayoutSpec>(savedLayout))
        }.getOrNull()
    },
)

/** A transient target shown while a pane is being dragged. */
data class CompositeDropTarget(
    val paneId: String,
    val edge: CompositeDockEdge,
)

/** Labels used by the built-in chrome. Supply localized strings from the host application. */
data class CompositeLayoutLabels(
    val dragPane: @Composable (String) -> String = { paneId -> "Move pane $paneId" },
    val enterEditMode: @Composable () -> String = { "Enter pane editing" },
    val enterEditModeText: @Composable () -> String = { "Edit" },
    val exitEditMode: @Composable () -> String = { "Exit pane editing" },
    val exitEditModeText: @Composable () -> String = { "Done" },
)

/** Scope supplied to a custom pane header. Apply [dragHandleModifier] to its drag affordance. */
class CompositePaneHeaderScope internal constructor(
    val entry: BackStackEntry,
    val isEditMode: Boolean,
    val dragHandleModifier: Modifier,
    val enterEditMode: () -> Unit,
    val exitEditMode: () -> Unit,
)

/** Optional built-in layout for resizable and draggable multi-pane navigation shells. */
@ExperimentalKompassCompositeLayoutApi
class SceneLayoutComposite(
    private val state: CompositeLayoutState = CompositeLayoutState(),
    val paneId: (BackStackEntry) -> String = { it.id },
    private val paneTitle: (BackStackEntry) -> String = { it.destinationId },
    private val paneTitleContent: (@Composable (BackStackEntry) -> String)? = null,
    private val labels: CompositeLayoutLabels = CompositeLayoutLabels(),
    private val paneHeaderContent: (@Composable (CompositePaneHeaderScope) -> Unit)? = null,
) : SceneLayout {

    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection,
    ) {
        val bounds = remember { mutableMapOf<String, Rect>() }
        val visiblePaneIds = backStack.map(paneId)
        val effectiveLayout = state.layout.reconcile(visiblePaneIds)
        var rootPosition by remember { mutableStateOf(Offset.Zero) }
        SideEffect {
            state.reconcile(visiblePaneIds)
            bounds.keys.retainAll(visiblePaneIds)
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    rootPosition = coordinates.boundsInRoot().topLeft
                },
        ) {
            effectiveLayout.root?.let { root ->
                RenderNode(
                    node = root,
                    path = emptyList(),
                    backStack = backStack,
                    resolve = resolve,
                    navController = navController,
                    bounds = bounds,
                )
            }
            DragPreview(
                backStack = backStack,
                bounds = bounds,
                rootPosition = rootPosition,
            )
        }
    }

    @Composable
    private fun RenderNode(
        node: CompositeLayoutNode,
        path: List<SplitBranch>,
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        bounds: MutableMap<String, Rect>,
    ) {
        when (node) {
            is CompositeLayoutNode.Pane -> {
                val entry = backStack.firstOrNull { paneId(it) == node.paneId } ?: return
                // Keep the pane's composition identity attached to its entry, not its visual slot.
                // A drag-and-drop move changes the tree position; the list/ViewModel state must move
                // with the pane instead of being recreated for the new slot.
                key(node.paneId) {
                    val (graph, destination) = remember(entry, resolve) { resolve(entry) }
                    PaneHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                bounds[node.paneId] = coordinates.boundsInRoot()
                            }
                            .alpha(if (state.draggedPaneId == node.paneId) 0.35f else 1f),
                        entry = entry,
                        graph = graph,
                        destination = destination,
                        navController = navController,
                        bounds = bounds,
                    )
                }
            }

            is CompositeLayoutNode.Split -> {
                val splitModifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        splitBounds[path] = coordinates.boundsInRoot()
                    }
                when (node.orientation) {
                    CompositeOrientation.Horizontal -> Row(splitModifier) {
                        Box(Modifier.weight(node.firstFraction).fillMaxHeight()) {
                            RenderNode(node.first, path + SplitBranch.First, backStack, resolve, navController, bounds)
                        }
                        ResizeHandle(
                            path = path,
                            orientation = CompositeOrientation.Horizontal,
                        )
                        Box(Modifier.weight(1f - node.firstFraction).fillMaxHeight()) {
                            RenderNode(node.second, path + SplitBranch.Second, backStack, resolve, navController, bounds)
                        }
                    }

                    CompositeOrientation.Vertical -> Column(splitModifier) {
                        Box(Modifier.weight(node.firstFraction).fillMaxWidth()) {
                            RenderNode(node.first, path + SplitBranch.First, backStack, resolve, navController, bounds)
                        }
                        ResizeHandle(
                            path = path,
                            orientation = CompositeOrientation.Vertical,
                        )
                        Box(Modifier.weight(1f - node.firstFraction).fillMaxWidth()) {
                            RenderNode(node.second, path + SplitBranch.Second, backStack, resolve, navController, bounds)
                        }
                    }
                }
            }
        }
    }

    private val splitBounds = mutableMapOf<List<SplitBranch>, Rect>()

    @Composable
    private fun ResizeHandle(
        path: List<SplitBranch>,
        orientation: CompositeOrientation,
    ) {
        if (!state.isEditMode) {
            Spacer(
                when (orientation) {
                    CompositeOrientation.Horizontal -> Modifier.width(1.dp).fillMaxHeight()
                    CompositeOrientation.Vertical -> Modifier.height(1.dp).fillMaxWidth()
                },
            )
            return
        }

        val handleModifier = when (orientation) {
            CompositeOrientation.Horizontal -> Modifier
                .width(12.dp)
                .fillMaxHeight()
            CompositeOrientation.Vertical -> Modifier
                .height(12.dp)
                .fillMaxWidth()
        }

        Box(
            modifier = handleModifier
                .background(CompositeLayoutDefaults.resizeHandleColor)
                .pointerInput(path, orientation) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val splitSize = splitBounds[path]?.let {
                                if (orientation == CompositeOrientation.Horizontal) it.width else it.height
                            } ?: return@detectDragGestures
                            if (splitSize > 0f) {
                                val delta = if (orientation == CompositeOrientation.Horizontal) {
                                    dragAmount.x
                                } else {
                                    dragAmount.y
                                }
                                val fraction = state.layout.root
                                    ?.fractionAt(path)
                                    ?.plus(delta / splitSize)
                                if (fraction != null) state.resize(path, fraction)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(4.dp)
                    .background(CompositeLayoutDefaults.resizeGripColor, RoundedCornerShape(2.dp)),
            )
        }
    }

    @Composable
    private fun PaneHost(
        modifier: Modifier,
        entry: BackStackEntry,
        graph: NavigationGraph,
        destination: Destination,
        navController: NavController,
        bounds: MutableMap<String, Rect>,
    ) {
        val handleBounds = remember(entry.id) { mutableStateOf<Rect?>(null) }
        Box(modifier) {
            Column(Modifier.fillMaxSize()) {
                PaneHeader(
                    entry = entry,
                    handleBounds = handleBounds,
                    bounds = bounds,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    graph.Content(
                        entry = entry,
                        destination = destination,
                        navController = navController,
                    )
                }
            }

            AnimatedVisibility(
                visible = state.draggedPaneId != null && state.draggedPaneId != paneId(entry),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                DockTargets(
                    paneId = paneId(entry),
                )
            }
        }
    }

    @Composable
    private fun DragPreview(
        backStack: ImmutableList<BackStackEntry>,
        bounds: Map<String, Rect>,
        rootPosition: Offset,
    ) {
        val draggedPaneId = state.draggedPaneId ?: return
        val entry = backStack.firstOrNull { paneId(it) == draggedPaneId } ?: return
        val sourceBounds = bounds[draggedPaneId]
        val density = LocalDensity.current
        val minWidthPx = with(density) { 160.dp.toPx() }
        val maxWidthPx = with(density) { 360.dp.toPx() }
        val previewWidthPx = (sourceBounds?.width ?: with(density) { 240.dp.toPx() })
            .coerceIn(minWidthPx, maxWidthPx)
        val previewWidth = with(density) { previewWidthPx.toDp() }
        val previewHeight = 96.dp
        val previewHeightPx = with(density) { previewHeight.toPx() }
        val title = paneTitleContent?.invoke(entry) ?: paneTitle(entry)

        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .graphicsLayer {
                    val position = state.dragPosition ?: Offset.Zero
                    translationX = position.x - rootPosition.x - previewWidthPx / 2f
                    translationY = position.y - rootPosition.y - previewHeightPx / 3f
                }
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(CompositeLayoutDefaults.dragPreviewColor, RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = CompositeLayoutDefaults.dragPreviewBorderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                BasicText(title)
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(CompositeLayoutDefaults.dragPreviewLineColor, RoundedCornerShape(4.dp)),
                )
                Spacer(
                    Modifier
                        .fillMaxWidth(0.65f)
                        .height(8.dp)
                        .background(CompositeLayoutDefaults.dragPreviewLineColor, RoundedCornerShape(4.dp)),
                )
            }
        }
    }

    @Composable
    private fun PaneHeader(
        entry: BackStackEntry,
        handleBounds: androidx.compose.runtime.MutableState<Rect?>,
        bounds: Map<String, Rect>,
    ) {
        val edgeDropThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        val title = paneTitleContent?.invoke(entry) ?: paneTitle(entry)
        var dragPosition = Offset.Zero
        val dragDescription = labels.dragPane(title)
        val dragHandleModifier = Modifier
            .size(32.dp)
            .onGloballyPositioned { coordinates ->
                handleBounds.value = coordinates.boundsInRoot()
            }
            .pointerInput(entry.id, state.isEditMode) {
                val onDragStart: (Offset) -> Unit = { offset ->
                    dragPosition = handleBounds.value?.topLeft?.plus(offset) ?: Offset.Zero
                    state.beginDrag(paneId(entry), dragPosition)
                    state.updateDragTarget(findDropTarget(bounds, dragPosition, edgeDropThreshold))
                }
                val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = {
                        change, dragAmount ->
                    change.consume()
                    dragPosition += dragAmount
                    state.updateDragPosition(dragPosition)
                    updateDragTarget(bounds, dragPosition, edgeDropThreshold)
                }
                if (state.isEditMode) {
                    detectDragGestures(
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = { state.finishDrag() },
                        onDragCancel = { state.cancelDrag() },
                    )
                } else {
                    detectDragGesturesAfterLongPress(
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = { state.finishDrag() },
                        onDragCancel = { state.cancelDrag() },
                    )
                }
            }
            .semantics { contentDescription = dragDescription }

        val scope = CompositePaneHeaderScope(
            entry = entry,
            isEditMode = state.isEditMode,
            dragHandleModifier = dragHandleModifier,
            enterEditMode = state::enterEditMode,
            exitEditMode = state::exitEditMode,
        )

        if (paneHeaderContent != null) {
            paneHeaderContent.invoke(scope)
        } else {
            DefaultPaneHeader(scope, title)
        }
    }

    @Composable
    private fun DefaultPaneHeader(scope: CompositePaneHeaderScope, title: String) {
        val enterDescription = labels.enterEditMode()
        val enterText = labels.enterEditModeText()
        val exitDescription = labels.exitEditMode()
        val exitText = labels.exitEditModeText()
        val actionDescription = if (scope.isEditMode) exitDescription else enterDescription
        val actionText = if (scope.isEditMode) exitText else enterText
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(CompositeLayoutDefaults.headerColor)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "⋮⋮",
                modifier = scope.dragHandleModifier,
            )
            Spacer(Modifier.width(4.dp))
            BasicText(title, Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clickable(
                        onClick = if (scope.isEditMode) scope.exitEditMode else scope.enterEditMode,
                    )
                    .background(
                        color = CompositeLayoutDefaults.exitEditModeColor,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = actionDescription },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(actionText)
            }
        }
    }

    @Composable
    private fun DockTargets(
        paneId: String,
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            DockTarget(
                modifier = Modifier.align(Alignment.CenterStart),
                target = CompositeDropTarget(paneId, CompositeDockEdge.Left),
            )
            DockTarget(
                modifier = Modifier.align(Alignment.CenterEnd),
                target = CompositeDropTarget(paneId, CompositeDockEdge.Right),
            )
            DockTarget(
                modifier = Modifier.align(Alignment.TopCenter),
                target = CompositeDropTarget(paneId, CompositeDockEdge.Top),
            )
            DockTarget(
                modifier = Modifier.align(Alignment.BottomCenter),
                target = CompositeDropTarget(paneId, CompositeDockEdge.Bottom),
            )
        }
    }

    @Composable
    private fun DockTarget(
        modifier: Modifier,
        target: CompositeDropTarget,
    ) {
        val active = state.dragTarget == target
        Box(
            modifier
                .size(28.dp)
                .background(
                    if (active) CompositeLayoutDefaults.activeDockTargetColor
                    else CompositeLayoutDefaults.dockTargetColor,
                    RoundedCornerShape(14.dp),
                )
                .border(
                    width = 1.dp,
                    color = CompositeLayoutDefaults.dockTargetBorderColor,
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    }

    private fun updateDragTarget(
        bounds: Map<String, Rect>,
        pointerPosition: Offset,
        edgeDropThreshold: Float,
    ) {
        state.updateDragTarget(findDropTarget(bounds, pointerPosition, edgeDropThreshold))
    }

    private fun findDropTarget(
        bounds: Map<String, Rect>,
        pointerPosition: Offset,
        edgeDropThreshold: Float,
    ): CompositeDropTarget? {
        val draggedPaneId = state.draggedPaneId ?: return null
        val sceneBounds = bounds.sceneBounds() ?: return null
        val edgeTarget = when {
            pointerPosition.x <= sceneBounds.left + edgeDropThreshold -> bounds.outermostPaneId(
                draggedPaneId,
                CompositeDockEdge.Left,
            )?.let { CompositeDropTarget(it, CompositeDockEdge.Left) }

            pointerPosition.x >= sceneBounds.right - edgeDropThreshold -> bounds.outermostPaneId(
                draggedPaneId,
                CompositeDockEdge.Right,
            )?.let { CompositeDropTarget(it, CompositeDockEdge.Right) }

            pointerPosition.y <= sceneBounds.top + edgeDropThreshold -> bounds.outermostPaneId(
                draggedPaneId,
                CompositeDockEdge.Top,
            )?.let { CompositeDropTarget(it, CompositeDockEdge.Top) }

            pointerPosition.y >= sceneBounds.bottom - edgeDropThreshold -> bounds.outermostPaneId(
                draggedPaneId,
                CompositeDockEdge.Bottom,
            )?.let { CompositeDropTarget(it, CompositeDockEdge.Bottom) }

            else -> null
        }
        if (edgeTarget != null) return edgeTarget

        val target = bounds.entries.firstOrNull { (paneId, bounds) ->
            paneId != draggedPaneId && bounds.contains(pointerPosition)
        } ?: return null
        val paneId = target.key
        val bounds = target.value
        val horizontalDistance = pointerPosition.x - bounds.left
        val verticalDistance = pointerPosition.y - bounds.top
        val horizontalRatio = horizontalDistance / bounds.width
        val verticalRatio = verticalDistance / bounds.height
        val edge = if (horizontalRatio < 0.25f) {
            CompositeDockEdge.Left
        } else if (horizontalRatio > 0.75f) {
            CompositeDockEdge.Right
        } else if (verticalRatio < 0.25f) {
            CompositeDockEdge.Top
        } else {
            CompositeDockEdge.Bottom
        }
        return CompositeDropTarget(paneId, edge)
    }
}

private fun Map<String, Rect>.sceneBounds(): Rect? {
    val first = values.firstOrNull() ?: return null
    return values.drop(1).fold(first) { current, next ->
        Rect(
            left = minOf(current.left, next.left),
            top = minOf(current.top, next.top),
            right = maxOf(current.right, next.right),
            bottom = maxOf(current.bottom, next.bottom),
        )
    }
}

private fun Map<String, Rect>.outermostPaneId(
    draggedPaneId: String?,
    edge: CompositeDockEdge,
): String? = asSequence()
    .filter { (paneId, _) -> paneId != draggedPaneId }
    .maxByOrNull { (_, bounds) ->
        when (edge) {
            CompositeDockEdge.Left -> -bounds.left
            CompositeDockEdge.Right -> bounds.right
            CompositeDockEdge.Top -> -bounds.top
            CompositeDockEdge.Bottom -> bounds.bottom
        }
    }
    ?.key

private object CompositeLayoutDefaults {
    val resizeHandleColor = Color(0xFFBDB5D9)
    val resizeGripColor = Color(0xFF6650A4)
    val headerColor = Color(0xFFF2F0F7)
    val exitEditModeColor = Color(0xFFE7E0F8)
    val dockTargetColor = Color(0x556650A4)
    val activeDockTargetColor = Color(0xCC6650A4)
    val dockTargetBorderColor = Color(0xFF6650A4)
    val dragPreviewColor = Color(0xFFF9F7FF)
    val dragPreviewBorderColor = Color(0xFF6650A4)
    val dragPreviewLineColor = Color(0xFFE3DDF0)
}

private fun CompositeLayoutNode.fractionAt(path: List<SplitBranch>): Float? = when {
    path.isEmpty() && this is CompositeLayoutNode.Split -> firstFraction
    this is CompositeLayoutNode.Split -> when (path.first()) {
        SplitBranch.First -> first.fractionAt(path.drop(1))
        SplitBranch.Second -> second.fractionAt(path.drop(1))
    }
    else -> null
}
