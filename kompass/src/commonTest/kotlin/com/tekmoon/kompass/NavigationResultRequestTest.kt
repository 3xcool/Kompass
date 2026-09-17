package com.tekmoon.kompass

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A result request belongs to the entry that stays directly below the new top. Every command that
 * removes that entry must say so, and none of them may drop an answer in silence.
 */
class NavigationResultRequestTest {
    private object Home : Destination { override val id = "home" }
    private object Picker : Destination { override val id = "picker" }

    @Serializable private data class Answer(val value: String) : NavigationResult
    private val answerKey = ResultKey<Answer>("answer")

    private class Reports {
        val messages = mutableListOf<String>()
        fun record(error: NavigationResultException) { messages += error.message.orEmpty() }
        fun only(): String = messages.single()
    }

    @Test fun reuse_keeps_an_answer_the_screen_has_not_read_yet() {
        val reports = Reports()
        val nav = createKompassNavController(Home, onNavigationError = reports::record)
        nav.navigate(Picker.toKompassEntry(), resultKey = answerKey)
        nav.pop(Answer("kept"), answerKey)

        // Home is reused before it ever read the answer. Its identity survives, so its answer must.
        nav.navigate(Home.toKompassEntry(), reuseIfExists = true)

        val state = nav.consumeResult(answerKey)
        assertIs<ResultState.Delivered<Answer>>(state)
        assertEquals("kept", state.value.value)
        assertTrue(reports.messages.isEmpty(), "a kept answer is not a problem to report")
    }

    @Test fun reuse_keeps_an_open_request() {
        val nav = createKompassNavController(Home)
        nav.navigate(Picker.toKompassEntry(), resultKey = answerKey)
        // A third destination arrives and moves Picker back to the top; Home still waits.
        nav.navigate(Picker.toKompassEntry(), reuseIfExists = true)

        nav.pop(Answer("late"), answerKey)
        val state = nav.consumeResult(answerKey)
        assertIs<ResultState.Delivered<Answer>>(state)
        assertEquals("late", state.value.value)
    }

    @Test fun asking_your_own_destination_for_a_result_is_reported() {
        val reports = Reports()
        val nav = createKompassNavController(Home, onNavigationError = reports::record)
        nav.navigate(Picker.toKompassEntry())

        // Picker is on top and reuses itself, so no entry is left below to answer.
        nav.navigate(Picker.toKompassEntry(), reuseIfExists = true, resultKey = answerKey)

        assertTrue(reports.only().contains("asked itself"), reports.only())
        assertTrue(nav.backStack.none { it.pendingResultKey != null }, "no entry may be left waiting")
    }

    @Test fun clearing_the_back_stack_with_a_result_key_is_reported() {
        val reports = Reports()
        val nav = createKompassNavController(Home, onNavigationError = reports::record)
        nav.navigate(Picker.toKompassEntry(), clearBackStack = true, resultKey = answerKey)

        assertTrue(reports.only().contains("emptied the back stack"), reports.only())
        assertTrue(nav.backStack.none { it.pendingResultKey != null })
    }

    @Test fun an_inclusive_pop_up_to_that_empties_the_stack_is_reported() {
        val reports = Reports()
        val nav = createKompassNavController(Home, onNavigationError = reports::record)
        nav.navigate(
            Picker.toKompassEntry(),
            popUpTo = Home.id,
            popUpToInclusive = true,
            resultKey = answerKey,
        )

        // The message must not blame clearBackStack, which this caller never used.
        assertTrue(reports.only().contains("popUpTo"), reports.only())
        assertTrue(nav.backStack.none { it.pendingResultKey != null })
    }

    @Test fun a_plain_pop_up_to_still_opens_the_request() {
        val nav = createKompassNavController(Home)
        nav.navigate(Picker.toKompassEntry())
        nav.navigate(Picker.toKompassEntry(), popUpTo = Home.id, resultKey = answerKey)

        assertEquals(Home.id, nav.backStack[nav.backStack.size - 2].destinationId)
        nav.pop(Answer("ok"), answerKey)
        assertIs<ResultState.Delivered<Answer>>(nav.consumeResult(answerKey))
    }

    @Test fun a_request_that_was_never_opened_delivers_nothing() {
        val nav = createKompassNavController(Home)
        nav.navigate(Picker.toKompassEntry(), clearBackStack = true, resultKey = answerKey)
        assertNull(nav.consumeResult(answerKey))
    }
}
