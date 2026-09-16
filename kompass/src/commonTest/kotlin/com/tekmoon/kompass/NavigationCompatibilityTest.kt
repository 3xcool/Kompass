package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.*

class NavigationCompatibilityTest {
    private val reducer = NavigationHandler()
    private fun entry(
        route: String,
        scope: String = route,
        args: String? = null,
        awaiting: String? = null,
        results: Map<String, NavigationResult> = emptyMap(),
    ) = KompassEntry(
        destinationId = route,
        args = args,
        scopeId = NavigationScopeId(scope),
        awaitingResultKey = awaiting,
        results = results.mapValues { StoredResult.Delivered(it.value) }.toPersistentMap(),
    )

    @Test fun reuse_moves_last_match_and_updates_payload_without_changing_identity() {
        val a = entry("a")
        val b = entry("b", args = "old")
        val c = entry("c")
        val incoming = entry("b", args = "new")
        val result = reducer.reduce(NavigationState(persistentListOf(a, b, c)),
            NavigationCommand.Navigate(incoming, reuseIfExists = true))
        assertEquals(listOf(a, c, incoming.withIdentityOf(b)), result.backStack)
    }

    @Test fun reuse_respects_an_explicit_different_scope() {
        val a = entry("a")
        val b = entry("b")
        val replacement = entry("b", scope = "another")
        val result = reducer.reduce(NavigationState(persistentListOf(a, b)),
            NavigationCommand.Navigate(replacement, reuseIfExists = true))
        assertEquals(listOf(a, replacement), result.backStack)
    }

    @Test fun repeat_push_is_distinct_even_with_a_shared_scope() {
        val first = entry("a")
        val second = entry("a")
        assertNotEquals(first.id, second.id)
        assertEquals(first.scopeId, second.scopeId)
        val result = reducer.reduce(defaultNavigationState(first), NavigationCommand.Navigate(second))
        assertEquals(listOf(first, second), result.backStack)
    }

    @Test fun reuse_matches_last_occurrence_after_pop_up_to() {
        val a = entry("a"); val b1 = entry("b"); val c = entry("c"); val b2 = entry("b")
        val state = NavigationState(persistentListOf(a, b1, c, b2))
        val incoming = entry("b", args = "updated")
        val result = reducer.reduce(state, NavigationCommand.Navigate(incoming, popUpTo = "c", reuseIfExists = true))
        assertEquals(listOf(a, c, incoming.withIdentityOf(b1)), result.backStack)
    }

    @Test fun result_delivery_preserves_receiver_identity_and_previous_results() {
        val a = entry("a", awaiting = "answer", results = mapOf("previous" to Result("first")))
        val b = entry("b")
        val result = reducer.reduce(
            NavigationState(persistentListOf(a, b)),
            NavigationCommand.Pop(result = Result("second"), resultKey = ResultKey<Result>("answer"))
        )
        val receiver = result.backStack.single()
        assertEquals(a.id, receiver.id)
        assertEquals(ResultState.Delivered(Result("first")), receiver.peekResult(ResultKey<Result>("previous")))
        assertEquals(ResultState.Delivered(Result("second")), receiver.peekResult(ResultKey<Result>("answer")))
        assertNull(receiver.awaitingResultKey)
    }

    @Test fun a_result_without_a_key_pops_nothing() {
        val a = entry("a", awaiting = "answer")
        val b = entry("b")
        val state = NavigationState(persistentListOf(a, b))
        val result = reducer.reduce(state, NavigationCommand.Pop(result = Result("second")))

        // The command asked to deliver and named no key. It is refused whole, so the entry that had
        // to answer stays on the stack and the request stays open.
        assertEquals(state.backStack, result.backStack)
        assertEquals(ResultState.Pending, result.backStack.first().peekResult(ResultKey<Result>("answer")))
    }

    @Test fun serialization_restores_identity_args_and_polymorphic_results() {
        val json = Json {
            classDiscriminator = "_type"
            serializersModule = SerializersModule {
                polymorphic(NavigationResult::class) { subclass(Result::class) }
            }
        }
        val state = defaultNavigationState(
            entry("a", args = "opaque-not-json", awaiting = "later", results = mapOf("answer" to Result("yes")))
        )
        val serializer = NavigationState.serializer(KompassEntry.serializer())
        val encoded = json.encodeToString(serializer, state)
        assertTrue(encoded.contains(state.backStack.single().id))
        assertEquals(state, json.decodeFromString(serializer, encoded))
    }

