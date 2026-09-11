package com.tekmoon.kompass

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.SavedState

/** Controller-owned, main-thread-confined entry resources; no process-global owner registry. */
internal class KompassOwnerStore(restored: Map<String, SavedState> = emptyMap()) {
    private val owners = mutableMapOf<String, KompassEntryOwner>()
    private val restoredStates = restored.toMutableMap()
    private val references = mutableMapOf<String, Int>()
    private var liveIds = emptySet<String>()
    private var topId: String? = null
    private var hostState = Lifecycle.State.CREATED
    private var closed = false
    var platformExtras: CreationExtras = CreationExtras.Empty

    fun owner(entry: BackStackEntry): KompassEntryOwner {
        check(!closed) { "The Kompass controller has been disposed" }
        return owners.getOrPut(entry.id) { KompassEntryOwner(restoredStates.remove(entry.id), platformExtras) }
    }

    fun reconcile(entries: List<BackStackEntry>) {
        val ids = entries.map { it.id }.toSet()
        require(ids.size == entries.size) { "Each back-stack occurrence needs a distinct BackStackEntry.id" }
        liveIds = ids
        topId = entries.lastOrNull()?.id
        restoredStates.keys.retainAll(ids)
        owners.keys.toList().forEach(::update)
    }

    fun retain(entry: BackStackEntry) {
        references[entry.id] = (references[entry.id] ?: 0) + 1
        NavigationScopes.retain(entry.scopeId)
        update(entry.id)
    }

    fun release(entry: BackStackEntry) {
        val count = checkNotNull(references[entry.id]) - 1
        if (count == 0) references.remove(entry.id) else references[entry.id] = count
        update(entry.id)
        NavigationScopes.release(entry.scopeId)
    }

    fun updateHostLifecycle(state: Lifecycle.State) {
        // Activity destruction during recreation must not destroy retained entry ViewModels.
        // Permanent disposal goes through close(), independently of the host lifecycle.
        hostState = if (state < Lifecycle.State.CREATED) Lifecycle.State.CREATED else state
        owners.keys.toList().forEach(::update)
    }

    private fun update(id: String) {
        val rendered = (references[id] ?: 0) > 0
        if ((closed || id !in liveIds) && !rendered) {
            owners.remove(id)?.clear()
            return
        }
        val target = when {
            !rendered -> Lifecycle.State.CREATED
            id == topId -> Lifecycle.State.RESUMED
            else -> Lifecycle.State.STARTED
        }
        owners[id]?.moveTo(minOf(hostState, target))
    }

    fun save(): Map<String, SavedState> = buildMap {
        putAll(restoredStates.filterKeys { it in liveIds })
        owners.filterKeys { it in liveIds }.forEach { (id, owner) -> put(id, owner.save()) }
    }

    fun close() {
        closed = true
        liveIds = emptySet()
        restoredStates.clear()
        owners.keys.toList().forEach(::update)
    }
}
