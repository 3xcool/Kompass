package com.tekmoon.kompass

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Metadata says how to present a destination. It has to survive every path the entry takes,
 * because a shell reads it instead of matching on destination names.
 */
class BackStackEntryMetadataTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private val sheet = mapOf("presentation" to "sheet")

    @Test fun an_entry_carries_no_metadata_by_default() {
        assertEquals(emptyMap(), A.toBackStackEntry().metadata)
    }

    @Test fun metadata_survives_a_save_and_a_restore() {
        val nav = createNavController(A)
        try {
            nav.navigate(B.toBackStackEntry(metadata = sheet))
            val saved = nav.saveNavigationState()

            val restored = createNavController(A, savedNavigationState = saved)
            try {
                assertEquals(sheet, restored.currentEntry.metadata)
                assertEquals(emptyMap(), restored.backStack.first().metadata)
            } finally {
                restored.close()
            }
        } finally {
            nav.close()
        }
    }

    @Test fun copy_keeps_metadata_and_can_replace_it() {
        val entry = A.toBackStackEntry(metadata = sheet)
        assertEquals(sheet, entry.copy(args = "other").metadata)
        assertEquals(emptyMap(), entry.copy(metadata = emptyMap()).metadata)
    }

    @Test fun metadata_takes_part_in_equality() {
        val plain = A.toBackStackEntry()
        assertNotEquals(plain, plain.copy(metadata = sheet))
        assertNotEquals(plain.hashCode(), plain.copy(metadata = sheet).hashCode())
    }

    @Test fun reusing_an_entry_applies_the_metadata_of_the_new_call() {
        // The caller that navigates decides how the destination is shown, so the incoming hint wins
        // while the occurrence identity stays.
        val nav = createNavController(A)
        try {
            nav.navigate(B.toBackStackEntry())
            val firstId = nav.currentEntry.id
            nav.navigate(A.toBackStackEntry())
            nav.navigate(B.toBackStackEntry(metadata = sheet), reuseIfExists = true)

            assertEquals("b", nav.currentEntry.destinationId)
            assertEquals(firstId, nav.currentEntry.id)
            assertEquals(sheet, nav.currentEntry.metadata)
        } finally {
            nav.close()
        }
    }

    @Test fun a_replaced_stack_keeps_the_metadata_of_every_level() {
        val nav = createNavController(NavigationState(persistentListOf(A.toBackStackEntry())))
        try {
            nav.replaceStack(
                listOf(
                    A.toBackStackEntry(),
                    B.toBackStackEntry(metadata = mapOf("presentation" to "pane")),
                )
            )

            assertEquals(emptyMap(), nav.backStack.first().metadata)
            assertEquals("pane", nav.backStack.last().metadata["presentation"])
        } finally {
            nav.close()
        }
    }

    @Test fun metadata_is_separate_from_args() {
        // args belong to the screen, metadata belongs to whoever draws around it.
        val entry = B.toBackStackEntry(args = "{\"userId\":\"7\"}", metadata = sheet)
        assertEquals("{\"userId\":\"7\"}", entry.args)
        assertEquals(sheet, entry.metadata)
        assertTrue(entry.toString().contains("metadata="))
    }
}
