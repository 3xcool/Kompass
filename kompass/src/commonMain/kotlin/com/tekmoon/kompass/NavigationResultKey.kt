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
 * A request starts with the `resultKey` of [KompassNavController.navigate] and ends when it consumes
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
 * Returns a copy of this entry that holds [value] as the answer to [key].
 *
 * It closes an open request for [key], the same way a `pop` that carries a result does. Occurrence
 * identity is kept, so the copy is the same entry holding an answer.
 *
 * This writes a state that only the reducer produces at run time. Use it to render the "answer
 * arrived" state directly, in a `@Preview` or in a test of a screen:
 *
 * ```
 * @Preview
 * @Composable
 * private fun CheckoutWithAddress() {
 *     CheckoutScreen(entry = Checkout.toKompassEntry().withResult(Address.Result, AddressResult(...)))
 * }
 * ```
 *
 * Production code never needs it. There, a request opens with the `resultKey` of
 * [KompassNavController.navigate] and closes with [KompassNavController.pop].
 */
fun <T : NavigationResult> KompassEntry.withResult(key: ResultKey<T>, value: T): KompassEntry =
    closeRequest(key.name, StoredResult.Delivered(value))

/**
 * Returns a copy of this entry whose request under [key] ended without an answer.
 *
 * This is the state Back, predictive Back and a plain `pop` leave behind. See [withResult].
 */
fun KompassEntry.withCancelledResult(key: ResultKey<*>): KompassEntry =
    closeRequest(key.name, StoredResult.Cancelled)

/**
 * Returns a copy of this entry that waits for [key], with no answer yet.
 *
 * This is the state the `resultKey` of [KompassNavController.navigate] leaves behind. It completes
 * the set with [withResult] and [withCancelledResult], one per [ResultState]. See [withResult].
 */
fun KompassEntry.withPendingResult(key: ResultKey<*>): KompassEntry =
    copyInternal(pendingResultKey = key.name, results = results.remove(key.name))

private fun KompassEntry.closeRequest(key: String, outcome: StoredResult): KompassEntry =
    copyInternal(
        // Delivery clears the waiting marker, the same as the reducer does when a pop closes it.
        pendingResultKey = pendingResultKey.takeIf { it != key },
        results = results.put(key, outcome),
    )

/**
 * A navigation result request that Kompass could not honour.
 *
 * Kompass reports it through `onNavigationError` and leaves the back stack unchanged. It never
 * throws, because every one of these can come from a repeated tap on a button.
 */
class NavigationResultException internal constructor(message: String) : IllegalStateException(message)
