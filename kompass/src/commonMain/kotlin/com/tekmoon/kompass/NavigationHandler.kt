package com.tekmoon.kompass

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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
    fun reduce(
        state: NavigationState,
        command: NavigationCommand
    ): NavigationState {
        return when (command) {

            is NavigationCommand.Navigate -> {
                val baseStack = navigateBaseStack(state, command)

                // Build the stack first, then record the request. Doing it in this order keeps one
                // rule for every stack policy: the request belongs to the entry that ends up
                // directly below the new top. reuseIfExists can move the requester itself to the
                // top, and then no entry is left to receive the answer.
                val pushedStack = if (command.reuseIfExists) {
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

                state.copy(backStack = applyResultRequest(pushedStack, baseStack.lastOrNull(), command.pendingResultKey))
            }

            is NavigationCommand.Pop -> {
                val stack = state.backStack
                if (stack.size <= 1) return state

                // A count below one asks for no entry at all. Removing one anyway would take a
                // screen the caller never named. It is not an error, so it never throws.
                if (command.count < 1) return state

                // A delivery that cannot be honoured changes nothing at all. Popping anyway would
                // remove a screen the user did not ask to leave: on the second tap of a repeated
                // tap, the first pop already delivered and cleared the request.
                if (resultIssue(state, command) != null) return state

                val newStack: List<KompassEntry> =
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

                // A command that pops nothing must not close a request either. popUntil naming the
                // top entry lands here: the stack is already in the wanted shape.
                if (newStack.size == stack.size) return state

                // Close the request of the entry the pop reveals. A matching answer is delivered;
                // a pop with no answer cancels, because the destination that had to answer is
                // leaving the stack. A delivery that does not match never reaches this point.
                val survivor = newStack.last()
                val pending = survivor.pendingResultKey
                val finalStack = if (pending == null) newStack else {
                    val outcome = command.result
                        ?.let { StoredResult.Delivered(it) }
                        ?: StoredResult.Cancelled
                    newStack.dropLast(1) + survivor.copyInternal(
                        pendingResultKey = null,
                        results = survivor.results.put(pending, outcome),
                    )
                }

                state.copy(backStack = finalStack.toImmutableList())
            }

            is NavigationCommand.ConsumeResult -> {
                state.copy(backStack = state.backStack.map { entry ->
                    if (entry.id == command.entryId) entry.copyInternal(
                        pendingResultKey = entry.pendingResultKey.takeIf { it != command.key },
                        results = entry.results.remove(command.key),
                    ) else entry
                }.toImmutableList())
            }

            is NavigationCommand.ReplaceStack -> {
                state.copy(backStack = command.entries.toImmutableList())
            }
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
    backStack: ImmutableList<KompassEntry>,
    destinationId: String,
    inclusive: Boolean
): ImmutableList<KompassEntry> {
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

/**
 * The stack a [NavigationCommand.Navigate] starts from, after its stack policy is applied and
 * before its entry is pushed. Its last element is the entry that asks for a result.
 */
internal fun navigateBaseStack(
    state: NavigationState,
    command: NavigationCommand.Navigate,
): ImmutableList<KompassEntry> = when {
    command.clearBackStack -> persistentListOf()
    command.popUpTo != null ->
        popUpToDestination(state.backStack, command.popUpTo, command.popUpToInclusive)

    else -> state.backStack
}

/**
 * Records a result request on [requester], once the new entry is already on the stack.
 *
 * The request is kept only when [requester] is the entry directly below the new top, because that
 * is the only entry a [NavigationCommand.Pop] can answer. Every other case leaves the stack
 * unchanged; [resultRequestLoss] explains it to the caller.
 *
 * A repeated request under the same key supersedes the previous one, so any answer still sitting
 * there is dropped. Failing here would turn a repeated tap into an exception.
 */
private fun applyResultRequest(
    stack: ImmutableList<KompassEntry>,
    requester: KompassEntry?,
    key: String?,
): ImmutableList<KompassEntry> {
    if (key == null || requester == null) return stack
    val index = stack.size - 2
    if (index < 0 || stack[index].id != requester.id) return stack
    return (stack.take(index) + stack[index].copyInternal(
        pendingResultKey = key,
        results = stack[index].results.remove(key),
    ) + stack.drop(index + 1)).toImmutableList()
}

/**
 * Explains why a result request cannot be opened, or returns null when it can.
 *
 * A request needs an entry that stays directly below the new top. [NavigationCommand.clearBackStack]
 * and an inclusive [NavigationCommand.popUpTo] can remove that entry, and `reuseIfExists` can move
 * it to the top instead, which leaves nothing below to answer.
 */
internal fun resultRequestLoss(
    state: NavigationState,
    command: NavigationCommand.Navigate,
): String? {
    val key = command.pendingResultKey ?: return null
    val base = navigateBaseStack(state, command)
    val requester = base.lastOrNull() ?: return "A result request under \"$key\" has no entry to " +
        "belong to, because the command emptied the back stack. Drop clearBackStack, or use a " +
        "popUpTo that keeps the entry that waits for the answer."
    val reusesRequester = command.reuseIfExists && requester.destinationId == command.entry.destinationId &&
        base.indexOfLast { it.destinationId == command.entry.destinationId } == base.size - 1
    return if (reusesRequester) {
        "The entry \"${requester.destinationId}\" asked itself for \"$key\" through reuseIfExists, " +
            "so no entry is left below to answer. Drop reuseIfExists, or drop the result key."
    } else {
        null
    }
}

/**
 * Explains why a [NavigationCommand.Pop] cannot deliver its result, or returns null when the
 * delivery is valid or when the command carries no result at all.
 *
 * The reducer and [KompassNavController] share this function, so the state change and the report
 * can never disagree.
 */
internal fun resultIssue(state: NavigationState, command: NavigationCommand.Pop): String? {
    if (command.result == null && command.resultKey == null) return null
    val key = command.resultKey?.name
        ?: return "pop received a result with no result key. Use pop(result, resultKey)."
    if (command.result == null) return "pop received the result key \"$key\" with no result."
    if (command.count != 1 || command.popUntil != null) {
        return "A result cannot be delivered while popping more than one entry. Key \"$key\"."
    }
    if (state.backStack.size <= 1) {
        return "The root entry cannot be popped, so \"$key\" was not delivered."
    }
    val survivor = state.backStack[state.backStack.size - 2]
    return when (survivor.pendingResultKey) {
        key -> null
        null -> "The entry \"${survivor.destinationId}\" is not waiting for a result. " +
            "Open the destination with navigate(entry, resultKey = ...) to receive \"$key\"."

        else -> "The entry \"${survivor.destinationId}\" waits for " +
            "\"${survivor.pendingResultKey}\", not \"$key\"."
    }
}

/**
 * Explains what a new result request throws away, or returns null when it throws away nothing.
 *
 * Unlike [resultIssue] this never blocks the command. Opening the destination is what the user
 * asked for, and refusing it would leave a button that does nothing. A stale answer only survives
 * this long when the screen never called [KompassNavController.consumeResult], so the report is for
 * whoever wrote that screen.
 */
internal fun discardedResultReport(
    state: NavigationState,
    command: NavigationCommand.Navigate,
): String? {
    val key = command.pendingResultKey ?: return null
    resultRequestLoss(state, command)?.let { return it }
    val requester = navigateBaseStack(state, command).lastOrNull() ?: return null
    val previous = requester.results[key] ?: return null
    val outcome = if (previous is StoredResult.Delivered) "an answer" else "a cancellation"
    return "The entry \"${requester.destinationId}\" opened a new request under \"$key\" while " +
        "$outcome to the previous one was never consumed. The old state is dropped. Call " +
        "consumeResult when the request closes."
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
     *
     * @param pendingResultKey Name of the [ResultKey] the current top entry starts waiting for.
     * A repeated request under the same name replaces the previous one. Combining it with
     * [clearBackStack] or [popUpTo] can remove the entry that would receive the answer; prefer
     * [KompassNavController.navigate] with a resultKey and no clearBackStack.
     */
    data class Navigate(
        val entry: KompassEntry,
        val clearBackStack: Boolean = false,
        val popUpTo: String? = null,
        val popUpToInclusive: Boolean = false,
        val reuseIfExists: Boolean = false,
        val pendingResultKey: String? = null
    ) : NavigationCommand

    /**
     * Pop entries from the back stack.
     *
     * Every pop closes the request of the entry it reveals. A result that matches the awaited key
     * is delivered; any other pop ends the request as [ResultState.Cancelled]. Back and predictive
     * Back reach the reducer through this command, so both cancel an open request.
     *
     * @param result Optional [NavigationResult] to be delivered to
     * the previous back stack entry under [resultKey].
     *
     * @param resultKey Optional typed key under which the result is stored. It must match the
     * awaited key of the revealed entry, otherwise the delivery is rejected and reported.
     *
     * @param count Number of entries to pop. Defaults to 1. A result is delivered only when this
     * is 1 and [popUntil] is null. A count below 1 pops nothing, so a computed count never removes
     * a screen the caller did not ask for.
     *
     * @param popUntil Optional destination ID indicating that all
     * entries after that destination should be popped. It cannot be combined with a [count] above
     * 1, because the two describe different stops and one would have to be ignored.
     */
    data class Pop(
        val result: NavigationResult? = null,
        val count: Int = 1,
        val popUntil: String? = null,
        val resultKey: ResultKey<*>? = null
    ) : NavigationCommand {

        init {
            require(count <= 1 || popUntil == null) {
                "Pop takes either a count above 1 or a popUntil, not both. " +
                    "count=$count, popUntil=$popUntil"
            }
        }
    }

    /**
     * Replace the whole back stack.
     *
     * One command applies a whole stack, so a server payload, a multi-level deep link and a
     * restored session all arrive in a single state change. Applying the same stack as a
     * `ReplaceStack` followed by several `Navigate` commands would publish every intermediate state
     * and play one animation per step.
     *
     * The first entry becomes the root and the last becomes the active destination. One entry in the
     * list replaces the whole stack with that entry.
     *
     * @param entries The new back stack, from root to top. It must not be empty.
     */
    data class ReplaceStack(
        val entries: ImmutableList<KompassEntry>
    ) : NavigationCommand {

        init {
            require(entries.isNotEmpty()) { "ReplaceStack requires at least one entry" }
        }

        /** Convenience for `listOf(...)` call sites. It converts, so the command holds no caller list. */
        constructor(entries: List<KompassEntry>) : this(entries.toImmutableList())
    }
}
