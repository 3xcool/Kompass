package com.tekmoon.kompass

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes [KompassEntry.metadata] as a plain JSON object.
 *
 * kotlinx.serialization has no serializer for the immutable collections, the same reason
 * [NavigationState] carries a hand-written one for its back stack. The wire format is unchanged: an
 * entry written before the property became immutable still decodes.
 */
internal object MetadataSerializer : KSerializer<ImmutableMap<String, String>> {

    private val delegate = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableMap<String, String>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): ImmutableMap<String, String> =
        delegate.deserialize(decoder).toPersistentMap()
}

/** Serializes the closed result requests of a [KompassEntry]. See [MetadataSerializer]. */
internal object StoredResultsSerializer : KSerializer<PersistentMap<String, StoredResult>> {

    private val delegate = MapSerializer(String.serializer(), StoredResult.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: PersistentMap<String, StoredResult>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): PersistentMap<String, StoredResult> =
        delegate.deserialize(decoder).toPersistentMap()
}
