package com.tekmoon.kompass

import kotlin.test.*

class NavigationScopeRetentionTest {
    @AfterTest fun cleanup() { NavigationScopes.clearAll() }

    @Test fun automatic_cleanup_waits_for_all_renderers() {
        val id = newScope()
        var clears = 0
        val scope = NavigationScopes.getScope(id)
        val instance = scope.getOrCreate("probe", { Any() }, { clears++ })
        NavigationScopes.retain(id)
        NavigationScopes.retain(id)
        NavigationScopes.requestClear(id)
        assertSame(instance, NavigationScopes.getScope(id).getOrCreate("probe", { Any() }, {}))
        NavigationScopes.release(id)
        assertEquals(0, clears)
        NavigationScopes.release(id)
        assertEquals(1, clears)
    }

    @Test fun rendering_again_does_not_cancel_pending_cleanup() {
        val id = newScope()
        var clears = 0
        NavigationScopes.getScope(id).getOrCreate("probe", { Any() }, { clears++ })
        NavigationScopes.retain(id)
        NavigationScopes.requestClear(id)
        NavigationScopes.retain(id)
        NavigationScopes.release(id)
        NavigationScopes.release(id)
        assertEquals(1, clears)
    }

    @Test fun returning_scope_to_back_stack_cancels_pending_cleanup() {
        val id = newScope()
        var clears = 0
        NavigationScopes.getScope(id).getOrCreate("probe", { Any() }, { clears++ })
        NavigationScopes.retain(id)
        NavigationScopes.requestClear(id)
        NavigationScopes.cancelClear(id)
        NavigationScopes.release(id)
        assertEquals(0, clears)
        NavigationScopes.requestClear(id)
        assertEquals(1, clears)
    }

    @Test fun explicit_clear_and_ad_hoc_scope_api_remain_available() {
        val id = NavigationScopeId("flow:test")
        var clears = 0
        val scope = NavigationScopes.getScope(id)
        val probe = scope.getOrCreate("key", { Any() }, { clears++ })
        assertSame(probe, NavigationScopes.getScope(id).getOrCreate("key", { Any() }, {}))
        NavigationScopes.clearScope(id)
        NavigationScopes.clearScope(id)
        assertEquals(1, clears)
    }
}
