package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.tekmoon.kompass.util.randomUUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a logical navigation destination.
 *
 * A [Destination] is a lightweight identifier describing where the navigation
 * system should route to. It does not contain UI logic by itself.
 *
 * Destinations are resolved and rendered by a [NavigationGraph].
 *
 * @property id Stable identifier for the destination.
 * This value is used to match [BackStackEntry.destinationId]
 * and must remain stable across app versions.
 */
@Stable
interface Destination {
    val id: String
}

/**
 * Helper function to convert [Destination] to [BackStackEntry]
 */
fun Destination.toBackStackEntry(
    args: ArgsJson? = null,
    scopeId: NavigationScopeId = defaultScope(),
    pendingResultKey: String? = null,
    results: Map<String, NavigationResult> = emptyMap()
): BackStackEntry =
    BackStackEntry(
        destinationId = id,
        args = args,
        scopeId = scopeId,
        pendingResultKey = pendingResultKey,
        results = results
    )

/**
 * Marker interface representing a navigation result.
 *
 * Navigation results are delivered when a destination is popped
 * and can be consumed by a previous destination.
 *
 * This is intentionally an interface to allow polymorphic result types.
 */
interface NavigationResult // because of polymorphism

/**
 * Type alias representing encoded navigation arguments.
 *
 * Arguments are treated as an opaque string by the navigation system.
 */
typealias ArgsJson = String

/**
 * Represents a single entry in the navigation back stack.
 *
 * A [BackStackEntry] is a pure data structure describing:
 * - Which destination should be rendered
 * - Which arguments were used to reach it
 * - Which navigation scope it belongs to
 * - Any pending or delivered navigation results
 *
 * @param destinationId Identifier of the destination to render.
 * This must match the [Destination.id] resolved by a [NavigationGraph].
 *
 * @param args Optional encoded arguments associated with this destination.
 *
 * args is opaque to DsNavigation
 * It can be:
 * JSON
 * base64
 * any stable encoding
 *
 * @param scopeId Identifier of the navigation scope associated with this entry.
 * Scopes are used to manage lifecycle-aware resources such as ViewModels.
 *
 * @param pendingResultKey Optional key indicating that this entry expects
 * a navigation result when it is popped.
 *
 * @param results Map of delivered navigation results keyed by result identifier.
 * Results are immutable once delivered.
 *
 * Occurrence identity is managed by Kompass. [id] is read-only and can be used as a
 * content key in custom animated layouts. Sharing [scopeId] does not merge UI state.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BackStackEntry(
    val destinationId: String,
    val args: ArgsJson? = null,
    val scopeId: NavigationScopeId,
    val pendingResultKey: String? = null,
    val results: Map<String, NavigationResult> = emptyMap(),
) {
    @EncodeDefault
    @SerialName("id")
    private var occurrenceId: String = randomUUID()

    /** Stable, library-managed occurrence key for custom layouts. */
    val id: String get() = occurrenceId

    /** Copies payload while retaining identity; a different destination or scope starts a new occurrence. */
    fun copy(
        destinationId: String = this.destinationId,
        args: ArgsJson? = this.args,
        scopeId: NavigationScopeId = this.scopeId,
        pendingResultKey: String? = this.pendingResultKey,
        results: Map<String, NavigationResult> = this.results,
    ): BackStackEntry = BackStackEntry(destinationId, args, scopeId, pendingResultKey, results).also {
        if (scopeId == this.scopeId && destinationId == this.destinationId) it.occurrenceId = occurrenceId
    }

    internal fun withIdentityOf(entry: BackStackEntry): BackStackEntry = copy().also {
        it.occurrenceId = entry.id
    }

    internal fun newOccurrence(): BackStackEntry = BackStackEntry(destinationId, args, scopeId, pendingResultKey, results)

    operator fun component1() = destinationId
    operator fun component2() = args
    operator fun component3() = scopeId
    operator fun component4() = pendingResultKey
    operator fun component5() = results

    override fun equals(other: Any?): Boolean = other is BackStackEntry &&
        id == other.id && destinationId == other.destinationId && args == other.args &&
        scopeId == other.scopeId && pendingResultKey == other.pendingResultKey && results == other.results

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + destinationId.hashCode()
        result = 31 * result + (args?.hashCode() ?: 0)
        result = 31 * result + scopeId.hashCode()
        result = 31 * result + (pendingResultKey?.hashCode() ?: 0)
        return 31 * result + results.hashCode()
    }

    override fun toString(): String = "BackStackEntry(destinationId=$destinationId, args=$args, " +
        "scopeId=$scopeId, pendingResultKey=$pendingResultKey, results=$results, id=$id)"
}

/**
 * Defines a navigation graph responsible for resolving and rendering destinations.
 *
 * A [NavigationGraph] acts as the bridge between:
 * - Back stack entries
 * - Destination resolution
 * - UI rendering
 *
 * Multiple graphs can coexist, each owning a subset of destinations.
 * The navigation system selects the appropriate graph based on destinationId.
 */
@Stable
interface NavigationGraph {

    /**
     * Whether this graph can resolve and render the given destinationId.
     *
     * @param destinationId Identifier of the destination to check.
     *
     * @return true if this graph owns the destination, false otherwise.
     */
    fun canResolveDestination(destinationId: String): Boolean

    /**
     * Resolves a destinationId and optional arguments into a [Destination].
     *
     * This method should be pure and deterministic.
     *
     * @param destinationId Identifier of the destination to resolve.
     *
     * @param args Optional encoded arguments associated with the destination.
     *
     * @return A resolved [Destination] instance.
     */
    fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination

    /**
     * Optional transition applied when navigating between destinations
     * owned by this graph.
     */
    val sceneTransition: SceneTransition?
        get() = null

    /**
     * Optional layout strategy used to render destinations
     * owned by this graph.
     */
    val sceneLayout: SceneLayout?
        get() = null

    /**
     * Renders the UI for the given destination.
     *
     * @param entry The current [BackStackEntry] being rendered.
     *
     * @param destination The resolved [Destination] associated with this entry.
     *
     * @param navController Controller used to perform navigation actions
     * from within this destination.
     */
    @Composable
    fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    )
}
