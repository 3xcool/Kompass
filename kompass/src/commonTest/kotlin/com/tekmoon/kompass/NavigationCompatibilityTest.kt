package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.*

class NavigationCompatibilityTest {
    private val reducer = NavigationHandler()
    private fun entry(route: String, scope: String = route, args: String? = null) =
        BackStackEntry(route, args, NavigationScopeId(scope))

    @Test fun reuse_moves_last_match_and_updates_payload_without_changing_identity() {
        val a = entry("a")
        val b = entry("b", args = "old")
        val c = entry("c")
        val incoming = entry("b", args = "new")
        val result = reducer.reduce(NavigationState(persistentListOf(a, b, c)),
            NavigationCommand.Navigate(incoming, reuseIfExists = true))
        assertEquals(listOf(a, c, incoming.copy(id = b.id)), result.backStack)
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
        assertEquals(listOf(a, c, incoming.copy(id = b1.id)), result.backStack)
    }

    @Test fun result_delivery_preserves_receiver_identity_and_previous_results() {
        val a = entry("a").copy(results = mapOf("previous" to Result("first")))
        val b = entry("b").copy(pendingResultKey = "answer")
        val result = reducer.reduce(NavigationState(persistentListOf(a, b)), NavigationCommand.Pop(Result("second")))
        assertEquals(a.copy(results = a.results + ("answer" to Result("second"))), result.backStack.single())
    }

    @Test fun serialization_restores_identity_args_and_polymorphic_results() {
        val json = Json {
            classDiscriminator = "_type"
            serializersModule = SerializersModule {
                polymorphic(NavigationResult::class) { subclass(Result::class) }
            }
        }
        val state = defaultNavigationState(entry("a", args = "opaque-not-json")
            .copy(results = mapOf("answer" to Result("yes"))))
        val serializer = NavigationState.serializer(BackStackEntry.serializer())
        val encoded = json.encodeToString(serializer, state)
        assertTrue(encoded.contains(state.backStack.single().id))
        assertEquals(state, json.decodeFromString(serializer, encoded))
    }

    @Test fun old_serialized_entries_without_identity_still_restore() {
        val restored = Json.decodeFromString(BackStackEntry.serializer(),
            """{"destinationId":"a","scopeId":"a"}""")
        assertEquals("a", restored.destinationId)
        assertTrue(restored.id.isNotBlank())
    }

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
}
