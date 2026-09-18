package com.tekmoon.kompass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A count and a popUntil both say where a pop stops. Neither may be ignored in silence. */
class NavigationPopCountTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private fun stackOfThree(): KompassNavController {
        val nav = createKompassNavController(A)
        nav.navigate(B.toKompassEntry())
        nav.navigate(A.toKompassEntry())
        return nav
    }

    @Test fun a_count_of_zero_pops_nothing() {
        val nav = stackOfThree()
        nav.pop(count = 0)
        assertEquals(listOf("a", "b", "a"), nav.backStack.map { it.destinationId })
    }

    @Test fun a_negative_count_pops_nothing() {
        val nav = stackOfThree()
        nav.pop(count = -3)
        assertEquals(listOf("a", "b", "a"), nav.backStack.map { it.destinationId })
    }

    @Test fun a_count_of_one_still_pops_one() {
        val nav = stackOfThree()
        nav.pop(count = 1)
        assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
    }

    @Test fun a_count_above_the_stack_stops_at_the_root() {
        val nav = stackOfThree()
        nav.pop(count = 99)
        assertEquals(listOf("a"), nav.backStack.map { it.destinationId })
    }

    @Test fun a_count_above_one_cannot_be_combined_with_pop_until() {
        val failure = assertFailsWith<IllegalArgumentException> {
            NavigationCommand.Pop(count = 2, popUntil = "b")
        }
        assertEquals(true, failure.message?.contains("not both"))
    }

    @Test fun a_count_of_one_may_carry_a_pop_until() {
        val nav = stackOfThree()
        nav.pop(count = 1, popUntil = "b")
        assertEquals(listOf("a", "b"), nav.backStack.map { it.destinationId })
    }
}