    @Test fun entries_saved_by_2_0_0_restore_unless_they_carry_a_result() {
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "_type"
            serializersModule = SerializersModule {
                polymorphic(NavigationResult::class) { subclass(Result::class) }
            }
        }
        // 2.0.0 stored a bare NavigationResult under each key. 2.1.0 wraps it in StoredResult, so an
        // entry that carries a result no longer decodes. A controller reports this and falls back
        // to its initial state; it never restores half a stack.
        val withResult = """
            {"destinationId":"a","scopeId":"a","pendingResultKey":"answer",
             "results":{"answer":{"_type":"com.tekmoon.kompass.NavigationCompatibilityTest.Result",
             "value":"kept"}}}
        """.trimIndent()
        assertFails { json.decodeFromString(KompassEntry.serializer(), withResult) }

        // An entry with no result still restores. The dropped pendingResultKey is an unknown key.
        val withoutResult = """{"destinationId":"a","scopeId":"a","pendingResultKey":"answer"}"""
        val restored = json.decodeFromString(KompassEntry.serializer(), withoutResult)
        assertEquals("a", restored.destinationId)
        assertNull(restored.awaitingResultKey)
    }

    @Test fun an_entry_never_holds_the_map_the_caller_passed() {
        val destination = object : Destination { override val id = "a" }
        val hints = mutableMapOf("presentation" to "sheet")
        val entry = destination.toKompassEntry(metadata = hints)

        hints["presentation"] = "fullscreen"
        hints["added"] = "later"

        // The builder converts at the boundary, so @Immutable is a promise the class keeps.
        assertEquals(mapOf("presentation" to "sheet"), entry.metadata.toMap())
    }

    @Test fun metadata_keeps_its_plain_object_wire_format() {
        val destination = object : Destination { override val id = "a" }
        val encoded = Json.encodeToString(
            KompassEntry.serializer(),
            destination.toKompassEntry(metadata = mapOf("presentation" to "sheet")),
        )
        assertTrue(encoded.contains("\"metadata\":{\"presentation\":\"sheet\"}"), encoded)

        // An entry written before metadata became immutable still decodes.
        val old = """{"destinationId":"a","scopeId":"a","metadata":{"presentation":"sheet"}}"""
        val restored = Json.decodeFromString(KompassEntry.serializer(), old)
        assertEquals("sheet", restored.metadata["presentation"])
    }

    @Test fun old_serialized_entries_without_identity_still_restore() {
        val restored = Json.decodeFromString(KompassEntry.serializer(),
            """{"destinationId":"a","scopeId":"a"}""")
        assertEquals("a", restored.destinationId)
        assertTrue(restored.id.isNotBlank())
    }

    // Keeps the deprecated ReplaceRoot command covered until it is removed.
    @Suppress("DEPRECATION")
    @Test fun deep_links_use_first_matching_handler_and_apply_commands_in_order() {
        val a = entry("a"); val b = entry("b")
        val handler = object : DeepLinkHandler {
            override fun matches(uri: String) = uri == "test://b"
            override fun resolve(uri: String) = listOf(NavigationCommand.ReplaceRoot(a), NavigationCommand.Navigate(b))
        }
        val unexpected = object : DeepLinkHandler {
            override fun matches(uri: String): Boolean = error("second handler must not run")
            override fun resolve(uri: String): List<NavigationCommand> = error("unreachable")
        }
        val commands = DeepLinkManager(listOf(handler, unexpected)).resolve("test://b")!!
        assertEquals(listOf(a, b), applyDeepLink(defaultNavigationState(entry("old")), commands, reducer).backStack)
    }

    @Serializable private data class Result(val value: String) : NavigationResult
    @Test fun copying_payload_preserves_library_identity_and_constructor_fields() {
        val original = KompassEntry("a", "old", newScope())
        val updated = original.copy(args = "new")
        assertEquals(original.id, updated.id)
        assertNotEquals(original.id, original.copy(scopeId = newScope()).id)
        assertNotEquals(original.id, original.copy(destinationId = "b").id)
        val (route, args, scope) = updated
        assertEquals("a", route)
        assertEquals("new", args)
        assertEquals(original.scopeId, scope)
        assertTrue(updated.metadata.isEmpty())
        assertEquals(updated, Json.decodeFromString<KompassEntry>(Json.encodeToString(updated)))
    }

}
