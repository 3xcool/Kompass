package com.tekmoon.kompass

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The typed layer is what answers "navigation is not type safe here". It is a lens applied at the
 * edge: the transport stays an opaque string, and the type is put back only when a screen reads it.
 * These tests hold that contract.
 */
class TypedDestinationTest {

    @Serializable
    private data class ProfileArgs(val userId: String, val tab: Int = 0)

    @Serializable
    private data class OrderArgs(val orderId: Long)

    private object Profile : TypedDestination<ProfileArgs> {
        override val id = "profile"
        override val argsSerializer = ProfileArgs.serializer()
    }

    private object Order : TypedDestination<OrderArgs> {
        override val id = "order"
        override val argsSerializer = OrderArgs.serializer()
    }

    private object SameShape : TypedDestination<ProfileArgs> {
        override val id = "same-shape"
        override val argsSerializer = ProfileArgs.serializer()
    }

    private object Home : Destination { override val id = "home" }

    private val json = Json

    // ---------------------------------------------------------------- destination side

    @Test fun args_survive_the_round_trip_through_the_opaque_string() {
        val args = ProfileArgs(userId = "42", tab = 3)
        val entry = Profile.toKompassEntry(args, json)

        // The wire format stays a plain string. Nothing about the type leaks into the entry.
        assertEquals("profile", entry.destinationId)
        assertTrue(entry.args!!.contains("\"userId\""))
        assertEquals(args, Profile.argsFrom(entry, json))
        assertEquals(args, Profile.argsOrNull(entry, json))
    }

    @Test fun encode_args_produces_exactly_what_the_entry_carries() {
        val args = OrderArgs(orderId = 7L)
        assertEquals(Order.encodeArgs(args, json), Order.toKompassEntry(args, json).args)
    }

    @Test fun an_entry_with_no_args_returns_null_or_fails_naming_the_destination() {
        // Both helpers read entry.args and nothing else. The destinationId is here only so the entry
        // looks like a real one: a deep link that forgot to attach the arguments.
        val noArgs = KompassEntry(destinationId = Profile.id, scopeId = Profile.defaultScope())

        // Optional arguments: null is an answer.
        assertNull(Profile.argsOrNull(noArgs, json))

        // Required arguments: failing is the answer, and the message has to name the destination.
        // The quoted form appears only in that message, never in the entry.
        val failure = assertFailsWith<IllegalStateException> { Profile.argsFrom(noArgs, json) }
        assertTrue(
            failure.message!!.contains("'${Profile.id}'"),
            "the message must name the destination, or it is useless in a crash report",
        )
    }

    @Test fun reading_args_with_the_wrong_destination_fails_even_when_the_payload_shape_matches() {
        val entry = Profile.toKompassEntry(ProfileArgs("42"), json)
        // A serializer alone cannot detect this: SameShape accepts the exact same payload shape.
        assertFailsWith<IllegalArgumentException> { SameShape.argsOrNull(entry, json) }
        assertFailsWith<IllegalArgumentException> { SameShape.argsFrom(entry, json) }
    }

    @Test fun a_typed_entry_takes_the_default_scope_of_its_destination() {
        val entry = Profile.toKompassEntry(ProfileArgs("42"), json)
        assertEquals(Profile.defaultScope(), entry.scopeId)

        val custom = NavigationScopeId("checkout")
        assertEquals(custom, Profile.toKompassEntry(ProfileArgs("42"), json, scopeId = custom).scopeId)
    }

    @Test fun a_typed_entry_carries_presentation_metadata() {
        val entry = Profile.toKompassEntry(ProfileArgs("42"), json, metadata = mapOf("presentation" to "sheet"))
        assertEquals("sheet", entry.metadata["presentation"])
    }

    // ---------------------------------------------------------------- controller side

