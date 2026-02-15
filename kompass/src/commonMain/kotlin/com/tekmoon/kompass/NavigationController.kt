package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Central navigation API exposed to consumers of the Kompass navigation system.
 *
 * [NavController] acts as a thin, state-aware facade over:
 * - [NavigationState], which represents the current back stack
 * - [NavigationHandler], which applies [NavigationCommand]s through a reducer
 *
 * This class is responsible for:
 * - Dispatching navigation commands
 * - Updating navigation state
 * - Cleaning up navigation scopes when entries are removed
 * - Coordinating deep link application
 *
 * [NavController] itself does not contain navigation rules.
 * All rules are delegated to the reducer to ensure consistency,
 * testability, and predictability.
 *
 * Instances of [NavController] are expected to be created via
 * [rememberNavController] and retained across recompositions.
 *
 * @param navState Mutable holder of the current [NavigationState].
 * This state is internally updated when navigation commands are dispatched.
 *
 * @param handler Reducer responsible for applying [NavigationCommand]s
 * and producing new [NavigationState] instances.
 *
 * @param deepLinkHandlers List of [DeepLinkHandler]s used to resolve
 * deep link URIs applied at runtime.
 */
@Stable
class NavController internal constructor(
    private val navState: MutableState<NavigationState>,
    private val handler: NavigationHandler,
    private val deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf()
) {

    /**
     * The current immutable [NavigationState].
     *
     * This state should be treated as read-only by consumers.
     * All mutations must go through navigation commands.
     */
    val state: NavigationState
        get() = navState.value

    /**
     * The current top-most [BackStackEntry].
     *
     * This represents the active destination.
     */
    val currentEntry: BackStackEntry
        get() = state.backStack.last()

    /**
     * A snapshot of the current back stack.
     *
     * The last element represents the active destination.
     */
    val backStack: List<BackStackEntry>
        get() = state.backStack

    /**
     * Dispatches a single [NavigationCommand] to the reducer.
     *
     * This method:
     * - Applies the command to the current state
     * - Computes which navigation scopes were removed
     * - Clears any scopes that are no longer present
     * - Updates the stored navigation state
     *
     * Scope cleanup is handled automatically to avoid leaking
     * scoped resources such as ViewModels.
     *
     * @param command The navigation command to be reduced
     * into a new [NavigationState].
     */
    private fun dispatch(command: NavigationCommand) {
        val oldState = navState.value
        val newState = handler.reduce(oldState, command)

        val oldScopes = oldState.backStack.map { it.scopeId }.toSet()
        val newScopes = newState.backStack.map { it.scopeId }.toSet()
        (oldScopes - newScopes).forEach(NavigationScopes::clearScope)

        navState.value = newState
    }

    /**
     * Navigates to the given [BackStackEntry].
     *
     * This method is a convenience wrapper over [NavigationCommand.Navigate].
     *
     * @param entry The destination entry to navigate to.
     *
     * @param clearBackStack Whether the entire back stack should be
     * cleared before navigating to the new entry.
     *
     * @param popUpTo Optional destination ID indicating where the
     * back stack should be popped up to before navigation.
     *
     * @param popUpToInclusive Whether the destination specified by
     * [popUpTo] should also be removed from the back stack.
     *
     * @param reuseIfExists Whether an existing matching entry in the
     * back stack should be reused instead of creating a new one.
     */
    fun navigate(
        entry: BackStackEntry,
        clearBackStack: Boolean = false,
        popUpTo: String? = null,
        popUpToInclusive: Boolean = false,
        reuseIfExists: Boolean = false
    ) {
        dispatch(
            NavigationCommand.Navigate(
                entry,
                clearBackStack,
                popUpTo,
                popUpToInclusive,
                reuseIfExists
            )
        )
    }

    /**
     * Pops one or more entries from the back stack.
     *
     * This method is a convenience wrapper over [NavigationCommand.Pop].
     *
     * @param result Optional [NavigationResult] to be delivered
     * to the previous back stack entry.
     *
     * @param count Number of entries to pop from the back stack.
     *
     * @param popUntil Optional destination ID indicating the back
     * stack should be popped until that destination is reached.
     */
    fun pop(
        result: NavigationResult? = null,
        count: Int = 1,
        popUntil: String? = null
    ) {
        dispatch(
            NavigationCommand.Pop(result, count, popUntil)
        )
    }

    /**
     * Replaces the entire back stack with a single root entry.
     *
     * @param entry The new root [BackStackEntry] that will become
     * the only entry in the back stack.
     */
    fun replaceRoot(entry: BackStackEntry) {
        dispatch(NavigationCommand.ReplaceRoot(entry))
    }

    /**
     * Executes a list of [NavigationCommand]s sequentially.
     *
     * @param commands List of navigation commands to be applied
     * in the order they appear.
     */
    fun runNavCommands(commands: List<NavigationCommand>) {
        commands.forEach { dispatch(it) }
    }

    /**
     * Attempts to apply a deep link URI to the current navigation state.
     *
     * @param uri The deep link URI to resolve and apply.
     *
     * @param deepLinkHandlers Optional list of [DeepLinkHandler]s
     * used to resolve the URI. If null, the handlers associated
     * with this controller are used.
     *
     * @return true if the URI was successfully resolved and applied,
     * false if no handler could resolve the URI.
     */
    fun applyDeepLink(uri: String, deepLinkHandlers: List<DeepLinkHandler>? = null): Boolean {
        val manager = DeepLinkManager(deepLinkHandlers ?: this.deepLinkHandlers)
        val commands = manager.resolve(uri)

        if (commands.isNullOrEmpty()) return false

        commands.forEach { command ->
            dispatch(command)
        }

        return true
    }

    /**
     * Pops the back stack if possible.
     *
     * @param onFailure Callback invoked if the back stack cannot
     * be popped, typically when the current entry is the root.
     */
    fun popIfCan(onFailure: () -> Unit = {}) {
        if (canGoBack()) {
            dispatch(NavigationCommand.Pop())
        } else {
            onFailure()
        }
    }

    /**
     * Returns true if the navigation stack can be popped.
     */
    fun canGoBack(): Boolean = state.canGoBack()
}


