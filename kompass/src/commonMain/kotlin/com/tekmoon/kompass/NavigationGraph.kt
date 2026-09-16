package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.tekmoon.kompass.util.randomUUID
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
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
 * Destinations are resolved and rendered by a [KompassNavigationGraph].
 *
 * @property id Stable identifier for the destination.
 * This value is used to match [KompassEntry.destinationId]
 * and must remain stable across app versions.
 */
@Stable
interface Destination {
    val id: String
}

/**
 * Helper function to convert [Destination] to [KompassEntry]
 */
fun Destination.toKompassEntry(
    args: ArgsJson? = null,
    scopeId: NavigationScopeId = defaultScope(),
    metadata: Map<String, String> = emptyMap()
): KompassEntry =
    KompassEntry(
        destinationId = id,
        args = args,
        scopeId = scopeId,
        metadata = metadata.toPersistentMap()
    )

/**
 * Builds a [KompassEntry] from a raw destination ID.
 *
 * Prefer [toKompassEntry] when a [Destination] object is at hand. Use this when only the ID is
 * known, which is the usual case inside a [DeepLinkHandler] that parses a URI.
 *
 * It cannot set result state. Only the reducer may do that.
 *
 * @param destinationId Identifier of the destination to render.
 * @param args Optional encoded arguments.
 * @param scopeId Scope the entry belongs to. Defaults to the same value [defaultScope] produces.
 * @param metadata Presentation hints for the shell.
 */
