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

    @Test fun consume_is_typed_once_and_targets_the_occurrence() {
        val root = A.toBackStackEntry(results = mapOf("answer" to Answer("root"), "keep" to Answer("keep")))
        val duplicate = A.toBackStackEntry(results = mapOf("answer" to Answer("duplicate")))
        val nav = createNavController(NavigationState(persistentListOf(root, duplicate)), serializers)
        try {
            assertNull(nav.consumeResult<Other>("answer", root.id))
            assertNull(nav.consumeResult<Answer>("missing", root.id))
            assertNull(nav.consumeResult<Answer>("answer", "gone"))
            assertEquals(Answer("root"), nav.consumeResult<Answer>("answer", root.id))
            assertNull(nav.consumeResult<Answer>("answer", root.id))
            assertEquals(Answer("duplicate"), nav.consumeResult<Answer>("answer"))
            assertEquals(Answer("keep"), nav.backStack.first().results["keep"])
            val restored = createNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try { assertNull(restored.consumeResult<Answer>("answer", root.id)) } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun delivered_result_can_be_consumed_after_restore() {
        val nav = createNavController(A, serializers)
        try {
            nav.navigate(B.toBackStackEntry(pendingResultKey = "answer"))
            nav.pop(Answer("saved"))
            val restored = createNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try {
                assertEquals(Answer("saved"), restored.consumeResult<Answer>("answer"))
                assertNull(restored.consumeResult<Answer>("answer"))
            } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun corrupt_missing_and_empty_saved_stacks_recover_and_report() {
        for (saved in listOf("{broken", "{}", "{\"backStack\":[]}")) {
            var reports = 0
            val nav = createNavController(A, savedNavigationState = saved, onRestoreFailure = { reports++ })
            try {
                assertEquals("a", nav.currentEntry.destinationId)
                assertNotNull(nav.restorationFailure)
                assertEquals(1, reports)
                nav.navigate(B.toBackStackEntry())
                assertEquals("b", nav.currentEntry.destinationId)
            } finally { nav.close() }
        }
    }

    @Test fun strict_restore_throws_and_invalid_initial_state_is_never_a_fallback() {
        assertFails { createNavController(A, savedNavigationState = "bad", restorePolicy = NavigationRestorePolicy.Throw) }
        assertFailsWith<IllegalArgumentException> { createNavController(NavigationState(persistentListOf())) }
        assertFails { Json.decodeFromString(NavigationState.serializer(BackStackEntry.serializer()), "{}") }
    }

    @Test fun duplicate_saved_identity_and_unregistered_results_are_reported() {
        val entry = A.toBackStackEntry()
        val duplicate = Json.encodeToString(BackStackEntry.serializer(), entry)
        val bad = "{\"backStack\":[$duplicate,$duplicate]}"
        val fallback = createNavController(A, savedNavigationState = bad)
        try { assertNotNull(fallback.restorationFailure); assertEquals(1, fallback.backStack.size) } finally { fallback.close() }
        val source = createNavController(defaultNavigationState(A.toBackStackEntry(results = mapOf("a" to Answer("x")))), serializers)
        try {
            val restored = createNavController(A, savedNavigationState = source.saveNavigationState())
            try { assertNotNull(restored.restorationFailure) } finally { restored.close() }
        } finally { source.close() }
    }

    @Test fun direction_comes_from_commands_and_result_consumption_does_not_change_it() {
        val nav = createNavController(A)
        try {
            nav.navigate(B.toBackStackEntry(pendingResultKey = "answer"))
            nav.pop(Answer("x"))
            assertEquals(NavDirection.Pop, nav.direction)
            nav.consumeResult<Answer>("answer")
            assertEquals(NavDirection.Pop, nav.direction)
            nav.pop()
            assertEquals(NavDirection.Pop, nav.direction)
            nav.replaceRoot(B.toBackStackEntry())
            assertEquals(NavDirection.Push, nav.direction)
            nav.navigate(A.toBackStackEntry())
            nav.navigate(B.toBackStackEntry(), reuseIfExists = true)
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals(NavDirection.Push, nav.direction)
            nav.navigate(A.toBackStackEntry(), clearBackStack = true)
            assertEquals(NavDirection.Push, nav.direction)
        } finally { nav.close() }
    }

    @Test fun external_flow_observes_commands_without_composition_and_close_rejects_mutation() {
        val nav = createNavController(A)
        val seen = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { nav.stateFlow.collect { seen += it.backStack.last().destinationId } }
        try {
            nav.navigate(B.toBackStackEntry())
            nav.pop()
            assertEquals(listOf("a", "b", "a"), seen)
            assertEquals(nav.state, nav.stateFlow.value)
            nav.close()
            nav.close()
            assertFailsWith<IllegalStateException> { nav.navigate(B.toBackStackEntry()) }
        } finally { collector.cancel(); nav.close() }
    }

    @Test fun external_controller_keeps_deep_links_typed_arguments_and_command_order() {
        val handler = object : DeepLinkHandler {
            override fun matches(uri: String) = uri == "app://b"
            override fun resolve(uri: String) = listOf(NavigationCommand.ReplaceRoot(A.toBackStackEntry()), NavigationCommand.Navigate(B.toBackStackEntry(args = "opaque")))
        }
        val nav = createNavController(A, deepLinkHandlers = persistentListOf(handler))
        try {
            assertFalse(nav.applyDeepLink("app://unknown"))
            assertTrue(nav.applyDeepLink("app://b"))
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals("opaque", nav.currentEntry.args)
            assertEquals(NavDirection.Push, nav.direction)
        } finally { nav.close() }
    }
}
