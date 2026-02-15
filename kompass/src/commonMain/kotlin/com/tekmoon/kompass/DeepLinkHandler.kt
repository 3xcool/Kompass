package com.tekmoon.kompass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName


/**
 * See Sample 5 for usage
 *
 * Platform delivers a URI -> DeepLinkRegistry converts it to commands -> Navigation reducer applies them
 *
 * A [DeepLinkHandler] is responsible for declaring whether it can handle a given URI
 * and translating that URI into a list of [NavigationCommand]s.
 *
 * This interface is intentionally simple and synchronous:
 * - It does not perform navigation directly
 * - It does not mutate navigation state
 * - It does not depend on platform-specific APIs
 *
 * The output of a [DeepLinkHandler] is later applied by the navigation reducer,
 * ensuring that deep links follow the same rules and invariants as normal navigation.
 *
 * Typical usage patterns:
 * - Feature modules define their own [DeepLinkHandler] implementations
 * - Each handler is responsible for a specific URI pattern or feature area
 * - The first matching handler is used to resolve the deep link
 */
interface DeepLinkHandler {

    /**
     * Whether this deep link can handle the given uri
     *
     * This method should be:
     * - Fast and side-effect free
     * - Deterministic for the same input
     *
     * Implementations should avoid performing any heavy parsing or allocation here.
     * Its primary purpose is to allow the [DeepLinkManager] to select the appropriate
     * handler for a given URI.
     *
     * Returning true indicates that this handler is capable of resolving the URI.
     */
    fun matches(uri: String): Boolean

    /**
     * Resolve the uri into navigation commands.
     *
     * Example:
     * - ReplaceRoot(Home)
     * - Navigate(Profile(userId))
     *
     * This method is only called if [matches] returned true for the same URI.
     *
     * The returned list of [NavigationCommand]s will be applied sequentially
     * to the current [NavigationState] using the navigation reducer.
     *
     * Guidelines for implementations:
     * - The returned commands should represent the minimal navigation steps
     *   required to reach the desired destination
     * - Commands should not assume any existing back stack state
     * - Commands should be pure data, without triggering side effects
     *
     * Returning an empty list indicates that the URI was recognized but does not
     * require any navigation changes.
     */
    fun resolve(uri: String): List<NavigationCommand>
}

/**
 * Coordinates the resolution of deep links by delegating to registered [DeepLinkHandler]s.
 *
 * The [DeepLinkManager] follows a first-match-wins strategy:
 * - Handlers are evaluated in the order they are provided
 * - The first handler whose [DeepLinkHandler.matches] returns true is used
 * - No further handlers are consulted after a match is found
 *
 * This class does not apply navigation commands itself.
 * It is only responsible for selecting a handler and resolving commands.
 */
class DeepLinkManager(
    private val handlers: List<DeepLinkHandler>
) {

    /**
     * Attempts to resolve the given URI into a list of [NavigationCommand]s.
     *
     * Returns:
     * - A list of commands if a matching handler is found
     * - null if no handler can handle the URI
     *
     * Callers are expected to apply the returned commands using the navigation reducer.
     */
    fun resolve(uri: String): List<NavigationCommand>? =
        handlers.firstOrNull { it.matches(uri) }
            ?.resolve(uri)
}

/**
 * Applies a list of [NavigationCommand]s to an existing [NavigationState].
 *
 * Commands are applied sequentially in the order they appear in the list.
 * Each command produces a new [NavigationState], which is passed as input
 * to the next command.
 *
 * This function ensures that deep link navigation uses the same reducer logic
 * as all other navigation flows, preserving consistency and invariants.
 */
fun applyDeepLink(
    state: NavigationState,
    commands: List<NavigationCommand>,
    handler: NavigationHandler
): NavigationState =
    commands.fold(state) { navState, command ->
        handler.reduce(navState, command)
    }

/**
 * A platform-facing channel used to deliver deep link URIs into the navigation system.
 *
 * This class acts as a bridge between platform-specific deep link sources
 * (Android intents, iOS scene callbacks, Desktop URI handlers)
 * and the shared navigation logic.
 *
 * Internally, it uses a buffered [Channel] to avoid dropping events when
 * there are no active observers.
 *
 * This type intentionally exposes a callback-based subscription API
 * to remain Swift-friendly on Apple platforms.
 */
class DeepLinkChannel {

    private val channel = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Sends a deep link URI into the channel.
     *
     * This method is safe to call from platform-specific code.
     * If no observers are currently collecting, the URI will be buffered.
     */
    public fun send(uri: String) {
        channel.trySend(uri)
    }

    /**
     * Swift-friendly subscription API
     *
     * Registers a callback that will be invoked for each received URI.
     *
     * The callback is invoked on the Main dispatcher.
     * Consumers are expected to forward the URI to a [DeepLinkManager]
     * and apply the resulting navigation commands.
     *
     * This method is designed for interoperability with Swift,
     * where collecting Kotlin Flows directly is not ergonomic.
     */
    public fun observe(onEvent: (String) -> Unit) {
        scope.launch {
            channel.receiveAsFlow().collect { uri ->
                onEvent(uri)
            }
        }
    }
}
