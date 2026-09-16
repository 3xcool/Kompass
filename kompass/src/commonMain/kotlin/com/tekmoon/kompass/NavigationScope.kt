package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tekmoon.kompass.util.randomUUID
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmInline

/**
 * ViewModel is created once
 *
 * Survives recomposition
 *
 * Destroyed when popped
 *
 * Scoped instances survive while their scope remains in memory. Navigation state and entry
 * SavedStateHandles have separate serialization; arbitrary instances are not serialized.
 */
@Serializable
@JvmInline
value class NavigationScopeId(val value: String)

/**
 * Creates a default scope ID for a destination.
 *
 * Use this when the same destination instance should share state
 * across multiple navigation. For example, navigating to ProfileScreen
 * multiple times will reuse the same ViewModel.
 *
 * Example:
 * ```
 * val entry = KompassEntry(
 *     destinationId = "profile",
 *     scopeId = Destination.defaultScope()  // Reuses same scope
 * )
 * ```
 *
 * @return Scope ID based on destination: "entry:{destinationId}"
 * @see newScope for isolated state variant
 */
fun Destination.defaultScope(): NavigationScopeId =
    NavigationScopeId("entry:${id}")

/**
 * Creates a unique scope ID for a destination.
 *
 * Use this when you need isolated state for multiple instances of the
 * same destination. For example, opening Profile(userId=1) and Profile(userId=2)
 * should maintain separate ViewModels.
 *
 * Example:
 * ```
 * val entry = KompassEntry(
 *     destinationId = "profile",
 *     args = """{"userId":"123"}""",
 *     scopeId = newScope()  // Creates unique scope
 * )
 * ```
 *
 * @return Unique scope ID: "entry:{randomUUID()}"
 * @see defaultScope for shared state variant
 */
fun newScope(): NavigationScopeId =
    NavigationScopeId("entry:${randomUUID()}")

/**
To avoid two DsNavigation Profile A and Profile B with same scope, use newScope or use args as id suffix
DsNavigationCommand.Navigate(
DsBackStackEntry(
destinationId = "main/profile",
args = """{"userId":"A"}""",
scopeId = newScope()
)
)
profile(A) and profile(B) coexist
state is isolated
pop clears only the correct instance
 */

class NavigationScope(
    val id: NavigationScopeId
) {
    private val instances = mutableMapOf<String, Any>()
    private val cleaners = mutableListOf<() -> Unit>()
    private val registeredCleaners = mutableSetOf<String>()  // Track registered keys

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreate(
        key: String,
        factory: () -> T,
        onCleared: (T) -> Unit
    ): T {
        val instance = instances.getOrPut(key) {
            factory()
        } as T

        // Register cleanup once per key, not per instance reference
        if (key !in registeredCleaners) {
            cleaners += { onCleared(instance) }
            registeredCleaners += key
        }

        return instance
    }

    fun clear() {
        cleaners.forEach { it() }
        cleaners.clear()
        registeredCleaners.clear()
        instances.clear()
    }
}

/**
 * Process-wide manager for explicitly shared navigation scopes.
 *
 * Access and cleanup must occur on the UI thread. Volatile publication of the map does not
 * make compound map operations or scoped objects thread-safe. Automatic navigation cleanup
 * waits for outgoing content; explicit clearScope/clearAll remain immediate overrides.
 */
data object NavigationScopes {

    // Volatile to ensure visibility across threads
    @Volatile
    private var scopes = mutableMapOf<NavigationScopeId, NavigationScope>()

    /**
     * Get or create a scope from Composable or other UI-thread code.
     *
     * @param scopeId The scope ID
     * @return The navigation scope, creating it if necessary
     */
    fun getScope(scopeId: NavigationScopeId): NavigationScope {
        // Fast path: try to get without locking
        val existing = scopes[scopeId]
        if (existing != null) {
            return existing
        }

        // Slow path: create and update atomically
        val newScope = NavigationScope(scopeId)
        val updated = scopes.toMutableMap()

        // Check again in case another thread created it
        if (scopeId in updated) {
            return updated[scopeId]!!
        }

        updated[scopeId] = newScope
        scopes = updated  // Atomic assignment via @Volatile

        return scopes[scopeId]!!
    }

