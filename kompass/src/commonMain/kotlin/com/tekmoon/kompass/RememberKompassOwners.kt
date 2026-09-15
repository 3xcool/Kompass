package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import com.tekmoon.kompass.util.randomUUID

internal class KompassOwnerStores(restored: SavedState? = null) : ViewModel() {
    private val stores = mutableMapOf<String, KompassOwnerStore>()
    private val restoredStores = restored?.read { toMap() }?.toMutableMap() ?: mutableMapOf()

    fun getOrCreate(id: String, fallback: Map<String, SavedState>): KompassOwnerStore = stores.getOrPut(id) {
        @Suppress("UNCHECKED_CAST")
        val restored = (restoredStores.remove(id) as? SavedState)?.read { toMap() } as? Map<String, SavedState>
        KompassOwnerStore(restored ?: fallback)
    }

    fun save(): SavedState = savedState(buildMap {
        putAll(restoredStores)
        stores.forEach { (id, store) -> put(id, savedState(store.save())) }
    })

    fun remove(id: String) {
        restoredStores.remove(id)
        stores.remove(id)?.close()
    }

    fun close() {
        stores.values.forEach { it.close() }
        stores.clear()
        restoredStores.clear()
    }

    override fun onCleared() = close()
}

@Composable
internal fun rememberKompassOwnerStore(entries: List<KompassEntry>): KompassOwnerStore {
    val id = rememberSaveable { randomUUID() }
    val parentRegistry = LocalSaveableStateRegistry.current
    val parentOwner = LocalViewModelStoreOwner.current
    val retained = when (parentOwner) {
        is KompassEntryOwner -> parentOwner.childStores
        null -> null
        else -> viewModel<KompassOwnerStores>(viewModelStoreOwner = parentOwner) { KompassOwnerStores() }
    }
    val store = remember(id, retained) {
        @Suppress("UNCHECKED_CAST")
        val restored = parentRegistry?.consumeRestored("kompass:owners:$id") as? Map<String, SavedState>
        retained?.getOrCreate(id, restored.orEmpty())
            ?: KompassOwnerStore(restored.orEmpty())
    }
    store.platformExtras = kompassPlatformCreationExtras()
    val isRecreating = rememberKompassHostRecreation()
    DisposableEffect(store, parentRegistry) {
        store.reconcile(entries)
        val registration = parentRegistry?.registerProvider("kompass:owners:$id") { store.save() }
        onDispose { registration?.unregister() }
    }
    DisposableEffect(store, retained) {
        onDispose {
            // A nested controller belongs to its parent entry, including while that entry
            // is covered. Clearing the parent owner also clears its retained children.
            if (parentOwner !is KompassEntryOwner && (retained == null || !isRecreating())) {
                if (retained == null) store.close() else retained.remove(id)
            }
        }
    }
    return store
}

/** Only Android Activity recreation retains a disposed composition's controller resources. */
@Composable
internal expect fun rememberKompassHostRecreation(): () -> Boolean

@Composable
internal expect fun kompassPlatformCreationExtras(): CreationExtras
