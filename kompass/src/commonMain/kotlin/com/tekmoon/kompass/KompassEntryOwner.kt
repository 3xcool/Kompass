package com.tekmoon.kompass

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState

/** An entry's AndroidX resources. The host keeps this alive through the last exit composition. */
internal class KompassEntryOwner(
    restored: SavedState? = null,
    private val platformExtras: CreationExtras = CreationExtras.Empty,
) :
    ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    // Nested controllers can be off-screen when saving. Their latest handles must be saved
    // by the parent owner, not only by the last snapshot of the child's disposed composition.
    internal val childStores by lazy {
        KompassOwnerStores(savedStateRegistry.consumeRestoredStateForKey("kompass:children"))
    }
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = kompassViewModelFactory()
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(platformExtras).apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@KompassEntryOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@KompassEntryOwner)
        }

    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restored)
        registry.currentState = Lifecycle.State.CREATED
        savedStateRegistry.registerSavedStateProvider("kompass:children") { childStores.save() }
    }

    fun moveTo(state: Lifecycle.State) {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = state
    }

    fun save(): SavedState = savedState().also(controller::performSave)

    fun clear() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        childStores.close()
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

internal expect fun kompassViewModelFactory(): ViewModelProvider.Factory
