package com.tekmoon.kompass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * How one closed result request is stored in a [KompassEntry].
 *
 * This is the shape that enters the saved navigation state. It pairs with the public [ResultState]:
 * same outcomes, minus [ResultState.Pending]. Pending cannot be stored by construction, because an
 * open request is recorded by [KompassEntry.pendingResultKey] instead.
 *
 * Internal on purpose. An application reads a result through [peekResult] and
 * [KompassNavController.consumeResult], and never names this type. [NavigationResult] stays the one
 * result type an application implements.
 */
@Serializable
internal sealed interface StoredResult {

    /** The destination answered with [value]. */
    @Serializable
    @SerialName("delivered")
    data class Delivered(val value: NavigationResult) : StoredResult

    /** The destination left the back stack without answering. */
    @Serializable
    @SerialName("cancelled")
    data object Cancelled : StoredResult
}

/**
 * Names a navigation result and carries its expected result type to call sites.
 *
 * The type parameter is compile-time information only. Two keys with the same [name] and different
 * type arguments are the same key at run time, so give a key a name that is unique in the
 * application. Declaring the key on the destination that produces the result keeps it unique and
 * keeps the contract next to its producer:
 *
 * ```
 * data object Second : AppDestination {
 *     override val id = "app/second"
 *     val Name = ResultKey<NameResult>("app/second/name")
 * }
 * ```
 *
 * The key is never serialized as a wrapper. Only [name] enters the navigation state.
 */
@JvmInline
value class ResultKey<T : NavigationResult>(val name: String)

/**
 * The state of one navigation result request, seen by the entry that started it.
 *
 * A request starts with [KompassNavController.navigateForResult] and ends when the entry consumes
 * it with [KompassNavController.consumeResult]. Read it with [peekResult].
 */
sealed interface ResultState<out T : NavigationResult> {

    /** The request is open. The destination that must answer is still on the back stack. */
    data object Pending : ResultState<Nothing>

    /** The destination answered with [value]. */
    data class Delivered<out T : NavigationResult>(val value: T) : ResultState<T>

    /**
     * The destination left the back stack without an answer.
     *
     * Back, predictive Back and a plain [KompassNavController.pop] all produce this state.
     */
    data object Cancelled : ResultState<Nothing>
}

/**
 * Reads the state of a result request without changing it.
 *
 * Safe to call while rendering. It returns:
 * - [ResultState.Pending] while the request is open;
 * - [ResultState.Delivered] once the destination answered;
 * - [ResultState.Cancelled] once the destination left without an answer;
 * - `null` when this entry has no request for [key], or when the delivered value has another type.
 *
 * Use it as the key of an effect, then call [KompassNavController.consumeResult] inside the effect
 * to close the request. Reading alone never closes it.
 */
inline fun <reified T : NavigationResult> KompassEntry.peekResult(key: ResultKey<T>): ResultState<T>? {
    if (isResultCancelled(key.name)) return ResultState.Cancelled
    val delivered = deliveredResult(key.name)
    if (delivered != null) return if (delivered is T) ResultState.Delivered(delivered) else null
    return if (pendingResultKey == key.name) ResultState.Pending else null
}

/**
 * A navigation result request that Kompass could not honour.
 *
 * Kompass reports it through `onNavigationError` and leaves the back stack unchanged. It never
 * throws, because every one of these can come from a repeated tap on a button.
 */
class NavigationResultException internal constructor(message: String) : IllegalStateException(message)
