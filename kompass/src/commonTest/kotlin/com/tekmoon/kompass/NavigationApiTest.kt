package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.*

class NavigationApiTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }
    @Serializable private data class Answer(val value: String) : NavigationResult
    @Serializable private data class Other(val value: Int) : NavigationResult
    private val serializers = SerializersModule { polymorphic(NavigationResult::class) { subclass(Answer::class) } }

    @Test fun replacing_the_stack_publishes_one_state_not_one_per_level() {
        val nav = createKompassNavController(A)
        val seen = mutableListOf<List<String>>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch {
            nav.stateFlow.collect { seen += it.backStack.map { entry -> entry.destinationId } }
        }
        try {
            seen.clear()
            nav.replaceStack(listOf(A.toKompassEntry(), B.toKompassEntry(), A.toKompassEntry()))

            // Building the same stack with ReplaceRoot + Navigate + Navigate would publish
            // [a], [a, b], [a, b, a] and animate three times. One command must publish once.
            assertEquals(listOf(listOf("a", "b", "a")), seen)
            assertEquals("a", nav.currentEntry.destinationId)
        } finally {
            collector.cancel()
            nav.close()
        }
    }

    @Test fun a_replaced_stack_keeps_every_level_a_separate_occurrence() {
        val nav = createKompassNavController(A)
        try {
            // The same entry object at three levels. Each level owns its own UI state, so the
            // repeats must not share an occurrence ID.
            val reused = B.toKompassEntry()
            nav.replaceStack(listOf(reused, reused, reused))

            assertEquals(3, nav.backStack.size)
            assertEquals(3, nav.backStack.map { it.id }.toSet().size)
            assertEquals(reused.id, nav.backStack.first().id)
        } finally {
            nav.close()
        }
    }

    @Test fun replacing_the_stack_with_no_entries_fails() {
        val nav = createKompassNavController(A)
        try {
            assertFailsWith<IllegalArgumentException> { nav.replaceStack(emptyList()) }
            assertEquals(listOf("a"), nav.backStack.map { it.destinationId })
        } finally {
            nav.close()
        }
    }

    @Test fun replacing_the_stack_with_one_entry_clears_the_rest() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B.toKompassEntry())
            nav.replaceStack(B.toKompassEntry())

            assertEquals(listOf("b"), nav.backStack.map { it.destinationId })
            assertEquals(NavDirection.Push, nav.direction)
        } finally {
            nav.close()
        }
    }

    @Test fun a_saved_stack_restores_through_one_command() {
        val source = createKompassNavController(A)
        val saved: List<KompassEntry>
        try {
            source.navigate(B.toKompassEntry(args = "opaque"))
            saved = source.backStack
        } finally {
            source.close()
        }

        val target = createKompassNavController(A)
        try {
            target.replaceStack(saved)

            assertEquals(saved.map { it.destinationId }, target.backStack.map { it.destinationId })
            assertEquals(saved.map { it.id }, target.backStack.map { it.id })
            assertEquals("opaque", target.currentEntry.args)
        } finally {
            target.close()
        }
    }

    @Test fun consume_is_typed_once_and_targets_the_occurrence() {
        val root = A.toKompassEntry(results = mapOf("answer" to Answer("root"), "keep" to Answer("keep")))
        val duplicate = A.toKompassEntry(results = mapOf("answer" to Answer("duplicate")))
        val nav = createKompassNavController(NavigationState(persistentListOf(root, duplicate)), serializers)
        try {
            assertNull(nav.consumeResult<Other>("answer", root.id))
            assertNull(nav.consumeResult<Answer>("missing", root.id))
            assertNull(nav.consumeResult<Answer>("answer", "gone"))
            assertEquals(Answer("root"), nav.consumeResult<Answer>("answer", root.id))
            assertNull(nav.consumeResult<Answer>("answer", root.id))
            assertEquals(Answer("duplicate"), nav.consumeResult<Answer>("answer"))
            assertEquals(Answer("keep"), nav.backStack.first().results["keep"])
            val restored = createKompassNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try { assertNull(restored.consumeResult<Answer>("answer", root.id)) } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun delivered_result_can_be_consumed_after_restore() {
        val nav = createKompassNavController(A, serializers)
        try {
            nav.navigate(B.toKompassEntry(pendingResultKey = "answer"))
            nav.pop(Answer("saved"))
            val restored = createKompassNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try {
                assertEquals(Answer("saved"), restored.consumeResult<Answer>("answer"))
                assertNull(restored.consumeResult<Answer>("answer"))
            } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun corrupt_missing_and_empty_saved_stacks_recover_and_report() {
        for (saved in listOf("{broken", "{}", "{\"backStack\":[]}")) {
            var reports = 0
            val nav = createKompassNavController(A, savedNavigationState = saved, onRestoreFailure = { reports++ })
            try {
                assertEquals("a", nav.currentEntry.destinationId)
                assertNotNull(nav.restorationFailure)
                assertEquals(1, reports)
                nav.navigate(B.toKompassEntry())
                assertEquals("b", nav.currentEntry.destinationId)
            } finally { nav.close() }
        }
    }

    @Test fun strict_restore_throws_and_invalid_initial_state_is_never_a_fallback() {
        assertFails { createKompassNavController(A, savedNavigationState = "bad", restorePolicy = NavigationRestorePolicy.Throw) }
        assertFailsWith<IllegalArgumentException> { createKompassNavController(NavigationState(persistentListOf())) }
        assertFails { Json.decodeFromString(NavigationState.serializer(KompassEntry.serializer()), "{}") }
    }

    @Test fun duplicate_saved_identity_and_unregistered_results_are_reported() {
        val entry = A.toKompassEntry()
        val duplicate = Json.encodeToString(KompassEntry.serializer(), entry)
        val bad = "{\"backStack\":[$duplicate,$duplicate]}"
        val fallback = createKompassNavController(A, savedNavigationState = bad)
        try { assertNotNull(fallback.restorationFailure); assertEquals(1, fallback.backStack.size) } finally { fallback.close() }
        val source = createKompassNavController(defaultNavigationState(A.toKompassEntry(results = mapOf("a" to Answer("x")))), serializers)
        try {
            val restored = createKompassNavController(A, savedNavigationState = source.saveNavigationState())
            try { assertNotNull(restored.restorationFailure) } finally { restored.close() }
        } finally { source.close() }
    }

    @Test fun direction_comes_from_commands_and_result_consumption_does_not_change_it() {
        val nav = createKompassNavController(A)
        try {
            nav.navigate(B.toKompassEntry(pendingResultKey = "answer"))
            nav.pop(Answer("x"))
            assertEquals(NavDirection.Pop, nav.direction)
            nav.consumeResult<Answer>("answer")
            assertEquals(NavDirection.Pop, nav.direction)
            nav.pop()
            assertEquals(NavDirection.Pop, nav.direction)
            nav.replaceStack(B.toKompassEntry())
            assertEquals(NavDirection.Push, nav.direction)
            nav.navigate(A.toKompassEntry())
            nav.navigate(B.toKompassEntry(), reuseIfExists = true)
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals(NavDirection.Push, nav.direction)
            nav.navigate(A.toKompassEntry(), clearBackStack = true)
            assertEquals(NavDirection.Push, nav.direction)
        } finally { nav.close() }
    }

    @Test fun external_flow_observes_commands_without_composition_and_close_rejects_mutation() {
        val nav = createKompassNavController(A)
        val seen = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { nav.stateFlow.collect { seen += it.backStack.last().destinationId } }
        try {
            nav.navigate(B.toKompassEntry())
            nav.pop()
            assertEquals(listOf("a", "b", "a"), seen)
            assertEquals(nav.state, nav.stateFlow.value)
            nav.close()
            nav.close()
            assertFailsWith<IllegalStateException> { nav.navigate(B.toKompassEntry()) }
        } finally { collector.cancel(); nav.close() }
    }

    // Keeps the deprecated ReplaceRoot command covered until it is removed.
    @Suppress("DEPRECATION")
    @Test fun external_controller_keeps_deep_links_typed_arguments_and_command_order() {
        val handler = object : DeepLinkHandler {
            override fun matches(uri: String) = uri == "app://b"
            override fun resolve(uri: String) = listOf(NavigationCommand.ReplaceRoot(A.toKompassEntry()), NavigationCommand.Navigate(B.toKompassEntry(args = "opaque")))
        }
        val nav = createKompassNavController(A, deepLinkHandlers = persistentListOf(handler))
        try {
            assertFalse(nav.applyDeepLink("app://unknown"))
            assertTrue(nav.applyDeepLink("app://b"))
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals("opaque", nav.currentEntry.args)
            assertEquals(NavDirection.Push, nav.direction)
        } finally { nav.close() }
    }
}
