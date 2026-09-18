package com.tekmoon.kompass

import kotlin.test.*

/**
 * Covers the short forms that take a [Destination] and build the entry, and the public
 * [KompassEntry] constructor they share with [toKompassEntry].
 */
class DestinationOverloadsTest {

    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }
    private object C : Destination { override val id = "c" }

    private fun KompassEntry.payload() = listOf(destinationId, args, scopeId.value, metadata.toMap())

    @Test fun navigate_with_a_destination_pushes_what_the_builder_would_push() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)

            // Same payload as the two-call form. Only the occurrence ID differs, and that is
            // library-managed.
            assertEquals(B.toKompassEntry().payload(), nav.currentEntry.payload())
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_carries_args_scope_and_metadata() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(
                destination = B,
                args = "opaque-not-json",
                scopeId = NavigationScopeId("checkout"),
                metadata = mapOf("presentation" to "sheet"),
            )

            val pushed = nav.currentEntry
            assertEquals("b", pushed.destinationId)
            assertEquals("opaque-not-json", pushed.args)
            assertEquals("checkout", pushed.scopeId.value)
            assertEquals(mapOf("presentation" to "sheet"), pushed.metadata.toMap())
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_defaults_the_scope_to_the_destination() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)
            assertEquals(B.defaultScope(), nav.currentEntry.scopeId)
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_honours_the_stack_policies() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)
            nav.navigate(A, clearBackStack = true)
            assertEquals(listOf("a"), nav.backStack.map { it.destinationId })

            nav.navigate(B)
            nav.navigate(A, reuseIfExists = true)
            assertEquals(listOf("b", "a"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_honours_pop_up_to() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)
            nav.navigate(C)

            // Not inclusive, so "b" survives and the new entry lands on top of it.
            nav.navigate(A, popUpTo = "b")
            assertEquals(listOf("a", "b", "a"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_honours_an_inclusive_pop_up_to() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)
            nav.navigate(C)

            // Inclusive, so "b" goes too.
            nav.navigate(C, popUpTo = "b", popUpToInclusive = true)
            assertEquals(listOf("a", "c"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_with_a_destination_opens_a_result_request() {
        val nav = createKompassNavController(A)
        try {
            val key = ResultKey<Answer>("answer")
            nav.navigate(B, resultKey = key)

            // The request is recorded on the entry that stays below, the same as the entry form.
            assertEquals(ResultState.Pending, nav.backStack.first().peekResult(key))
        } finally {
            nav.close()
        }
    }

    @Test fun replace_stack_with_a_destination_leaves_one_entry() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B)
            nav.replaceStack(A)

            assertEquals(listOf("a"), nav.backStack.map { it.destinationId })
            assertEquals(A.toKompassEntry().payload(), nav.currentEntry.payload())
        } finally {
            nav.close()
        }
    }

    @Test fun replace_stack_with_a_destination_carries_args_scope_and_metadata() {
        val nav = createKompassNavController(A)
        try {
            nav.replaceStack(
                destination = B,
                args = "opaque",
                scopeId = NavigationScopeId("root"),
                metadata = mapOf("presentation" to "fullscreen"),
            )

            val only = nav.backStack.single()
            assertEquals(listOf("b", "opaque", "root", mapOf("presentation" to "fullscreen")), only.payload())
        } finally {
            nav.close()
        }
    }

    @Test fun the_public_constructor_builds_the_same_entry_as_the_builder() {
        val built = KompassEntry(
            destinationId = "b",
            args = "opaque",
            scopeId = NavigationScopeId("checkout"),
            metadata = mapOf("presentation" to "sheet"),
        )
        val fromBuilder = B.toKompassEntry(
            args = "opaque",
            scopeId = NavigationScopeId("checkout"),
            metadata = mapOf("presentation" to "sheet"),
        )

        assertEquals(fromBuilder.payload(), built.payload())

        // Identity is library-managed, so two entries built the same way are separate occurrences.
        assertNotEquals(fromBuilder.id, built.id)
        assertTrue(built.id.isNotBlank())
    }

    @Test fun the_public_constructor_starts_an_entry_with_no_result_state() {
        val built = KompassEntry(destinationId = "b", scopeId = B.defaultScope())

        assertNull(built.pendingResultKey)
        assertNull(built.peekResult(ResultKey<Answer>("answer")))
    }

    @Test fun the_public_constructor_does_not_hold_the_map_the_caller_passed() {
        val hints = mutableMapOf("presentation" to "sheet")
        val built = KompassEntry(destinationId = "b", scopeId = B.defaultScope(), metadata = hints)

        hints["presentation"] = "fullscreen"
        hints["added"] = "later"

        assertEquals(mapOf("presentation" to "sheet"), built.metadata.toMap())
    }

    private data class Answer(val value: String) : NavigationResult
}
