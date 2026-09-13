package com.tekmoon.kompass

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.collections.plus
import kotlin.compareTo

/**
 * Reducer responsible for applying [NavigationCommand]s to a [NavigationState].
 *
 * [NavigationHandler] is the core of the navigation engine.
 * It implements a pure, deterministic state transition function:
 *
 * NavigationState + NavigationCommand -> NavigationState
 *
 * This class:
 * - Contains all navigation rules
 * - Does not perform side effects
 * - Does not depend on Compose or platform APIs
 *
 * All navigation mutations must go through this reducer to guarantee
 * consistency, testability, and predictable behavior.
 */
class NavigationHandler() {

    /**
     * Applies a [NavigationCommand] to the given [NavigationState].
     *
     * @param state The current navigation state.
     *
     * @param command The navigation command describing the desired state change.
     *
     * @return A new [NavigationState] representing the result of applying
     * the command.
     */
    // The reducer must keep handling the deprecated ReplaceRoot until it is removed.
    @Suppress("DEPRECATION")
    fun reduce(
        state: NavigationState,
        command: NavigationCommand
    ): NavigationState {
        return when (command) {

            is NavigationCommand.Navigate -> {
                val baseStack = when {
                    command.clearBackStack -> persistentListOf<BackStackEntry>()
                    command.popUpTo != null -> popUpToDestination(
                        state.backStack,
                        command.popUpTo,
                        command.popUpToInclusive
                    )

                    else -> state.backStack
                }

                val newStack = if (command.reuseIfExists) {
                    val existingIndex =
                        baseStack.indexOfLast { it.destinationId == command.entry.destinationId }
                    if (existingIndex >= 0) {
                        // Move the matching occurrence to the top, updating its payload but retaining identity
                        (baseStack.filterIndexed { index, _ -> index != existingIndex } +
                            (if (command.entry.scopeId == baseStack[existingIndex].scopeId)
                                command.entry.withIdentityOf(baseStack[existingIndex]) else command.entry)).toImmutableList()
                    } else {
                        // Entry doesn't exist, add it
                        (baseStack + command.entry).toImmutableList()
                    }
                } else {
                    // Regular behavior: always add new instance
                    (baseStack + command.entry).toImmutableList()
                }

                state.copy(backStack = newStack)
            }

            is NavigationCommand.Pop -> {
                val stack = state.backStack
                if (stack.size <= 1) return state

                val newStack: List<BackStackEntry> =
                    when {
                        // Pop multiple entries (go back N steps)
                        command.count > 1 -> {
                            val maxPop = minOf(command.count, stack.size - 1)
                            val targetIndex = stack.size - 1 - maxPop
                            stack.take(targetIndex + 1)
                        }

                        // Pop until destination (exclusive)
                        command.popUntil != null -> {
                            val targetIndex =
                                stack.indexOfLast { it.destinationId == command.popUntil }

                            if (targetIndex < 0) return state

                            stack.take(targetIndex + 1)
                        }

                        // Pop single entry
                        else -> {
                            stack.dropLast(1)
                        }
                    }

                // Attach navigation result (only for single pop)
                val finalStack =
                    if (command.result != null &&
                        command.count == 1 &&
                        command.popUntil == null &&
                        newStack.isNotEmpty()
                    ) {
                        val popped = stack.last()
                        val key = popped.pendingResultKey

                        if (key != null) {
                            val receiver = newStack.last()
                            newStack.dropLast(1) + receiver.copy(
                                results = receiver.results + (key to command.result)
                            )
                        } else {
                            newStack
                        }
                    } else {
                        newStack
                    }

                state.copy(backStack = finalStack.toImmutableList())
            }

            is NavigationCommand.ConsumeResult -> {
                state.copy(backStack = state.backStack.map { entry ->
                    if (entry.id == command.entryId) entry.copy(results = entry.results - command.key) else entry
                }.toImmutableList())
            }

            is NavigationCommand.ReplaceRoot -> {
                state.copy(backStack = persistentListOf(command.entry))
            }

            is NavigationCommand.ReplaceStack -> {
                state.copy(backStack = command.entries.toImmutableList())
            }
        }
    }

