package com.tekmoon.kompass

import androidx.compose.runtime.Stable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * A [Destination] that carries strongly-typed arguments.
 *
 * Use this when a screen needs structured input. The [argsSerializer] is the
 * single source of truth for how arguments are encoded into the opaque
 * [BackStackEntry.args] string and decoded back when the screen reads them.
 *
 * ### Defining a typed destination
 *
 * ```
 * @Serializable
 * data class ProductDetailsArgs(
 *     val productId: Long,
 *     val source: String,
 * )
 *
 * object ProductDetailsDestination : TypedDestination<ProductDetailsArgs> {
 *     override val id: String = "kompass/products/details"
 *     override val argsSerializer = ProductDetailsArgs.serializer()
 * }
 * ```
 *
 * ### Navigating with typed args
 *
 * ```
 * navController.navigateTo(
 *     ProductDetailsDestination,
 *     ProductDetailsArgs(productId = 42, source = "deeplink"),
 * )
 * ```
 *
 * ### Reading args inside a screen
 *
 * ```
 * @Composable
 * fun ProductDetailsScreen(entry: BackStackEntry, navController: NavController) {
 *     val args: ProductDetailsArgs = navController.requireArgs(ProductDetailsDestination, entry)
 *     // ...
 * }
 * ```
 *
 * For destinations that don't carry arguments, keep using the plain
 * [Destination] interface — there's no benefit to wrapping `Unit` in JSON.
 *
 * @param T The argument data class. Should be `@Serializable` so that
 * Kompass can encode/decode it using the controller's [Json] instance.
 */
@Stable
interface TypedDestination<T : Any> : Destination {
    /**
     * Serializer used to encode and decode arguments of type [T].
     *
     * Typically `MyArgs.serializer()` from `@Serializable` data classes.
     */
    val argsSerializer: KSerializer<T>
}

// ----------------------------------------------------------------------------
// Helpers — destination-side
// ----------------------------------------------------------------------------

/**
 * Encodes typed [args] into the opaque [ArgsJson] string stored on
 * [BackStackEntry.args].
 */
fun <T : Any> TypedDestination<T>.encodeArgs(args: T, json: Json): ArgsJson =
    json.encodeToString(argsSerializer, args)

/**
 * Decodes args from a [BackStackEntry] for this typed destination.
 *
 * Returns `null` if the entry has no args attached.
 */
fun <T : Any> TypedDestination<T>.argsOrNull(entry: BackStackEntry, json: Json): T? =
    entry.args?.let { json.decodeFromString(argsSerializer, it) }

/**
 * Decodes args from a [BackStackEntry] for this typed destination.
 *
 * Throws [IllegalStateException] if no args are attached. Prefer this when the
 * destination contractually requires arguments (the common case).
 */
fun <T : Any> TypedDestination<T>.argsFrom(entry: BackStackEntry, json: Json): T =
    argsOrNull(entry, json)
        ?: error("Missing typed args for destination '$id'. Did you forget to pass them when navigating?")

/**
 * Builds a [BackStackEntry] for this typed destination from typed [args].
 *
 * Use this when constructing a back stack manually (e.g., for deep links
 * or initial state). For ordinary navigation prefer [NavController.navigateTo].
 */
fun <T : Any> TypedDestination<T>.toBackStackEntry(
    args: T,
    json: Json,
    scopeId: NavigationScopeId = defaultScope(),
    pendingResultKey: String? = null,
): BackStackEntry =
    BackStackEntry(
        destinationId = id,
        args = encodeArgs(args, json),
        scopeId = scopeId,
        pendingResultKey = pendingResultKey,
    )

// ----------------------------------------------------------------------------
// Helpers — NavController-side
// ----------------------------------------------------------------------------

