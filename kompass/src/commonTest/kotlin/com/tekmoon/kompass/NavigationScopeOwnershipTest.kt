package com.tekmoon.kompass

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Scopes live in a process-wide store, so two controllers can name the same one. Only the last
 * holder may clear it.
 */
class NavigationScopeOwnershipTest {
    private object Home : Destination { override val id = "home" }
    private object Detail : Destination { override val id = "detail" }

    @AfterTest fun cleanup() = NavigationScopes.clearAll()

    private fun NavigationScopeId.instance(onCleared: () -> Unit = {}): Any =
        NavigationScopes.getScope(this).getOrCreate("probe", { Any() }, { onCleared() })

    @Test fun closing_one_controller_keeps_the_scope_another_one_holds() {
        var cleared = 0
        val first = createKompassNavController(Home)
        val second = createKompassNavController(Home)
        assertEquals(first.currentEntry.scopeId, second.currentEntry.scopeId, "both root at home")
        val instance = Home.defaultScope().instance { cleared++ }

        first.close()

        assertEquals(0, cleared, "the second controller still shows this scope")
        assertSame(instance, Home.defaultScope().instance(), "the same object must survive")
    }

    @Test fun the_last_holder_clears_the_scope() {
        var cleared = 0
        val first = createKompassNavController(Home)
        val second = createKompassNavController(Home)
        val instance = Home.defaultScope().instance { cleared++ }

        first.close()
        second.close()

        assertEquals(1, cleared)
        assertNotSame(instance, Home.defaultScope().instance(), "a fresh scope, not the old one")
    }

    @Test fun closing_twice_clears_once() {
        var cleared = 0
        val nav = createKompassNavController(Home)
        Home.defaultScope().instance { cleared++ }

        nav.close()
        nav.close()

        assertEquals(1, cleared)
    }

    @Test fun a_scope_that_leaves_the_back_stack_is_still_cleared() {
        var cleared = 0
        val nav = createKompassNavController(Home)
        nav.navigate(Detail.toKompassEntry())
        Detail.defaultScope().instance { cleared++ }

        nav.pop()

        assertEquals(1, cleared, "no other controller holds the detail scope")
    }

    @Test fun a_scope_two_controllers_navigate_to_survives_one_pop() {
        var cleared = 0
        val first = createKompassNavController(Home)
        val second = createKompassNavController(Home)
        first.navigate(Detail.toKompassEntry())
        second.navigate(Detail.toKompassEntry())
        Detail.defaultScope().instance { cleared++ }

        first.pop()

        assertEquals(0, cleared, "the second controller still stands on detail")

        second.pop()

        assertEquals(1, cleared)
    }

    @Test fun clear_scope_remains_an_explicit_override() {
        var cleared = 0
        createKompassNavController(Home)
        Home.defaultScope().instance { cleared++ }

        // An owner of a manual scope must still be able to clear it on demand.
        NavigationScopes.clearScope(Home.defaultScope())

        assertEquals(1, cleared)
    }
}
