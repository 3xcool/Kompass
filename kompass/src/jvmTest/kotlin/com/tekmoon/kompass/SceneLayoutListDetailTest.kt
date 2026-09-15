@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.tekmoon.kompass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Width behaviour of the adaptive list-detail layout.
 *
 * The layout shows one pane below its threshold and two panes above it, so the threshold is the
 * only thing that decides how many entries a user sees.
 */
class SceneLayoutListDetailTest {

    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private class Graph(override val sceneLayout: SceneLayout) : NavigationGraph {
        override fun canResolveDestination(destinationId: String) = true
        override fun resolveDestination(destinationId: String, args: String?) =
            if (destinationId == "a") A else B

        @Composable
        override fun Content(
            entry: BackStackEntry,
            destination: Destination,
            navController: NavController,
        ) {
            BasicText("screen:${entry.destinationId}")
        }
    }

    @Test
    fun a_compact_width_shows_only_the_top_entry() = runComposeUiTest {
        val nav = hostOfWidth(400.dp)

        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()

        onNodeWithText("screen:b").assertExists()
        onNodeWithText("screen:a").assertDoesNotExist()
    }

    @Test
    fun an_expanded_width_shows_the_first_entry_beside_the_top_entry() = runComposeUiTest {
        val nav = hostOfWidth(900.dp)

        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()

        onNodeWithText("screen:a").assertExists()
        onNodeWithText("screen:b").assertExists()
    }

    @Test
    fun an_expanded_width_still_shows_one_pane_for_a_single_entry() = runComposeUiTest {
        hostOfWidth(900.dp)

        onNodeWithText("screen:a").assertExists()
        onNodeWithText("screen:b").assertDoesNotExist()
    }

    @Test
    fun the_threshold_decides_where_the_second_pane_appears() = runComposeUiTest {
        val nav = hostOfWidth(500.dp, layout = SceneLayoutListDetail(compactWidthThreshold = 400.dp))

        runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
        waitForIdle()

        onNodeWithText("screen:a").assertExists()
        onNodeWithText("screen:b").assertExists()
    }

    @Test
    fun the_master_pane_keeps_the_first_entry_while_the_detail_pane_follows_the_top() =
        runComposeUiTest {
            val nav = hostOfWidth(900.dp)

            runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
            waitForIdle()
            runOnIdle { nav.navigate(B.toKompassBackStackEntry()) }
            waitForIdle()

            runOnIdle { assertEquals(3, nav.backStack.size) }
            onNodeWithText("screen:a").assertExists()
            onNodeWithText("screen:b").assertExists()
        }

    private fun ComposeUiTest.hostOfWidth(
        width: Dp,
        layout: SceneLayout = SceneLayoutListDetail(),
    ): NavController {
        lateinit var nav: NavController
        setContent {
            nav = rememberKompassNavController(A)
            Box(Modifier.requiredWidth(width).fillMaxHeight()) {
                KompassNavigationHost(nav, persistentListOf(Graph(layout)))
            }
        }
        waitForIdle()
        return nav
    }
}
