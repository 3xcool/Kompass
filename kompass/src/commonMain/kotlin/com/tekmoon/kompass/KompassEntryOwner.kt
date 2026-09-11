package com.tekmoon.kompass

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState

/** An entry's AndroidX resources. The host keeps this alive through the last exit composition. */
internal class KompassEntryOwner(
    restored: SavedState? = null,
    internal val scopeOwner: KompassScopeOwner,
) :
    ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    // Nested controllers can be off-screen when saving. Their latest handles must be saved
    // by the parent owner, not only by the last snapshot of the child's disposed composition.
    internal val childStores by lazy {
        KompassOwnerStores(savedStateRegistry.consumeRestoredStateForKey("kompass:children"))
    }
    override val viewModelStore get() = scopeOwner.viewModelStore
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val defaultViewModelProviderFactory get() = scopeOwner.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras get() = scopeOwner.defaultViewModelCreationExtras

    init {
        controller.performAttach()
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
    }
}

internal expect fun kompassViewModelFactory(): ViewModelProvider.Factory