fun kompassEntry(
    destinationId: String,
    args: ArgsJson? = null,
    scopeId: NavigationScopeId = NavigationScopeId("entry:$destinationId"),
    metadata: Map<String, String> = emptyMap(),
): KompassEntry = KompassEntry(
    destinationId = destinationId,
    args = args,
    scopeId = scopeId,
    metadata = metadata.toPersistentMap(),
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
 * A [KompassEntry] is a pure data structure describing:
 * - Which destination should be rendered
 * - Which arguments were used to reach it
 * - Which navigation scope it belongs to
 * - Any pending or delivered navigation results
 *
 * @param destinationId Identifier of the destination to render.
 * This must match the [Destination.id] resolved by a [KompassNavigationGraph].
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
 * @param metadata Presentation hints for the shell, keyed by name. This says **how** to show the
 * destination, while [args] says **what** the screen receives. Keep the two apart: [args] belongs
 * to the screen, and [metadata] belongs to whoever draws around it.
 *
 * A layout reads a hint instead of matching on [destinationId], so the shell never learns the names
 * of the destinations a feature module owns:
 *
 * ```
 * Profile.toKompassEntry(metadata = mapOf("presentation" to "sheet"))
 * ```
 *
 * Values are strings because the whole entry crosses the wire. A server payload, a deep link and a
 * restored session can all set a hint, the same way they set [args]. Kompass never reads a hint
 * itself; it only carries it and serialises it with the state.
 *
 * Occurrence identity is managed by Kompass. [id] is read-only and can be used as a
 * content key in custom animated layouts. Sharing [scopeId] does not merge UI state.
 *
 * Build an entry with [toKompassEntry]. The constructor is internal, because an entry also carries
 * result state that only the reducer may set.
 */
@OptIn(ExperimentalSerializationApi::class)
@Immutable
@Serializable
class KompassEntry internal constructor(
    val destinationId: String,
    val args: ArgsJson? = null,
    val scopeId: NavigationScopeId,
    /**
     * Presentation hints, as an immutable map.
     *
     * The public builders take a plain `Map` and convert here, so a caller keeps `mapOf(...)` and
     * the entry never holds a reference the caller can still change. Compose also reads an
     * [ImmutableMap] as a stable parameter, which a `Map` is not.
     */
    @Serializable(with = MetadataSerializer::class)
    val metadata: ImmutableMap<String, String> = persistentMapOf(),
    /**
     * Name of the [ResultKey] this entry waits for, or null when it waits for nothing.
     *
     * The `resultKey` of [KompassNavController.navigate] writes it, and delivery, cancellation and
     * [KompassNavController.consumeResult] clear it. It records **that** the entry is waiting;
     * the key that routes an answer travels with [KompassNavController.pop] instead.
     */
    val pendingResultKey: String? = null,
    /**
     * Closed result requests, keyed by result name.
     *
     * Internal, so an application cannot depend on the storage shape. Read a result with
     * [peekResult] and close it with [KompassNavController.consumeResult].
     *
     * A [PersistentMap] rather than an [ImmutableMap], because the reducer adds and removes one key
     * at a time and needs the functional `plus` and `minus`.
     */
    @Serializable(with = StoredResultsSerializer::class)
    internal val results: PersistentMap<String, StoredResult> = persistentMapOf(),
    /**
     * Stable, library-managed occurrence key for custom layouts.
     *
     * A constructor parameter rather than a `var` assigned after construction, so nothing about an
     * entry changes once it exists and [Immutable] is a promise the class actually keeps.
     */
    @EncodeDefault
    @SerialName("id")
    val id: String = randomUUID(),
) {

    /**
     * Copies the payload while retaining identity.
     *
     * A different destination or scope starts a new occurrence. Result state is carried over and
     * cannot be set here, because only the reducer may change it.
     */
    fun copy(
        destinationId: String = this.destinationId,
        args: ArgsJson? = this.args,
        scopeId: NavigationScopeId = this.scopeId,
        metadata: Map<String, String> = this.metadata,
    ): KompassEntry = copyInternal(destinationId, args, scopeId, metadata.toPersistentMap())

    internal fun copyInternal(
        destinationId: String = this.destinationId,
        args: ArgsJson? = this.args,
        scopeId: NavigationScopeId = this.scopeId,
        metadata: ImmutableMap<String, String> = this.metadata,
        pendingResultKey: String? = this.pendingResultKey,
        results: PersistentMap<String, StoredResult> = this.results,
    ): KompassEntry = KompassEntry(
        destinationId, args, scopeId, metadata, pendingResultKey, results,
        // A different destination or scope is a different occurrence, so it takes a fresh ID.
        id = if (scopeId == this.scopeId && destinationId == this.destinationId) id else randomUUID(),
    )

    internal fun withIdentityOf(entry: KompassEntry): KompassEntry = KompassEntry(
        destinationId, args, scopeId, metadata, pendingResultKey, results, id = entry.id,
    )

    internal fun newOccurrence(): KompassEntry =
        KompassEntry(destinationId, args, scopeId, metadata, pendingResultKey, results)

    /** True when the request under [key] ended without an answer. */
    @PublishedApi
    internal fun isResultCancelled(key: String): Boolean = results[key] is StoredResult.Cancelled

    /** The value delivered under [key], or null when nothing was delivered. */
    @PublishedApi
    internal fun deliveredResult(key: String): NavigationResult? =
        (results[key] as? StoredResult.Delivered)?.value

    operator fun component1() = destinationId
    operator fun component2() = args
    operator fun component3() = scopeId

    override fun equals(other: Any?): Boolean = other is KompassEntry &&
        id == other.id && destinationId == other.destinationId && args == other.args &&
        scopeId == other.scopeId && metadata == other.metadata &&
        pendingResultKey == other.pendingResultKey && results == other.results

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + destinationId.hashCode()
        result = 31 * result + (args?.hashCode() ?: 0)
        result = 31 * result + scopeId.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + (pendingResultKey?.hashCode() ?: 0)
        return 31 * result + results.hashCode()
    }

    override fun toString(): String = "KompassEntry(destinationId=$destinationId, args=$args, " +
        "scopeId=$scopeId, metadata=$metadata, pendingResultKey=$pendingResultKey, " +
        "results=$results, id=$id)"
}

/**
 * Defines a navigation graph responsible for resolving and rendering destinations.
 *
 * A [KompassNavigationGraph] acts as the bridge between:
 * - Back stack entries
 * - Destination resolution
 * - UI rendering
 *
 * Multiple graphs can coexist, each owning a subset of destinations.
 * The navigation system selects the appropriate graph based on destinationId.
 */
@Stable
interface KompassNavigationGraph {

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
     * @param entry The current [KompassEntry] being rendered.
     *
     * @param destination The resolved [Destination] associated with this entry.
     *
     * @param navController Controller used to perform navigation actions
     * from within this destination.
     */
    @Composable
    fun Content(
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController
    )
}