    /**
     * Helper function to pop entries up to a destination.
     *
     * @param backStack Current immutable back stack.
     *
     * @param destinationId Identifier of the destination to pop up to.
     *
     * @param inclusive If true, removes the destination itself;
     * if false, keeps the destination in the back stack.
     *
     * @return The resulting back stack after applying the pop operation.
     */
    private fun popUpToDestination(
        backStack: ImmutableList<BackStackEntry>,
        destinationId: String,
        inclusive: Boolean
    ): ImmutableList<BackStackEntry> {
        val index = backStack.indexOfLast { it.destinationId == destinationId }
        return if (index >= 0) {
            if (inclusive) {
                backStack.take(index).toImmutableList()
            } else {
                backStack.take(index + 1).toImmutableList()
            }
        } else {
            backStack
        }
    }
}

/**
 * Represents a command describing a navigation state transition.
 *
 * [NavigationCommand]s are pure data objects that describe what should
 * happen, not how it should happen.
 *
 * They are interpreted and applied by [NavigationHandler].
 */
sealed interface NavigationCommand {

    /** Removes one result from a specific occurrence; does not navigate or animate. */
    data class ConsumeResult(val entryId: String, val key: String) : NavigationCommand


    /**
     * Navigate to a destination.
     *
     * @param entry The back stack entry to navigate to.
     *
     * @param clearBackStack If true, clears the entire back stack
     * before navigating.
     *
     * @param popUpTo Optional destination ID to pop up to before navigating.
     *
     * @param popUpToInclusive If true, the destination specified in
     * [popUpTo] is also removed.
     *
     * @param reuseIfExists If true and a destination with the same ID
     * already exists in the stack, moves the last matching occurrence to the top and updates
     * its payload. Its identity (and UI state) is retained when the scope is unchanged;
     * explicitly supplying a different scope starts new ownership.
     */
    data class Navigate(
        val entry: BackStackEntry,
        val clearBackStack: Boolean = false,
        val popUpTo: String? = null,
        val popUpToInclusive: Boolean = false,
        val reuseIfExists: Boolean = false
    ) : NavigationCommand

    /**
     * Pop entries from the back stack.
     *
     * @param result Optional [NavigationResult] to be delivered to
     * the previous back stack entry.
     *
     * @param count Number of entries to pop. Defaults to 1.
     *
     * @param popUntil Optional destination ID indicating that all
     * entries after that destination should be popped.
     */
    data class Pop(
        val result: NavigationResult? = null,
        val count: Int = 1,
        val popUntil: String? = null
    ) : NavigationCommand

    /**
     * Replace the root of the back stack.
     *
     * @param entry The new root [BackStackEntry] that will become
     * the only entry in the back stack.
     */
    @Deprecated(
        message = "Use ReplaceStack, which applies one entry or a whole stack.",
        replaceWith = ReplaceWith("NavigationCommand.ReplaceStack(listOf(entry))"),
    )
    data class ReplaceRoot(
        val entry: BackStackEntry
    ) : NavigationCommand

    /**
     * Replace the whole back stack.
     *
     * One command applies a whole stack, so a server payload, a multi-level deep link and a
     * restored session all arrive in a single state change. Applying the same stack as a
     * `ReplaceStack` followed by several `Navigate` commands would publish every intermediate state
     * and play one animation per step.
     *
     * The first entry becomes the root and the last becomes the active destination. One entry in the
     * list replaces the whole stack with that entry, which is what `ReplaceRoot` did.
     *
     * @param entries The new back stack, from root to top. It must not be empty.
     */
    data class ReplaceStack(
        val entries: List<BackStackEntry>
    ) : NavigationCommand {

        init {
            require(entries.isNotEmpty()) { "ReplaceStack requires at least one entry" }
        }
    }
}
