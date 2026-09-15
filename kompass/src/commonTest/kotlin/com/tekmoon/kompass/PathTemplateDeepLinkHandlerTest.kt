package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathTemplateDeepLinkHandlerTest {
    private object Home : Destination { override val id = "home" }
    private object Profile : Destination { override val id = "profile" }

    private fun handler(template: String) = PathTemplateDeepLinkHandler(template) { match ->
        listOf(
            NavigationCommand.ReplaceStack(
                listOf(
                    Home.toKompassBackStackEntry(),
                    Profile.toKompassBackStackEntry(args = match.args),
                )
            )
        )
    }

    private fun argsOf(handler: DeepLinkHandler, uri: String): JsonObject {
        val command = handler.resolve(uri).single() as NavigationCommand.ReplaceStack
        return Json.parseToJsonElement(command.entries.last().args!!) as JsonObject
    }

    // ---------------------------------------------------------------- matching

    @Test fun a_template_captures_one_segment_for_each_placeholder() {
        val handler = handler("app://orders/{orderId}/items/{itemId}")
        assertTrue(handler.matches("app://orders/7/items/3"))

        val args = argsOf(handler, "app://orders/7/items/3")
        assertEquals("7", args["orderId"]!!.jsonPrimitive.content)
        assertEquals("3", args["itemId"]!!.jsonPrimitive.content)
    }

    @Test fun a_uri_of_the_wrong_shape_does_not_match() {
        val handler = handler("app://profile/{userId}")
        assertFalse(handler.matches("app://profile"), "too few segments")
        assertFalse(handler.matches("app://profile/7/extra"), "too many segments")
        assertFalse(handler.matches("app://settings/7"), "a literal segment must match")
        assertFalse(handler.matches("app://profile/"), "an empty segment is not a value")
        assertFalse(handler.matches("other://profile/7"), "the scheme is part of the template")
    }

    @Test fun matching_is_case_sensitive_and_a_trailing_slash_counts() {
        val handler = handler("app://profile/{userId}")
        assertFalse(handler.matches("app://Profile/7"))
        assertFalse(handler.matches("app://profile/7/"))
    }

    // ---------------------------------------------------------------- query and decoding

    @Test fun query_parameters_join_the_arguments_and_are_optional() {
        val handler = handler("app://profile/{userId}")
        assertTrue(handler.matches("app://profile/7?tab=orders&ref=email"))

        val withQuery = argsOf(handler, "app://profile/7?tab=orders&ref=email")
        assertEquals("7", withQuery["userId"]!!.jsonPrimitive.content)
        assertEquals("orders", withQuery["tab"]!!.jsonPrimitive.content)
        assertEquals("email", withQuery["ref"]!!.jsonPrimitive.content)

        assertEquals(setOf("userId"), argsOf(handler, "app://profile/7").keys)
    }

    @Test fun a_path_placeholder_wins_over_a_query_parameter_of_the_same_name() {
        val handler = handler("app://profile/{userId}")
        assertEquals("7", argsOf(handler, "app://profile/7?userId=9")["userId"]!!.jsonPrimitive.content)
    }

    @Test fun values_are_percent_decoded_including_non_ascii() {
        val handler = handler("app://search/{term}")
        assertEquals("café com leite", argsOf(handler, "app://search/caf%C3%A9%20com%20leite")["term"]!!.jsonPrimitive.content)
        // '+' is a space in a query, and a literal '+' in a path segment.
        assertEquals("a b", argsOf(handler, "app://search/x?q=a+b")["q"]!!.jsonPrimitive.content)
        assertEquals("a+b", argsOf(handler, "app://search/a+b")["term"]!!.jsonPrimitive.content)
    }

    @Test fun a_fragment_is_ignored() {
        val handler = handler("app://profile/{userId}")
        assertTrue(handler.matches("app://profile/7#section"))
        assertEquals("7", argsOf(handler, "app://profile/7?tab=a#section")["userId"]!!.jsonPrimitive.content)
    }

    // ---------------------------------------------------------------- the injection this replaces

    @Test fun a_value_with_quotes_and_backslashes_produces_valid_json() {
        // The README used to build args by concatenation. This input broke that.
        val handler = handler("app://search/{term}")
        val raw = "he said \"hi\" \\ then left"
        val encoded = "he%20said%20%22hi%22%20%5C%20then%20left"

        val args = argsOf(handler, "app://search/$encoded")
        assertEquals(raw, args["term"]!!.jsonPrimitive.content)
    }

    @Test fun build_args_escapes_what_concatenation_would_break() {
        val hostile = "\"}, \"admin\": true, \"x\": \""
        val args = buildArgs { put("name", hostile) }

        val parsed = Json.parseToJsonElement(args) as JsonObject
        assertEquals(setOf("name"), parsed.keys, "the payload must not gain a key")
        assertEquals(hostile, parsed["name"]!!.jsonPrimitive.content)
    }

    @Test fun build_args_keeps_the_type_of_a_number_and_a_boolean() {
        val parsed = Json.parseToJsonElement(
            buildArgs { put("tab", 2); put("archived", true); put("name", "a") }
        ) as JsonObject
        assertEquals("2", parsed["tab"]!!.jsonPrimitive.content)
        assertFalse(parsed["tab"]!!.jsonPrimitive.isString)
        assertFalse(parsed["archived"]!!.jsonPrimitive.isString)
        assertTrue(parsed["name"]!!.jsonPrimitive.isString)
    }

    // ---------------------------------------------------------------- end to end

    @Test fun a_multi_level_link_applies_in_one_state_change() {
        val nav = createKompassNavController(Home, deepLinkHandlers = persistentListOf(handler("app://profile/{userId}")))
        try {
            assertFalse(nav.applyDeepLink("app://unknown/7"))
            assertTrue(nav.applyDeepLink("app://profile/7?tab=orders"))

            assertEquals(listOf("home", "profile"), nav.backStack.map { it.destinationId })
            val args = Json.parseToJsonElement(nav.currentEntry.args!!) as JsonObject
            assertEquals("7", args["userId"]!!.jsonPrimitive.content)
            assertEquals("orders", args["tab"]!!.jsonPrimitive.content)
        } finally {
            nav.close()
        }
    }

    @Test fun the_first_matching_handler_wins() {
        val specific = PathTemplateDeepLinkHandler("app://profile/me") { listOf() }
        val general = handler("app://profile/{userId}")
        val manager = DeepLinkManager(listOf(specific, general))

        assertEquals(0, manager.resolve("app://profile/me")!!.size, "the specific template matched first")
        assertEquals(1, manager.resolve("app://profile/7")!!.size)
        assertNull(manager.resolve("app://other/7"))
    }
}
