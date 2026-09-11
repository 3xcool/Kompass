package com.tekmoon.kompass

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Immutable representation of the current navigation state.
 *
 * [NavigationState] is the single source of truth for navigation.
 * It contains the complete back stack and nothing else.
 *
 * Design principles:
 * - Immutable and serializable
 * - Free of UI and platform concerns
 * - Suitable for reducer-based state transitions
 *
 * All navigation mutations must produce a new [NavigationState].
 *
 * @param backStack Ordered list of [BackStackEntry] instances.
 * The last entry represents the currently active destination.
 */
@Serializable
data class NavigationState(
    val backStack: ImmutableList<BackStackEntry>
) {

    /**
     * Returns true if the navigation stack can be popped.
     *
     * A stack with more than one entry indicates that a previous
     * destination exists.
     */
    fun canGoBack(): Boolean = backStack.size > 1

    companion object {

        /**
         * Creates a custom serializer for [NavigationState].
         *
         * This indirection allows callers to provide a custom
         * [KSerializer] for [BackStackEntry], enabling polymorphic
         * destination arguments and results.
         *
         * @param backStackEntrySerializer Serializer used to encode
         * and decode individual [BackStackEntry] instances.
         *
         * @return A [KSerializer] for [NavigationState].
         */
        fun serializer(backStackEntrySerializer: KSerializer<BackStackEntry>): KSerializer<NavigationState> {
            return NavigationStateSerializer(backStackEntrySerializer)
        }
    }
}

/**
 * Creates a default [NavigationState] with a single root entry.
 *
 * This is the canonical way to initialize navigation for an app.
 *
 * @param start The initial root [BackStackEntry].
 *
 * @return A [NavigationState] containing only the start entry.
 */
fun defaultNavigationState(
    start: BackStackEntry
): NavigationState = NavigationState(backStack = persistentListOf(start))

/**
 * Custom serializer for [NavigationState].
 *
 * This serializer is intentionally implemented manually to:
 * - Support immutable persistent collections
 * - Enable forward-compatible decoding
 * - Reject corrupted or incompatible state so the controller can apply its recovery policy
 *
 * Serialization format:
 * - Encodes the back stack as a list of [BackStackEntry]
 */
private class NavigationStateSerializer(
    private val backStackEntrySerializer: KSerializer<BackStackEntry>
) : KSerializer<NavigationState> {

    private val listSerializer = ListSerializer(backStackEntrySerializer)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NavigationState") {
        element<List<BackStackEntry>>("backStack")
    }

    /**
     * Serializes the given [NavigationState].
     *
     * @param encoder Encoder used to write the serialized form.
     *
     * @param value The navigation state to serialize.
     */
    override fun serialize(encoder: Encoder, value: NavigationState) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor,
                0,
                listSerializer,
                value.backStack.toList()
            )
        }
    }

    /**
     * Deserializes a [NavigationState].
     *
     * This implementation is tolerant to unknown fields to support
     * forward compatibility when newer versions add fields.
     *
     * Invalid or incompatible state throws. Controller restoration applies the selected
     * fallback policy; direct serializer callers receive the original failure.
     *
     * @param decoder Decoder used to read the serialized form.
     *
     * @return A valid decoded [NavigationState].
     */
    override fun deserialize(decoder: Decoder): NavigationState {
        var entries: ImmutableList<BackStackEntry>? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> entries = decodeSerializableElement(descriptor, 0, listSerializer).toPersistentList()
                    -1 -> break
                    else -> throw kotlinx.serialization.SerializationException("Unexpected navigation field index: $index")
                }
            }
        }
        return NavigationState(entries ?: throw kotlinx.serialization.SerializationException("Missing backStack"))
            .also { it.requireValid() }
    }
}

/** Invalid stacks must never reach a host. Recovery belongs to the caller that knows the root. */
internal fun NavigationState.requireValid() {
    require(backStack.isNotEmpty()) { "Navigation requires a non-empty back stack" }
    require(backStack.map { it.id }.toSet().size == backStack.size) { "Duplicate navigation occurrence IDs" }
}