/**
 * Remembers a [NavController] using a pre-built [NavigationState].
 *
 * @param initialState The initial navigation state used as the
 * starting point for this controller.
 *
 * @param serializersModule Optional [SerializersModule] used to
 * register custom serializers for destinations and arguments.
 *
 * @param deepLinkUri Optional deep link URI that will be resolved
 * and applied during initialization.
 *
 * @param deepLinkHandlers List of [DeepLinkHandler]s used to resolve
 * deep link URIs.
 */
@Composable
fun rememberNavController(
    initialState: NavigationState,
    serializersModule: SerializersModule = SerializersModule {},
    deepLinkUri: String? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf()
): NavController {
    val handler = remember { NavigationHandler() }
    val json = rememberNavigationJson(serializersModule)
    val deepLinkManager = remember(deepLinkHandlers) {
        DeepLinkManager(deepLinkHandlers)
    }

    val resolvedInitialState = remember(deepLinkUri, initialState) {
        if (deepLinkUri == null) {
            initialState
        } else {
            deepLinkManager
                .resolve(deepLinkUri)
                ?.let { commands ->
                    applyDeepLink(initialState, commands, handler)
                }
                ?: initialState
        }
    }

    val navigationState = rememberSaveable(
        saver = remember(json) {
            navigationStateSaver(json, BackStackEntry.serializer())
        }
    ) {
        mutableStateOf(resolvedInitialState)
    }

    return remember {
        NavController(
            navState = navigationState,
            handler = handler,
            deepLinkHandlers = deepLinkHandlers
        )
    }
}

/**
 * Remembers a [NavController] starting from a single root destination.
 *
 * @param startDestination The root [Destination] used to create
 * the initial back stack entry.
 *
 * @param serializersModule Optional [SerializersModule] used to
 * register custom serializers.
 *
 * @param scopeId Optional [NavigationScopeId] used to override the
 * default scope of the start destination.
 *
 * @param deepLinkUri Optional deep link URI that may override
 * the start destination.
 *
 * @param deepLinkHandlers List of [DeepLinkHandler]s used to resolve
 * deep link URIs.
 */
@Composable
fun rememberNavController(
    startDestination: Destination,
    serializersModule: SerializersModule = SerializersModule {},
    scopeId: NavigationScopeId? = null,
    deepLinkUri: String? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf()
): NavController {
    val handler = remember { NavigationHandler() }
    val json = rememberNavigationJson(serializersModule)
    val deepLinkManager = remember(deepLinkHandlers) {
        DeepLinkManager(deepLinkHandlers)
    }

    val startEntry = remember(startDestination) {
        BackStackEntry(
            destinationId = startDestination.id,
            scopeId = scopeId ?: startDestination.defaultScope()
        )
    }

    val baseState = remember {
        defaultNavigationState(startEntry)
    }

    val initialState = remember(deepLinkUri) {
        if (deepLinkUri == null) {
            baseState
        } else {
            deepLinkManager
                .resolve(deepLinkUri)
                ?.let { commands ->
                    applyDeepLink(baseState, commands, handler)
                }
                ?: baseState
        }
    }

    val navigationState = rememberSaveable(
        saver = remember(json) {
            navigationStateSaver(json, BackStackEntry.serializer())
        }
    ) {
        mutableStateOf(initialState)
    }

    return remember {
        NavController(
            navState = navigationState,
            handler = handler,
            deepLinkHandlers = deepLinkHandlers
        )
    }
}

/**
 * Creates and remembers a configured [Json] instance used for
 * serializing and restoring [NavigationState].
 *
 * @param serializersModule Module providing serializers required
 * to encode and decode navigation-related types.
 */
@Composable
private fun rememberNavigationJson(
    serializersModule: SerializersModule
): Json {
    return remember(serializersModule) {
        Json {
            ignoreUnknownKeys = true
            classDiscriminator = "_type"
            this.serializersModule = serializersModule
        }
    }
}

/**
 * A [Saver] responsible for serializing and restoring [NavigationState].
 *
 * @param json Configured [Json] instance used for encoding and decoding.
 *
 * @param backStackEntrySerializer Serializer used to encode and decode
 * individual [BackStackEntry] instances.
 */
private fun navigationStateSaver(
    json: Json,
    backStackEntrySerializer: KSerializer<BackStackEntry>
): Saver<MutableState<NavigationState>, String> =
    Saver(
        save = { state ->
            json.encodeToString(
                NavigationState.serializer(backStackEntrySerializer),
                state.value
            )
        },
        restore = { saved ->
            mutableStateOf(
                json.decodeFromString(
                    NavigationState.serializer(backStackEntrySerializer),
                    saved
                )
            )
        }
    )
