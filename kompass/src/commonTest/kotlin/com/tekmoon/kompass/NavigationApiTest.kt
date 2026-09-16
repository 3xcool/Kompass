package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap
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
    private val answerKey = ResultKey<Answer>("answer")
    private val otherKey = ResultKey<Other>("answer")
    private val serializers = SerializersModule { polymorphic(NavigationResult::class) { subclass(Answer::class) } }

    /** Builds an entry with result state already set. Only the reducer may do this in production. */
    private fun Destination.entryWith(
        delivered: Map<String, NavigationResult> = emptyMap(),
        cancelled: Set<String> = emptySet(),
        pending: String? = null,
    ) = KompassEntry(
        destinationId = id,
        scopeId = defaultScope(),
        pendingResultKey = pending,
        results = (delivered.mapValues { StoredResult.Delivered(it.value) } +
            cancelled.associateWith { StoredResult.Cancelled }).toPersistentMap(),
    )

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
        val keepKey = ResultKey<Answer>("keep")
        val root = A.entryWith(mapOf("answer" to Answer("root"), "keep" to Answer("keep")))
        val duplicate = A.entryWith(mapOf("answer" to Answer("duplicate")))
        val nav = createKompassNavController(NavigationState(persistentListOf(root, duplicate)), serializers)
        try {
            assertNull(nav.consumeResult(otherKey, root.id))
            assertNull(nav.consumeResult(ResultKey<Answer>("missing"), root.id))
            assertNull(nav.consumeResult(answerKey, "gone"))
            assertEquals(ResultState.Delivered(Answer("root")), nav.consumeResult(answerKey, root.id))
            assertNull(nav.consumeResult(answerKey, root.id))
            assertEquals(ResultState.Delivered(Answer("duplicate")), nav.consumeResult(answerKey))
            assertEquals(ResultState.Delivered(Answer("keep")), nav.backStack.first().peekResult(keepKey))
            val restored = createKompassNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try { assertNull(restored.consumeResult(answerKey, root.id)) } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun delivered_result_can_be_consumed_after_restore() {
        val nav = createKompassNavController(A, serializers)
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.pop(result = Answer("saved"), resultKey = answerKey)
            val restored = createKompassNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try {
                assertEquals(ResultState.Delivered(Answer("saved")), restored.consumeResult(answerKey))
                assertNull(restored.consumeResult(answerKey))
            } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun a_request_is_pending_until_it_is_answered() {
        val nav = createKompassNavController(A, serializers)
        try {
            assertNull(nav.currentEntry.peekResult(answerKey))
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            assertEquals(ResultState.Pending, nav.backStack.first().peekResult(answerKey))
            nav.pop(result = Answer("done"), resultKey = answerKey)
            assertEquals(ResultState.Delivered(Answer("done")), nav.currentEntry.peekResult(answerKey))
            assertNull(nav.currentEntry.pendingResultKey)
        } finally { nav.close() }
    }

    @Test fun back_and_a_plain_pop_cancel_an_open_request() {
        for (goBack in listOf<(KompassNavController) -> Unit>({ it.pop() }, { it.popIfCan() })) {
            val nav = createKompassNavController(A, serializers)
            try {
                nav.navigateForResult(B.toKompassEntry(), answerKey)
                goBack(nav)
                assertEquals(ResultState.Cancelled, nav.currentEntry.peekResult(answerKey))
                assertNull(nav.currentEntry.pendingResultKey)
                assertEquals(ResultState.Cancelled, nav.consumeResult(answerKey))
                assertNull(nav.currentEntry.peekResult(answerKey))
            } finally { nav.close() }
        }
    }

    @Test fun a_multi_entry_pop_cancels_the_request_of_the_surviving_entry() {
        val byCount = createKompassNavController(A, serializers)
        try {
            byCount.navigateForResult(B.toKompassEntry(), answerKey)   // [a(waiting), b]
            byCount.navigate(A.toKompassEntry())                       // [a(waiting), b, a]
            byCount.pop(count = 2)                                     // survivor: the waiting a
            assertEquals(ResultState.Cancelled, byCount.currentEntry.peekResult(answerKey))
        } finally { byCount.close() }

        val byDestination = createKompassNavController(A, serializers)
        try {
            byDestination.navigate(B.toKompassEntry())                          // [a, b]
            byDestination.navigateForResult(A.toKompassEntry(), answerKey)      // [a, b(waiting), a]
            byDestination.pop(popUntil = "b")                                   // survivor: the waiting b
            assertEquals("b", byDestination.currentEntry.destinationId)
            assertEquals(ResultState.Cancelled, byDestination.currentEntry.peekResult(answerKey))
        } finally { byDestination.close() }
    }

    @Test fun replacing_the_stack_never_cancels_and_can_drop_an_open_request() {
        val nav = createKompassNavController(A, serializers)
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.replaceStack(A.toKompassEntry())
            // The entry that was waiting is gone, so there is nobody to tell. This is documented
            // behaviour: replaceStack applies a whole stack and does not close open requests.
            assertNull(nav.currentEntry.peekResult(answerKey))
        } finally { nav.close() }
    }

    @Test fun a_rejected_delivery_is_reported_and_changes_nothing_at_all() {
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            // A never asked for a result. The pop must not happen either: the caller asked to
            // deliver, and it cannot, so the whole command is refused.
            nav.navigate(B.toKompassEntry())
            val before = nav.backStack
            nav.pop(result = Answer("ignored"), resultKey = answerKey)
            assertEquals(1, reports.size)
            assertEquals(before, nav.backStack)
            assertNull(nav.currentEntry.peekResult(answerKey))
        } finally { nav.close() }
    }

    @Test fun the_second_pop_of_a_repeated_tap_removes_no_extra_screen() {
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            nav.navigate(B.toKompassEntry())                          // [a, b]
            nav.navigateForResult(A.toKompassEntry(), answerKey)      // [a, b(waiting), a]

            nav.pop(result = Answer("first"), resultKey = answerKey)  // [a, b] delivered
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })

            // The user tapped twice. The second pop must not take "b" away with it.
            nav.pop(result = Answer("second"), resultKey = answerKey)
            assertEquals(1, reports.size)
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals(ResultState.Delivered(Answer("first")), nav.currentEntry.peekResult(answerKey))
        } finally { nav.close() }
    }

    @Test fun a_pop_that_removes_nothing_leaves_an_open_request_open() {
        // A stack restored or rebuilt with replaceStack can put a waiting entry on top. popUntil
        // naming that entry pops nothing, so it must not close the request either.
        val waiting = A.entryWith(pending = "answer")
        val nav = createKompassNavController(
            NavigationState(persistentListOf(B.toKompassEntry(), waiting)), serializers,
        )
        try {
            val before = nav.backStack
            nav.pop(popUntil = "a")

            assertEquals(before, nav.backStack)
            assertEquals(ResultState.Pending, nav.currentEntry.peekResult(answerKey))
            assertEquals("answer", nav.currentEntry.pendingResultKey)
        } finally { nav.close() }
    }

    @Test fun a_delivery_under_another_key_is_rejected() {
        val expectedKey = ResultKey<Answer>("expected")
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            nav.navigateForResult(B.toKompassEntry(), expectedKey)
            nav.pop(result = Answer("wrong key"), resultKey = answerKey)

            // Nothing happens: the destination stays on the stack and the request stays open. The
            // caller asked to deliver and could not, so the pop is refused with it.
            assertEquals(1, reports.size)
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals(ResultState.Pending, nav.backStack.first().peekResult(expectedKey))
        } finally { nav.close() }
    }

    @Test fun a_repeated_request_over_an_unconsumed_answer_navigates_and_reports() {
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.pop(result = Answer("stale"), resultKey = answerKey)
            assertEquals(ResultState.Delivered(Answer("stale")), nav.currentEntry.peekResult(answerKey))
            assertEquals(0, reports.size)

            // The screen never consumed the first answer. Opening the destination is what the user
            // asked for, so it happens; the stale answer is dropped and the drop is reported.
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
            assertEquals(ResultState.Pending, nav.backStack.first().peekResult(answerKey))
            assertEquals(1, reports.size)
            assertTrue(reports.single().contains("never consumed"), reports.single())
        } finally { nav.close() }
    }

    @Test fun a_repeated_request_over_an_unconsumed_cancellation_is_reported_too() {
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.pop()
            assertEquals(ResultState.Cancelled, nav.currentEntry.peekResult(answerKey))

            nav.navigateForResult(B.toKompassEntry(), answerKey)
            assertEquals(1, reports.size)
            assertEquals(ResultState.Pending, nav.backStack.first().peekResult(answerKey))
        } finally { nav.close() }
    }

    @Test fun a_request_after_a_clean_consume_reports_nothing() {
        val reports = mutableListOf<String>()
        val nav = createKompassNavController(A, serializers, onNavigationError = { reports += it.message.orEmpty() })
        try {
            repeat(2) {
                nav.navigateForResult(B.toKompassEntry(), answerKey)
                nav.pop(result = Answer("answer $it"), resultKey = answerKey)
                assertEquals(ResultState.Delivered(Answer("answer $it")), nav.consumeResult(answerKey))
            }
            assertEquals(0, reports.size)
        } finally { nav.close() }
    }

    @Test fun pending_is_derived_and_never_enters_the_saved_state() {
        val nav = createKompassNavController(A, serializers)
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            val saved = nav.saveNavigationState()
            assertTrue(saved.contains("\"pendingResultKey\":\"answer\""), saved)
            assertFalse(saved.contains("Pending"), saved)

            val restored = createKompassNavController(A, serializers, savedNavigationState = saved)
            try {
                assertEquals(ResultState.Pending, restored.backStack.first().peekResult(answerKey))
            } finally { restored.close() }
        } finally { nav.close() }
    }

    @Test fun a_cancelled_request_survives_restoration() {
        val nav = createKompassNavController(A, serializers)
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.pop()
            val restored = createKompassNavController(A, serializers, savedNavigationState = nav.saveNavigationState())
            try {
                assertEquals(ResultState.Cancelled, restored.currentEntry.peekResult(answerKey))
                assertEquals(ResultState.Cancelled, restored.consumeResult(answerKey))
                assertNull(restored.consumeResult(answerKey))
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
        val source = createKompassNavController(defaultNavigationState(A.entryWith(mapOf("a" to Answer("x")))), serializers)
        try {
            val restored = createKompassNavController(A, savedNavigationState = source.saveNavigationState())
            try { assertNotNull(restored.restorationFailure) } finally { restored.close() }
        } finally { source.close() }
    }

    @Test fun direction_comes_from_commands_and_result_consumption_does_not_change_it() {
        val nav = createKompassNavController(A)
        try {
            nav.navigateForResult(B.toKompassEntry(), answerKey)
            nav.pop(result = Answer("x"), resultKey = answerKey)
            assertEquals(NavDirection.Pop, nav.direction)
            nav.consumeResult(answerKey)
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