    private val renderCounts = mutableMapOf<NavigationScopeId, Int>()
    private val pendingClear = mutableSetOf<NavigationScopeId>()

    internal fun retain(scopeId: NavigationScopeId) {
        renderCounts[scopeId] = (renderCounts[scopeId] ?: 0) + 1
    }

    internal fun release(scopeId: NavigationScopeId) {
        val count = checkNotNull(renderCounts[scopeId]) - 1
        if (count == 0) {
            renderCounts.remove(scopeId)
            if (pendingClear.remove(scopeId)) clearScope(scopeId)
        } else renderCounts[scopeId] = count
    }

    internal fun cancelClear(scopeId: NavigationScopeId) {
        pendingClear.remove(scopeId)
    }

    internal fun requestClear(scopeId: NavigationScopeId) {
        if ((renderCounts[scopeId] ?: 0) > 0) pendingClear.add(scopeId)
        else clearScope(scopeId)
    }

    /**
     * Clear a scope immediately, on the UI thread.
     *
     * Called when back stack entries are removed.
     *
     * @param scopeId The scope ID to clear
     */
    fun clearScope(scopeId: NavigationScopeId) {
        pendingClear.remove(scopeId)
        val updated = scopes.toMutableMap()
        val removed = updated.remove(scopeId)
        scopes = updated
        removed?.clear()
    }

    /**
     * Clear all scopes (for testing/reset).
     */
    fun clearAll() {
        val toClean = scopes.toMap()
        toClean.values.forEach { it.clear() }
        scopes = mutableMapOf()
    }
}


/**
 * Retrieves or creates a scoped instance (similar to ViewModel).
 *
 * The instance is created once per scope and survives recomposition.
 *
 * ## Two lifetimes, and you pick one with [scopeId]
 *
 * **Automatic — the scope belongs to entries.** Pass `entry.scopeId`, [Destination.defaultScope] or
 * [newScope], or any ID that some [KompassEntry] carries. Kompass clears the scope once, after the
 * last entry using it leaves the back stack and its outgoing content is disposed. Nothing to write:
 *
 * ```
 * val viewModel = rememberScoped<ProfileViewModel>(
 *     scopeId = entry.scopeId,
 *     factory = { ProfileViewModel(userId) },
 *     onCleared = { it.close() }
 * )
 * ```
 *
 * **Manual — the scope belongs to you.** Pass an ID no entry carries, to share one object across a
 * flow of different destinations. Kompass never clears it, because there is no entry whose removal
 * could mean "the flow ended". It is a process-wide singleton until you say otherwise, so give it
 * an owner:
 *
 * ```
 * private val CheckoutScope = NavigationScopeId("flow:checkout")
 *
 * DisposableEffect(CheckoutScope) {
 *     onDispose { NavigationScopes.clearScope(CheckoutScope) }
 * }
 * ```
 *
 * Prefer the automatic form. Reach for the manual one only when the flow has no single entry that
 * outlives the others, and remember that the object survives the whole process without that
 * [NavigationScopes.clearScope].
 *
 * @param T Instance type
 * @param scopeId The scope to store the instance in. It decides which lifetime above applies.
 * @param key Unique key for the instance (defaults to class qualified name)
 * @param factory Function to create the instance if it doesn't exist
 * @param onCleared Called when the scope is cleared
 *
 * @return Scoped instance that survives recomposition
 *
 * Threading: call from composition on the UI thread.
 */
@Composable
inline fun <reified T : Any> rememberScoped(
    scopeId: NavigationScopeId,
    key: String = T::class.qualifiedName ?: "anonymous",
    noinline onCleared: (T) -> Unit = {},
    noinline factory: () -> T
): T {
    val scope = remember(scopeId) {
        NavigationScopes.getScope(scopeId)
    }

    return remember(scopeId, key) {
        scope.getOrCreate(key, factory, onCleared)
    }
}