/**
 * Navigates to a [TypedDestination] with strongly-typed [args].
 *
 * Equivalent to building a [BackStackEntry] via [TypedDestination.toBackStackEntry]
 * and dispatching [NavController.navigate], but in one call. The controller's
 * [NavController.json] is used to encode the args.
 *
 * @param destination The typed destination to navigate to.
 * @param args Typed arguments for the destination.
 * @param scopeId Optional scope override. Defaults to the destination's [defaultScope].
 * @param pendingResultKey Optional key for [NavigationResult] return when this
 * entry is later popped.
 * @param clearBackStack Whether to clear the back stack before navigating.
 * @param popUpTo Optional destination ID to pop up to before navigating.
 * @param popUpToInclusive Whether to also remove [popUpTo] itself.
 * @param reuseIfExists Replace an existing matching entry rather than pushing a new one.
 */
fun <T : Any> NavController.navigateTo(
    destination: TypedDestination<T>,
    args: T,
    scopeId: NavigationScopeId = destination.defaultScope(),
    pendingResultKey: String? = null,
    clearBackStack: Boolean = false,
    popUpTo: String? = null,
    popUpToInclusive: Boolean = false,
    reuseIfExists: Boolean = false,
) {
    val entry = destination.toBackStackEntry(
        args = args,
        json = json,
        scopeId = scopeId,
        pendingResultKey = pendingResultKey,
    )
    navigate(
        entry = entry,
        clearBackStack = clearBackStack,
        popUpTo = popUpTo,
        popUpToInclusive = popUpToInclusive,
        reuseIfExists = reuseIfExists,
    )
}

/**
 * Replaces the entire back stack with a single [TypedDestination] entry, with
 * strongly-typed [args].
 */
fun <T : Any> NavController.replaceRootTo(
    destination: TypedDestination<T>,
    args: T,
    scopeId: NavigationScopeId = destination.defaultScope(),
) {
    replaceRoot(
        destination.toBackStackEntry(
            args = args,
            json = json,
            scopeId = scopeId,
        )
    )
}

/**
 * Reads typed args for [destination] from a [BackStackEntry] (defaults to the
 * controller's [NavController.currentEntry]). Throws if the entry has no args.
 *
 * Common usage inside a screen:
 *
 * ```
 * val args = navController.requireArgs(ProductDetailsDestination, entry)
 * ```
 */
fun <T : Any> NavController.requireArgs(
    destination: TypedDestination<T>,
    entry: BackStackEntry = currentEntry,
): T = destination.argsFrom(entry, json)

/**
 * Reads typed args for [destination] from a [BackStackEntry], or `null` if
 * none are attached.
 */
fun <T : Any> NavController.argsOrNull(
    destination: TypedDestination<T>,
    entry: BackStackEntry = currentEntry,
): T? = destination.argsOrNull(entry, json)

/**
 * Encodes typed [args] for [destination] into the opaque [ArgsJson] string
 * using this controller's [Json] instance.
 *
 * Use this when external code needs a raw encoded string — e.g., when a
 * custom [DeepLinkHandler] or test fixture builds [NavigationCommand]s by
 * hand. Prefer [navigateTo] / [replaceRootTo] for ordinary navigation.
 */
fun <T : Any> NavController.encodeArgs(
    destination: TypedDestination<T>,
    args: T,
): ArgsJson = destination.encodeArgs(args, json)

/**
 * Builds a [BackStackEntry] for [destination] with typed [args], using this
 * controller's [Json] for encoding.
 *
 * Useful when constructing entries for a [NavigationCommand] list that will
 * be dispatched later (e.g., from a custom [DeepLinkHandler]) without
 * exposing the controller's internal [Json] to call sites.
 *
 * @param destination The typed destination this entry targets.
 * @param args Typed arguments for the destination.
 * @param scopeId Optional scope override. Defaults to the destination's [defaultScope].
 * @param pendingResultKey Optional key for [NavigationResult] return when this
 * entry is later popped.
 */
fun <T : Any> NavController.toBackStackEntry(
    destination: TypedDestination<T>,
    args: T,
    scopeId: NavigationScopeId = destination.defaultScope(),
    pendingResultKey: String? = null,
): BackStackEntry = destination.toBackStackEntry(
    args = args,
    json = json,
    scopeId = scopeId,
    pendingResultKey = pendingResultKey,
)
