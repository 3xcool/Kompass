package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Use [rememberKompassNavController] in composition or [createKompassNavController] for external ownership.
 * Mutation, saving and disposal are confined to the UI thread. StateFlow observation may
 * occur on any dispatcher.
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
    /**
     * The [Json] instance used by Kompass for state restoration and for typed
     * argument encoding/decoding (see [TypedDestination] and [navigateTo]).
     *
     * `internal` so the typed-args extensions defined in this module can use it,
     * while preventing external consumers from poking at the raw [Json].
     * Configured with the [SerializersModule] passed to [rememberKompassNavController].
     *
     * If a consumer ever needs raw encode/decode, the right pattern is a public
     * extension on [NavController] that wraps the encoding — not direct access
     * to this property.
     */
    internal val json: Json,
    private val deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf(),
    internal val entryOwners: KompassOwnerStore = KompassOwnerStore(),
    val restorationFailure: Throwable? = null,
) {
    private val observedState = MutableStateFlow(navState.value)
    /** Read-only, conflated state observation; this is not a queue of navigation events. */
    val stateFlow: StateFlow<NavigationState> = observedState.asStateFlow()

    /** Direction of the last command that changed the active occurrence. */
    private var directionState by mutableStateOf(NavDirection.Push)
    val direction: NavDirection
        get() = directionState

    /**
     * Visual state of an unfinished Back gesture.
     *
     * This is UI state, not navigation state. It never enters [NavigationState] and
     * [saveNavigationState] never writes it. Feed it with [KompassPredictiveBackHandler] and render
     * it with [SceneLayoutPredictive].
     */
    val predictiveBack: PredictiveBackState = PredictiveBackState()

    init {
        navState.value.requireValid()
        entryOwners.reconcile(navState.value.backStack)
    }

    /** Save navigation payloads. Register NavigationResult subtypes in serializersModule. */
    fun saveNavigationState(): String = json.encodeToString(NavigationState.serializer(BackStackEntry.serializer()), state)

    /** Release an externally owned controller. Idempotent; do not call for a temporary host unmount. */
    fun close() {
        if (entryOwners.isClosed) return
        entryOwners.close()
        state.backStack.map { it.scopeId }.toSet().forEach(NavigationScopes::requestClear)
    }

    /**
     * Returns and removes a matching result once. Missing entry/key or wrong type returns null
     * without removing anything. Pass entryId when consuming from a covered occurrence.
     * Call from an event handler or effect, never while rendering composition.
     */
    inline fun <reified T : NavigationResult> consumeResult(key: String, entryId: String = currentEntry.id): T? =
        consumeResultMatching(key, entryId) { it is T } as? T

    @PublishedApi
    internal fun consumeResultMatching(key: String, entryId: String, matches: (NavigationResult) -> Boolean): NavigationResult? {
        check(!entryOwners.isClosed) { "The navigation controller has been closed" }
        val result = backStack.firstOrNull { it.id == entryId }?.results?.get(key) ?: return null
        if (!matches(result)) return null
        dispatch(NavigationCommand.ConsumeResult(entryId, key))
        return result
    }


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
     *
     * This is an [ImmutableList], the same type [NavigationState] holds. Compose reads it as a
     * stable parameter, so a composable that takes the back stack can still skip recomposition.
     */
    val backStack: ImmutableList<BackStackEntry>
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
        check(!entryOwners.isClosed) { "The navigation controller has been closed" }
        val oldState = navState.value
        val normalized = command.withDistinctOccurrences(oldState.backStack)
        val newState = handler.reduce(oldState, normalized)
        newState.requireValid()
        if (newState == oldState) return
        if (newState.backStack.last().id != oldState.backStack.last().id) {
            directionState = if (command is NavigationCommand.Pop) NavDirection.Pop else NavDirection.Push
        }

        val oldScopes = oldState.backStack.map { it.scopeId }.toSet()
        val newScopes = newState.backStack.map { it.scopeId }.toSet()
        navState.value = newState
        entryOwners.reconcile(newState.backStack)
        newScopes.forEach(NavigationScopes::cancelClear)
        (oldScopes - newScopes).forEach(NavigationScopes::requestClear)
        observedState.value = newState
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
     * back stack should be moved to the top with updated arguments. An unchanged scope
     * preserves entry ownership and UI state; a different scope requests fresh ownership.
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
    @Deprecated(
        message = "Use replaceStack, which applies one entry or a whole stack.",
        replaceWith = ReplaceWith("replaceStack(entry)"),
    )
    fun replaceRoot(entry: BackStackEntry) {
        replaceStack(entry)
    }

    /**
     * Replaces the entire back stack with the given entries, in one state change.
     *
     * Use this to apply a stack that arrives whole: a server payload, a multi-level deep link, or a
     * session restored from [saveNavigationState]. Applying the same stack as several [navigate]
     * calls publishes every intermediate state and plays one animation per step.
     *
     * A repeated entry object becomes a separate occurrence, the same as it does for [navigate].
     *
     * @param entries The new back stack, from root to top. It must not be empty.
     */
    fun replaceStack(entries: List<BackStackEntry>) {
        dispatch(NavigationCommand.ReplaceStack(entries))
    }

    /**
     * Replaces the entire back stack with a single entry.
     *
     * @param entry The entry that becomes the only entry in the back stack.
     */
    fun replaceStack(entry: BackStackEntry) {
        replaceStack(listOf(entry))
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


/** Recovery for invalid saved navigation. Initial state must itself be valid. */
enum class NavigationRestorePolicy { UseInitialState, Throw }

private class NavigationRestoration(val state: MutableState<NavigationState>, val failure: Throwable? = null)

private fun navigationJson(serializersModule: SerializersModule): Json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "_type"
    this.serializersModule = serializersModule
}

private fun restoreNavigation(
    saved: String?,
    initialState: NavigationState,
    json: Json,
    policy: NavigationRestorePolicy,
): NavigationRestoration {
    initialState.requireValid()
    if (saved == null) return NavigationRestoration(mutableStateOf(initialState))
    return try {
        NavigationRestoration(mutableStateOf(json.decodeFromString(NavigationState.serializer(BackStackEntry.serializer()), saved)))
    } catch (cause: Exception) {
        if (cause is CancellationException || policy == NavigationRestorePolicy.Throw) throw cause
        NavigationRestoration(mutableStateOf(initialState), cause)
    }
}

/**
 * Create a controller without composition. Its owner must call close() on the UI thread.
 * savedNavigationState restores navigation only; it does not serialize live ViewModels or UI.
 * Recovery reports the failure and uses initialState unless Throw is selected.
 */
fun createKompassNavController(
    initialState: NavigationState,
    serializersModule: SerializersModule = SerializersModule {},
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf(),
    savedNavigationState: String? = null,
    restorePolicy: NavigationRestorePolicy = NavigationRestorePolicy.UseInitialState,
    onRestoreFailure: (Throwable) -> Unit = {},
): NavController {
    val json = navigationJson(serializersModule)
    val restored = restoreNavigation(savedNavigationState, initialState, json, restorePolicy)
    restored.failure?.let(onRestoreFailure)
    return NavController(restored.state, NavigationHandler(), json, deepLinkHandlers, restorationFailure = restored.failure)
}

/** External-ownership convenience overload starting at a destination. */
fun createKompassNavController(
    startDestination: Destination,
    serializersModule: SerializersModule = SerializersModule {},
    scopeId: NavigationScopeId? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf(),
    savedNavigationState: String? = null,
    restorePolicy: NavigationRestorePolicy = NavigationRestorePolicy.UseInitialState,
    onRestoreFailure: (Throwable) -> Unit = {},
): NavController = createKompassNavController(
    defaultNavigationState(startDestination.toKompassBackStackEntry(scopeId = scopeId ?: startDestination.defaultScope())),
    serializersModule, deepLinkHandlers, savedNavigationState, restorePolicy, onRestoreFailure,
)

/** Remember a controller with automatic owner retention and saved navigation recovery. */
@Composable
fun rememberKompassNavController(
    initialState: NavigationState,
    serializersModule: SerializersModule = SerializersModule {},
    deepLinkUri: String? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf(),
    restorePolicy: NavigationRestorePolicy = NavigationRestorePolicy.UseInitialState,
    onRestoreFailure: (Throwable) -> Unit = {},
): NavController {
    val json = remember(serializersModule) { navigationJson(serializersModule) }
    val initial = remember {
        initialState.requireValid()
        val commands = deepLinkUri?.let { DeepLinkManager(deepLinkHandlers).resolve(it) }
        if (commands == null) initialState else applyDeepLink(initialState, commands, NavigationHandler()).also { it.requireValid() }
    }
    val restored = rememberSaveable(saver = remember(json, initial, restorePolicy) {
        Saver<NavigationRestoration, String>(
            save = { json.encodeToString(NavigationState.serializer(BackStackEntry.serializer()), it.state.value) },
            restore = { restoreNavigation(it, initial, json, restorePolicy) },
        )
    }) { NavigationRestoration(mutableStateOf(initial)) }
    val reportFailure by rememberUpdatedState(onRestoreFailure)
    LaunchedEffect(restored) { restored.failure?.let(reportFailure) }
    val owners = rememberKompassOwnerStore(restored.state.value.backStack)
    return remember(owners) {
        NavController(restored.state, NavigationHandler(), json, deepLinkHandlers, owners, restored.failure)
    }
}

/** Remember a controller starting at one destination. */
@Composable
fun rememberKompassNavController(
    startDestination: Destination,
    serializersModule: SerializersModule = SerializersModule {},
    scopeId: NavigationScopeId? = null,
    deepLinkUri: String? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf(),
    restorePolicy: NavigationRestorePolicy = NavigationRestorePolicy.UseInitialState,
    onRestoreFailure: (Throwable) -> Unit = {},
): NavController {
    val initial = remember { defaultNavigationState(startDestination.toKompassBackStackEntry(scopeId = scopeId ?: startDestination.defaultScope())) }
    return rememberKompassNavController(initial, serializersModule, deepLinkUri, deepLinkHandlers, restorePolicy, onRestoreFailure)
}

/**
 * Gives every repeated entry its own occurrence ID.
 *
 * A stack that arrives whole may hold the same entry object at two levels. Each level is a separate
 * occurrence with its own owner and its own UI state, so a repeat needs a new ID. The first
 * appearance keeps its ID, which lets a caller rebuild a stack from entries it already holds.
 */
private fun distinctOccurrences(entries: List<BackStackEntry>): List<BackStackEntry> {
    val seen = mutableSetOf<String>()
    return entries.map { entry ->
        if (seen.add(entry.id)) entry
        else entry.newOccurrence().also { seen.add(it.id) }
    }
}

/** Normalize at both controller dispatch and initial deep-link boundaries; keep the reducer pure. */
internal fun NavigationCommand.withDistinctOccurrences(backStack: List<BackStackEntry>): NavigationCommand = when {
    this is NavigationCommand.Navigate && !reuseIfExists && backStack.any { it.id == entry.id } ->
        copy(entry = entry.newOccurrence())
    this is NavigationCommand.ReplaceStack -> copy(entries = distinctOccurrences(entries))
    else -> this
}
