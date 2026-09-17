package com.tekmoon.samples

import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationCommand
import com.tekmoon.kompass.NavigationHandler
import com.tekmoon.kompass.NavigationState
import com.tekmoon.kompass.toKompassEntry
import kotlinx.collections.immutable.persistentListOf

class NavigationHandlerTestRobot(
    start: Destination
) {

    private val handler = NavigationHandler()

    var state: NavigationState =
        NavigationState(
            backStack = persistentListOf(
                start.toKompassEntry()
            )
        )
        private set

    // === Basic Navigation ===

    fun navigate(destination: Destination) {
        state = handler.reduce(
            state,
            NavigationCommand.Navigate(
                entry = destination.toKompassEntry()
            )
        )
    }

    // === Pop Variations ===

    fun pop() {
        state = handler.reduce(
            state,
            NavigationCommand.Pop()
        )
    }

    fun popCount(count: Int) {
        state = handler.reduce(
            state,
            NavigationCommand.Pop(count = count)
        )
    }

    fun popUntil(destinationId: String) {
        state = handler.reduce(
            state,
            NavigationCommand.Pop(popUntil = destinationId)
        )
    }

    // === PopUpTo Variations ===

    fun navigateWithPopUpTo(
        destination: Destination,
        popUpTo: String,
        inclusive: Boolean
    ) {
        state = handler.reduce(
            state,
            NavigationCommand.Navigate(
                entry = destination.toKompassEntry(),
                popUpTo = popUpTo,
                popUpToInclusive = inclusive
            )
        )
    }

    // === Reuse If Exists ===

    fun navigateWithReuse(destination: Destination) {
        state = handler.reduce(
            state,
            NavigationCommand.Navigate(
                entry = destination.toKompassEntry(),
                reuseIfExists = true
            )
        )
    }

    // === Clear Back Stack ===

    fun navigateWithClearBackStack(destination: Destination) {
        state = handler.reduce(
            state,
            NavigationCommand.Navigate(
                entry = destination.toKompassEntry(),
                clearBackStack = true
            )
        )
    }

    // === Complex Scenarios ===

    fun navigateWithPopUpToAndReuse(
        destination: Destination,
        popUpTo: String,
        inclusive: Boolean
    ) {
        state = handler.reduce(
            state,
            NavigationCommand.Navigate(
                entry = destination.toKompassEntry(),
                popUpTo = popUpTo,
                popUpToInclusive = inclusive,
                reuseIfExists = true
            )
        )
    }

    // === Assertions ===

    fun assertTop(expected: Destination) {
        val top =
            state.backStack.lastOrNull()
                ?: error("Back stack is empty")

        check(top.destinationId == expected.id) {
            "Expected ${expected.id}, found ${top.destinationId}"
        }
    }

    fun assertStackSize(expected: Int) {
        check(state.backStack.size == expected) {
            "Expected stack size $expected, found ${state.backStack.size}"
        }
    }

    fun assertStackContains(destination: Destination) {
        check(state.backStack.any { it.destinationId == destination.id }) {
            "Stack does not contain ${destination.id}"
        }
    }

    fun assertStackDoesNotContain(destination: Destination) {
        check(state.backStack.none { it.destinationId == destination.id }) {
            "Stack contains ${destination.id} but shouldn't"
        }
    }
}