@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.tekmoon.kompass

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two tab models, and what each one keeps.
 *
 * The reorder model uses one controller and `reuseIfExists`. Tapping a tab moves its root to the
 * top, and Back walks the cross-tab visit history.
 *
 * The per-tab model uses one controller for each tab, so every tab keeps its own depth. The
 * question this answers with a test rather than with prose: when the host of an inactive tab leaves
 * composition, what survives?
 */
class TabNavigationTest {
    private object Home : Destination { override val id = "home" }
    private object Search : Destination { override val id = "search" }
    private object Profile : Destination { override val id = "profile" }

    private class Counter : ViewModel() {
        var value = 0
        var clears = 0
        override fun onCleared() { clears++ }
    }

    private class Graph(
        val content: @Composable (BackStackEntry, NavController) -> Unit,
    ) : NavigationGraph {
        override fun canResolveDestination(destinationId: String) = true
        override fun resolveDestination(destinationId: String, args: String?) = when (destinationId) {
            "home" -> Home
            "search" -> Search
            else -> Profile
        }

        @Composable
        override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) =
            content(entry, navController)
    }

    // ---------------------------------------------------------------- reorder model

    @Test fun reordering_tabs_moves_the_entry_and_keeps_its_state_and_view_model() = runComposeUiTest {
        lateinit var nav: NavController
        val counters = mutableMapOf<String, Counter>()
        setContent {
            nav = rememberNavController(Home)
            KompassNavigationHost(
                nav,
                persistentListOf(
                    Graph { entry, _ ->
                        val counter = viewModel(key = entry.id) { Counter() }
                        counters[entry.destinationId] = counter
                        var typed by rememberSaveable(entry.id) { mutableStateOf("") }
                        BasicText("${entry.destinationId}:${counter.value}:$typed")
                    }
                ),
            )
        }

        // Visit Home, then Search, and leave a mark on each.
        runOnIdle { counters.getValue("home").value = 7 }
        val homeId = nav.backStack.last().id
        runOnIdle { nav.navigate(Search.toBackStackEntry(), reuseIfExists = true) }
        runOnIdle { counters.getValue("search").value = 3 }

        // Back to Home. The entry moves to the top instead of being created again.
        runOnIdle { nav.navigate(Home.toBackStackEntry(), reuseIfExists = true) }

        runOnIdle {
            assertEquals(listOf("search", "home"), nav.backStack.map { it.destinationId })
            assertEquals(homeId, nav.backStack.last().id, "reuse must retain the occurrence ID")
            assertEquals(7, counters.getValue("home").value, "the ViewModel must survive the move")
            assertEquals(0, counters.getValue("home").clears)
            assertEquals(0, counters.getValue("search").clears)
        }
        onNodeWithText("home:7:").assertExists()
    }

    @Test fun back_walks_the_cross_tab_visit_history() = runComposeUiTest {
        lateinit var nav: NavController
        setContent {
            nav = rememberNavController(Home)
            KompassNavigationHost(nav, persistentListOf(Graph { entry, _ -> BasicText(entry.destinationId) }))
        }
        runOnIdle { nav.navigate(Search.toBackStackEntry(), reuseIfExists = true) }
        runOnIdle { nav.navigate(Profile.toBackStackEntry(), reuseIfExists = true) }
        runOnIdle { nav.navigate(Search.toBackStackEntry(), reuseIfExists = true) }

        // Search was visited twice, and only the later visit stays on the stack.
        runOnIdle { assertEquals(listOf("home", "profile", "search"), nav.backStack.map { it.destinationId }) }
        runOnIdle { nav.popIfCan() }
        runOnIdle { assertEquals("profile", nav.currentEntry.destinationId) }
        runOnIdle { nav.popIfCan() }
        runOnIdle { assertEquals("home", nav.currentEntry.destinationId) }
    }

    @Test fun reuse_moves_one_entry_and_not_the_segment_above_it() = runComposeUiTest {
        // This is the documented limit of the reorder model, and it is correct behaviour for it.
        lateinit var nav: NavController
        setContent {
            nav = rememberNavController(Home)
            KompassNavigationHost(nav, persistentListOf(Graph { entry, _ -> BasicText(entry.destinationId) }))
        }
        runOnIdle { nav.navigate(Profile.toBackStackEntry(), reuseIfExists = true) }
        runOnIdle { nav.navigate(Search.toBackStackEntry()) }          // a detail inside Profile
        runOnIdle { nav.navigate(Home.toBackStackEntry(), reuseIfExists = true) }
        runOnIdle { nav.navigate(Profile.toBackStackEntry(), reuseIfExists = true) }

        runOnIdle {
            // Profile comes back alone. Its detail stays where it was, it does not follow the tab.
            assertEquals("profile", nav.currentEntry.destinationId)
            assertEquals(listOf("search", "home", "profile"), nav.backStack.map { it.destinationId })
        }
    }

    // ---------------------------------------------------------------- per-tab model

    @Test fun swapping_the_controller_of_one_host_clears_the_outgoing_tab() = runComposeUiTest {
        // The trap. One host whose navController parameter changes is NOT the same as unmounting a
        // host: the outgoing controller's entry owners are reconciled away, and its ViewModels are
        // cleared. Do not build a per-tab model this way.
        val tabs = listOf(Home, Search).map { createNavController(it) }
        val counters = mutableMapOf<String, Counter>()
        var active by mutableStateOf(0)

        setContent {
            KompassNavigationHost(
                tabs[active],
                persistentListOf(
                    Graph { entry, _ ->
                        counters[entry.id] = viewModel(key = entry.id) { Counter() }
                        BasicText(entry.destinationId)
                    }
                ),
            )
        }
        val homeId = tabs[0].currentEntry.id
        runOnIdle { active = 1 }
        runOnIdle {
            assertTrue(
                counters.getValue(homeId).clears > 0,
                "swapping the controller clears the outgoing tab, which is why this pattern is wrong",
            )
        }
        runOnIdle { tabs.forEach { it.close() } }
    }

    @Test fun one_host_per_tab_keeps_the_stack_view_models_and_ui_state_while_unmounted() = runComposeUiTest {
        // The pattern that works. Each tab owns a controller created outside composition and its own
        // host. Only the active host is composed, and the inactive tab keeps everything.
        val tabs = listOf(Home, Search).map { createNavController(it) }
        val counters = mutableMapOf<String, Counter>()
        var active by mutableStateOf(0)

        @Composable
        fun TabHost(nav: NavController) = KompassNavigationHost(
            nav,
            persistentListOf(
                Graph { entry, _ ->
                    val counter = viewModel(key = entry.id) { Counter() }
                    counters[entry.id] = counter
                    BasicText("${entry.destinationId}:${counter.value}")
                }
            ),
        )

        setContent {
            if (active == 0) TabHost(tabs[0]) else TabHost(tabs[1])
        }

        // Go one level deep in the first tab and mark its ViewModel.
        runOnIdle { tabs[0].navigate(Profile.toBackStackEntry()) }
        val deepId = tabs[0].currentEntry.id
        runOnIdle { counters.getValue(deepId).value = 42 }

        runOnIdle { active = 1 }
        runOnIdle {
            assertEquals("search", tabs[1].currentEntry.destinationId)
            assertEquals(0, counters.getValue(deepId).clears, "an unmounted host must not clear its ViewModels")
        }

        runOnIdle { active = 0 }
        runOnIdle {
            assertEquals(listOf("home", "profile"), tabs[0].backStack.map { it.destinationId })
            assertEquals(deepId, tabs[0].currentEntry.id)
            assertEquals(42, counters.getValue(deepId).value)
        }
        onNodeWithText("profile:42").assertExists()
        runOnIdle { tabs.forEach { it.close() } }
    }

    @Test fun closing_a_tab_controller_clears_its_view_models() = runComposeUiTest {
        // The other half of the contract: an externally owned controller is released by close(),
        // not by leaving composition. A tab host that is never closed leaks.
        val nav = createNavController(Home)
        val counters = mutableMapOf<String, Counter>()
        var mounted by mutableStateOf(true)

        setContent {
            if (mounted) {
                KompassNavigationHost(
                    nav,
                    persistentListOf(
                        Graph { entry, _ ->
                            counters[entry.id] = viewModel(key = entry.id) { Counter() }
                            BasicText(entry.destinationId)
                        }
                    ),
                )
            }
        }
        val rootId = nav.currentEntry.id
        runOnIdle { mounted = false }
        runOnIdle { assertEquals(0, counters.getValue(rootId).clears) }
        runOnIdle {
            nav.close()
            assertTrue(counters.getValue(rootId).clears > 0, "close() must release the tab's ViewModels")
        }
    }
}
