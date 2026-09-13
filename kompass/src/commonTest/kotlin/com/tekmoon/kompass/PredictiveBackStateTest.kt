package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.*

class PredictiveBackStateTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    @Test fun a_new_state_reports_no_gesture() {
        val state = PredictiveBackState()
        assertNull(state.progress)
        assertNull(state.targetEntryId)
        assertFalse(state.isActive)
    }

    @Test fun a_gesture_starts_at_zero_and_clamps_every_update() {
        val state = PredictiveBackState()
        state.start("entry-1")
        assertTrue(state.isActive)
        assertEquals("entry-1", state.targetEntryId)
        assertEquals(0f, state.progress)

        state.update(0.4f)
        assertEquals(0.4f, state.progress)
        state.update(-3f)
        assertEquals(0f, state.progress)
        state.update(9f)
        assertEquals(1f, state.progress)
    }

    @Test fun progress_without_a_gesture_is_ignored() {
        val state = PredictiveBackState()
        state.update(0.5f)
        assertNull(state.progress)
        assertFalse(state.isActive)
    }

    @Test fun finishing_clears_both_the_target_and_the_progress() {
        val state = PredictiveBackState()
        state.start("entry-1")
        state.update(0.8f)
        state.finish()
        assertNull(state.progress)
        assertNull(state.targetEntryId)
        assertFalse(state.isActive)
    }

    @Test fun a_gesture_never_reaches_the_reducer_or_the_saved_state() {
        val nav = createNavController(A)
        nav.navigate(B.toBackStackEntry())
        val before = nav.saveNavigationState()

        nav.predictiveBack.start(nav.backStack.first().id)
        nav.predictiveBack.update(0.7f)

        assertEquals(2, nav.backStack.size)
        assertEquals("b", nav.currentEntry.destinationId)
        assertEquals(before, nav.saveNavigationState())
        nav.close()
    }

    @Test fun a_controller_owns_exactly_one_gesture_state() {
        val nav = createNavController(NavigationState(persistentListOf(A.toBackStackEntry())))
        assertSame(nav.predictiveBack, nav.predictiveBack)
        assertNotSame(nav.predictiveBack, createNavController(A).predictiveBack)
        nav.close()
    }
}
