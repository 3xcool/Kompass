package com.tekmoon.kompass

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState

/** Shared ViewModels and handles outlive any one entry using this scope. */
internal class KompassScopeOwner(
    restored: SavedState?,
    private val platformExtras: CreationExtras,
) : ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory = kompassViewModelFactory()
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(platformExtras).apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@KompassScopeOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@KompassScopeOwner)
        }

    init {
        controller.performAttach()
        enableSavedStateHandles()
        controller.performRestore(restored)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun save(): SavedState = savedState().also(controller::performSave)

    fun clear() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
