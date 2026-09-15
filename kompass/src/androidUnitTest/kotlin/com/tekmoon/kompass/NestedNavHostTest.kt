package com.tekmoon.kompass

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NestedNavHostTest {
    @Test fun nested_navhost_can_rebind_store_during_outer_pop_animation() {
        val store = KompassOwnerStore()
        val root = KompassEntry("root", scopeId = newScope())
        val video = KompassEntry("video", scopeId = newScope())
        store.reconcile(listOf(root, video))
        store.updateHostLifecycle(Lifecycle.State.RESUMED)
        val owner = store.owner(video)
        store.retain(video)
        val nested = NavHostController(RuntimeEnvironment.getApplication()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setLifecycleOwner(owner)
            setViewModelStore(owner.viewModelStore)
            graph = createGraph(startDestination = "list") { composable("list") {} }
        }
        val keys = owner.viewModelStore.keys().toSet()
        assertTrue(keys.isNotEmpty())
        store.reconcile(listOf(root))
        // This is the call AndroidX NavHost makes again when recomposed during exit.
        // An immediate clear here reproduces "ViewModelStore should be set before setGraph call".
        nested.setViewModelStore(owner.viewModelStore)
        assertEquals(keys, owner.viewModelStore.keys())
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)
        store.release(video)
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertTrue(owner.viewModelStore.keys().isEmpty())
        store.close()
    }
    class HandleViewModel(val handle: androidx.lifecycle.SavedStateHandle) : androidx.lifecycle.ViewModel()

    @Test fun default_android_factory_restores_saved_state_after_parcel_round_trip() {
        val entry = KompassEntry("a", scopeId = newScope())
        val store = KompassOwnerStore()
        store.reconcile(listOf(entry))
        val owner = store.owner(entry)
        val original = androidx.lifecycle.ViewModelProvider.create(owner)[HandleViewModel::class]
        original.handle["counter"] = 42
        val nextEntry = KompassEntry("b", scopeId = entry.scopeId)
        store.reconcile(listOf(entry, nextEntry))
        val shared = androidx.lifecycle.ViewModelProvider.create(store.owner(nextEntry))[HandleViewModel::class]
        assertSame(original, shared)
        store.reconcile(listOf(nextEntry))
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(42, shared.handle.get<Int>("counter"))
        val restored = store.save().mapValues { (_, saved) ->
            val parcel = android.os.Parcel.obtain()
            try {
                parcel.writeBundle(saved)
                parcel.setDataPosition(0)
                parcel.readBundle(javaClass.classLoader)!!
            } finally { parcel.recycle() }
        }
        store.close()
        val recreated = KompassOwnerStore(restored)
        recreated.reconcile(listOf(nextEntry))
        val next = androidx.lifecycle.ViewModelProvider.create(recreated.owner(nextEntry))[HandleViewModel::class]
        assertNotSame(original, next)
        assertEquals(42, next.handle.get<Int>("counter"))
        recreated.close()
    }

}
