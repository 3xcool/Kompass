package com.tekmoon.kompass

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.*

/**
 * Covers [withResult], [withCancelledResult] and [withPendingResult], the builders that write the
 * result state a `@Preview` or a screen test needs without running the reducer.
 */
class ResultStateBuildersTest {

    private object Caller : Destination { override val id = "caller" }
    private object Producer : Destination { override val id = "producer" }
    private val key = ResultKey<Answer>("answer")
    private val other = ResultKey<Answer>("other")

    @Test fun with_result_reports_delivered() {
        val entry = Caller.toKompassEntry().withResult(key, Answer("yes"))

        assertEquals(ResultState.Delivered(Answer("yes")), entry.peekResult(key))
    }

    @Test fun with_cancelled_result_reports_cancelled() {
        val entry = Caller.toKompassEntry().withCancelledResult(key)

        assertEquals(ResultState.Cancelled, entry.peekResult(key))
    }

    @Test fun with_pending_result_reports_pending() {
        val entry = Caller.toKompassEntry().withPendingResult(key)

        assertEquals(ResultState.Pending, entry.peekResult(key))
    }

    @Test fun closing_a_request_clears_the_waiting_marker() {
        val waiting = Caller.toKompassEntry().withPendingResult(key)
        assertEquals(key.name, waiting.pendingResultKey)

        assertNull(waiting.withResult(key, Answer("yes")).pendingResultKey)
        assertNull(waiting.withCancelledResult(key).pendingResultKey)
    }

    @Test fun closing_one_request_leaves_another_one_waiting() {
        val entry = Caller.toKompassEntry().withPendingResult(other).withResult(key, Answer("yes"))

        assertEquals(other.name, entry.pendingResultKey)
        assertEquals(ResultState.Delivered(Answer("yes")), entry.peekResult(key))
    }

    @Test fun a_later_outcome_replaces_an_earlier_one() {
        val entry = Caller.toKompassEntry().withResult(key, Answer("first")).withCancelledResult(key)
        assertEquals(ResultState.Cancelled, entry.peekResult(key))

        assertEquals(
            ResultState.Delivered(Answer("second")),
            entry.withResult(key, Answer("second")).peekResult(key),
        )
    }

    @Test fun the_builders_keep_occurrence_identity() {
        val entry = Caller.toKompassEntry()

        assertEquals(entry.id, entry.withPendingResult(key).id)
        assertEquals(entry.id, entry.withResult(key, Answer("yes")).id)
        assertEquals(entry.id, entry.withCancelledResult(key).id)
    }

    @Test fun the_builders_leave_the_original_entry_alone() {
        val entry = Caller.toKompassEntry()
        entry.withResult(key, Answer("yes"))

        assertNull(entry.peekResult(key))
        assertNull(entry.pendingResultKey)
    }

    @Test fun with_result_matches_what_the_reducer_writes() {
        // The point of the builders is to stand in for the reducer. If the reducer ever writes a
        // different shape, this test fails instead of a preview quietly drifting from production.
        val nav = createKompassNavController(Caller)
        val delivered: KompassEntry
        val cancelled: KompassEntry
        try {
            nav.navigate(Producer, resultKey = key)
            nav.pop(Answer("yes"), key)
            delivered = nav.currentEntry

            nav.navigate(Producer, resultKey = other)
            nav.pop()
            cancelled = nav.currentEntry
        } finally {
            nav.close()
        }

        val built = Caller.toKompassEntry().withResult(key, Answer("yes")).withCancelledResult(other)

        assertEquals(delivered.peekResult(key), built.peekResult(key))
        assertEquals(cancelled.peekResult(other), built.peekResult(other))
        assertEquals(cancelled.pendingResultKey, built.pendingResultKey)
    }

    @Test fun a_built_entry_survives_serialization() {
        val json = Json {
            classDiscriminator = "_type"
            serializersModule = SerializersModule {
                polymorphic(NavigationResult::class) { subclass(Answer::class) }
            }
        }
        val state = defaultNavigationState(
            Caller.toKompassEntry().withResult(key, Answer("yes")).withCancelledResult(other)
        )
        val serializer = NavigationState.serializer(KompassEntry.serializer())

        assertEquals(state, json.decodeFromString(serializer, json.encodeToString(serializer, state)))
    }

    @Serializable private data class Answer(val value: String) : NavigationResult
}