    @Test fun navigate_to_pushes_a_typed_entry_that_require_args_reads_back() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Profile, ProfileArgs(userId = "7", tab = 2))

            assertEquals("profile", nav.currentEntry.destinationId)
            assertEquals(ProfileArgs("7", 2), nav.requireArgs(Profile))
            assertEquals(ProfileArgs("7", 2), nav.argsOrNull(Profile))
        } finally {
            nav.close()
        }
    }

    @Test fun require_args_on_an_untyped_entry_fails_and_args_or_null_does_not() {
        val nav = createKompassNavController(Home)
        try {
            assertNull(nav.argsOrNull(Profile))
            assertFailsWith<IllegalStateException> { nav.requireArgs(Profile) }
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_to_honours_reuse_and_updates_the_payload_without_a_new_occurrence() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Profile, ProfileArgs("7"))
            val firstId = nav.currentEntry.id
            nav.navigateTo(Order, OrderArgs(1L))
            nav.navigateTo(Profile, ProfileArgs("9"), reuseIfExists = true)

            assertEquals(listOf("home", "order", "profile"), nav.backStack.map { it.destinationId })
            assertEquals(firstId, nav.currentEntry.id, "reuse must retain the occurrence")
            assertEquals(ProfileArgs("9"), nav.requireArgs(Profile), "the new payload must win")
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_to_forwards_pop_up_to_and_clear_back_stack() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Order, OrderArgs(1L))
            nav.navigateTo(Profile, ProfileArgs("7"), popUpTo = "home", popUpToInclusive = false)
            assertEquals(listOf("home", "profile"), nav.backStack.map { it.destinationId })

            nav.navigateTo(Order, OrderArgs(2L), clearBackStack = true)
            assertEquals(listOf("order"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun navigate_to_accepts_the_new_positional_parameter_order() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Order, OrderArgs(1L), Order.defaultScope(), true)

            assertEquals(listOf("order"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun replace_stack_to_leaves_a_single_typed_entry() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Order, OrderArgs(1L))
            nav.replaceStack(Profile, ProfileArgs("7"), metadata = mapOf("presentation" to "pane"))

            assertEquals(listOf("profile"), nav.backStack.map { it.destinationId })
            assertEquals(ProfileArgs("7"), nav.requireArgs(Profile))
            assertEquals("pane", nav.currentEntry.metadata["presentation"])
        } finally {
            nav.close()
        }
    }

    @Test fun the_typed_replace_stack_leaves_one_entry_with_its_arguments() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Order, OrderArgs(1L))
            nav.replaceStack(Profile, ProfileArgs("7"))

            assertEquals(listOf("profile"), nav.backStack.map { it.destinationId })
            assertEquals(ProfileArgs("7"), nav.requireArgs(Profile))
        } finally {
            nav.close()
        }
    }

    @Test fun the_controller_helpers_build_the_same_entry_as_the_destination_helpers() {
        val nav = createKompassNavController(Home)
        try {
            val args = ProfileArgs("7", 1)
            val built = nav.toKompassEntry(Profile, args)

            assertEquals(nav.encodeArgs(Profile, args), built.args)
            assertEquals(Profile.encodeArgs(args, json), built.args)
            assertEquals(args, nav.requireArgs(Profile, built))
        } finally {
            nav.close()
        }
    }

    @Test fun typed_args_survive_a_save_and_a_restore() {
        val source = createKompassNavController(Home)
        val saved: String
        try {
            source.navigateTo(Profile, ProfileArgs("7", 2))
            saved = source.saveNavigationState()
        } finally {
            source.close()
        }

        val restored = createKompassNavController(Home, savedNavigationState = saved)
        try {
            assertNull(restored.restorationFailure)
            assertEquals(ProfileArgs("7", 2), restored.requireArgs(Profile))
        } finally {
            restored.close()
        }
    }

    @Test fun two_typed_entries_of_the_same_destination_are_separate_occurrences() {
        val nav = createKompassNavController(Home)
        try {
            nav.navigateTo(Profile, ProfileArgs("7"))
            nav.navigateTo(Profile, ProfileArgs("9"))

            val ids = nav.backStack.filter { it.destinationId == "profile" }.map { it.id }
            assertEquals(2, ids.toSet().size)
            assertNotEquals(nav.backStack[1].args, nav.backStack[2].args)
        } finally {
            nav.close()
        }
    }
}
