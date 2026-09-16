package com.tekmoon.kompass

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a [PathTemplateDeepLinkHandler] extracted from a URI.
 *
 * @param uri The original URI, unchanged.
 * @param path Values captured by the `{name}` placeholders of the template, percent-decoded.
 * @param query Query parameters, percent-decoded. A repeated key keeps its last value.
 * @param args [path] and [query] merged into one JSON object, ready for [KompassEntry.args]. A
 * query parameter never overwrites a path placeholder of the same name.
 *
 * The Compose stability report marks the backing field of [args] unstable, because it cannot see
 * inside a `Lazy`. The [Immutable] promise still holds: [args] is computed once, from values that
 * never change, and nothing here has a setter. Keep the annotation, and keep this note with it.
 */
@Immutable
class DeepLinkMatch internal constructor(
    val uri: String,
    val path: ImmutableMap<String, String>,
    val query: ImmutableMap<String, String>,
) {
    /** Every value as a JSON string. Escaping is done by the serializer, never by concatenation. */
    val args: ArgsJson by lazy {
        buildJsonObject {
            query.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
            path.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }.toString()
    }

    /** Reads one value, from the path first and then from the query. */
    operator fun get(name: String): String? = path[name] ?: query[name]
}

/**
 * A [DeepLinkHandler] that matches a URI against a path template and hands you the parts.
 *
 * ```
 * PathTemplateDeepLinkHandler("app://profile/{userId}") { match ->
 *     listOf(
 *         NavigationCommand.ReplaceStack(
 *             listOf(
 *                 Home.toKompassEntry(),
 *                 Profile.toKompassEntry(args = match.args),
 *             )
 *         )
 *     )
 * }
 * ```
 *
 * Return a single [NavigationCommand.ReplaceStack] for a multi-level link. A list of commands is
 * applied one at a time, so it publishes every intermediate state and plays one animation per step.
 *
 * The template is compared segment by segment, and a `{name}` segment captures one segment. Values
 * are percent-decoded, and `+` counts as a space in the query only. Matching is case sensitive, and
 * a trailing slash is significant. A query string in the template is ignored: query parameters are
 * always optional and always parsed.
 *
 * This exists because building arguments by hand invites an injection:
 *
 * ```
 * args = """{"userId":"$userId"}"""   // a quote or a backslash in userId breaks the JSON
 * ```
 *
 * [DeepLinkMatch.args] is produced by the JSON serializer, so every value is escaped correctly.
 *
 * @param template A URI pattern, for example `app://orders/{orderId}/items/{itemId}`.
 * @param toCommands Turns a match into the commands to apply.
 */
class PathTemplateDeepLinkHandler(
    private val template: String,
    private val toCommands: (DeepLinkMatch) -> List<NavigationCommand>,
) : DeepLinkHandler {

    private val templateSegments: List<String> = template.substringBefore('?').split('/')

    override fun matches(uri: String): Boolean = capture(uri) != null

    override fun resolve(uri: String): List<NavigationCommand> {
        val path = capture(uri) ?: return emptyList()
        return toCommands(DeepLinkMatch(uri, path.toPersistentMap(), parseQuery(uri).toPersistentMap()))
    }

    /** Returns the captured placeholders, or null when the URI does not fit the template. */
    private fun capture(uri: String): Map<String, String>? {
        val segments = uri.substringBefore('#').substringBefore('?').split('/')
        if (segments.size != templateSegments.size) return null

        val captured = mutableMapOf<String, String>()
        for (index in templateSegments.indices) {
            val expected = templateSegments[index]
            val actual = segments[index]
            if (expected.length > 2 && expected.startsWith('{') && expected.endsWith('}')) {
                // An empty segment is not a value. "app://profile/" must not match "{userId}".
                if (actual.isEmpty()) return null
                captured[expected.substring(1, expected.length - 1)] = percentDecode(actual, plusIsSpace = false)
            } else if (expected != actual) {
                return null
            }
        }
        return captured
    }

    private fun parseQuery(uri: String): Map<String, String> {
        val query = uri.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                percentDecode(name, plusIsSpace = true) to percentDecode(value, plusIsSpace = true)
            }
    }

    override fun toString(): String = "PathTemplateDeepLinkHandler($template)"
}

/**
 * Builds an [ArgsJson] string with the JSON serializer instead of by concatenation.
 *
 * ```
 * val args = buildArgs {
 *     put("userId", userId)
 *     put("tab", 2)
 * }
 * ```
 *
 * Use this when a [DeepLinkHandler] or a test fixture needs raw arguments and the destination is not
 * a [TypedDestination]. For a typed destination, prefer `encodeArgs`, which uses its serializer.
 */
fun buildArgs(builder: JsonObjectBuilder.() -> Unit): ArgsJson =
    buildJsonObject(builder).toString()

/** Decodes `%XX` escapes, and `+` as a space where a query string allows it. */
private fun percentDecode(value: String, plusIsSpace: Boolean): String {
    if (!value.contains('%') && !(plusIsSpace && value.contains('+'))) return value

    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '%' && index + 2 < value.length -> {
                val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (decoded == null) {
                    bytes += char.code.toByte()
                    index++
                } else {
                    bytes += decoded.toByte()
                    index += 3
                }
            }

            plusIsSpace && char == '+' -> {
                bytes += ' '.code.toByte()
                index++
            }

            else -> {
                // Anything already literal keeps its own UTF-8 bytes.
                char.toString().encodeToByteArray().forEach { bytes += it }
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
