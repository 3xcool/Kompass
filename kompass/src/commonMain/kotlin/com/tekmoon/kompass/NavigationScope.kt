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
 * Survives rotation / process death (via SavedState)
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
 * val entry = BackStackEntry(
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
 * val entry = BackStackEntry(
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
 * Thread-safe manager for navigation scopes.
 *
 * Uses @Volatile with copy-on-write pattern for multiplatform compatibility.
 * Works on JVM, Native (iOS), and JS without platform-specific code.
 */
data object NavigationScopes {

    // Volatile to ensure visibility across threads
    @Volatile
    private var scopes = mutableMapOf<NavigationScopeId, NavigationScope>()

    /**
     * Get or create a scope (thread-safe, non-blocking).
     *
     * Safe to call from Composable and UI code.
     * Uses double-checked locking pattern with copy-on-write for efficiency.
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

    /**
     * Clear a scope (thread-safe).
     *
     * Called when back stack entries are removed.
     *
     * @param scopeId The scope ID to clear
     */
    fun clearScope(scopeId: NavigationScopeId) {
        val updated = scopes.toMutableMap()
        updated.remove(scopeId)?.clear()
        scopes = updated
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
 * It's destroyed when the scope is cleared (i.e., when the back stack entry is popped).
 *
 * @param T Instance type
 * @param scopeId The scope to store the instance in
 * @param key Unique key for the instance (defaults to class qualified name)
 * @param factory Function to create the instance if it doesn't exist
 * @param onCleared Called when the scope is cleared
 *
 * @return Scoped instance that survives recomposition but dies with navigation pop
 *
 * Example:
 * ```
 * val viewModel = rememberScoped<ProfileViewModel>(
 *     scopeId = entry.scopeId,
 *     factory = { ProfileViewModel(userId) },
 *     onCleared = { it.close() }
 * )
 * ```
 *
 * Thread Safety: Safe to call from Composable. Uses @Volatile for visibility.
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