@file:OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    androidx.compose.ui.test.ExperimentalTestApi::class,
    ExperimentalKompassSharedTransitionApi::class,
)

package com.tekmoon.kompass

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedElementTransitionTest {
    private object ListDestination : Destination {
        override val id = "list"
    }

    private object DetailDestination : Destination {
        override val id = "detail"
    }

    private class Graph(
        private val scopes: MutableMap<String, Pair<SharedTransitionScope?, AnimatedVisibilityScope?>>,
        private val sharedStates: MutableMap<String, SharedContentState>,
        override val sceneLayout: SceneLayout = SceneLayoutDefaultAnimatedSinglePane,
    ) : KompassNavigationGraph {
        override fun canResolveDestination(destinationId: String) = true

        override fun resolveDestination(destinationId: String, args: String?) =
            if (destinationId == ListDestination.id) ListDestination else DetailDestination

        @Composable
        override fun Content(
            entry: KompassEntry,
            destination: Destination,
            navController: KompassNavController,
        ) {
            val sharedTransitionScope = LocalKompassSharedTransitionScope.current
            val animatedVisibilityScope = LocalKompassAnimatedVisibilityScope.current

            SideEffect {
                scopes[entry.destinationId] = sharedTransitionScope to animatedVisibilityScope
            }

            if (sharedTransitionScope == null || animatedVisibilityScope == null) {
                BasicText("screen:${entry.destinationId}")
                return
            }

            with(sharedTransitionScope) {
                val state = rememberSharedContentState(key = "hero")
                SideEffect { sharedStates[entry.destinationId] = state }
                Box(
                    Modifier
                        .size(if (entry.destinationId == ListDestination.id) 48.dp else 160.dp)
                        .sharedElement(
                            sharedContentState = state,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                ) {
                    BasicText("screen:${entry.destinationId}")
                }
            }
        }
    }

    @Test
    fun shared_host_matches_elements_across_navigation_occurrences() = runComposeUiTest {
        lateinit var navController: KompassNavController
        val scopes = mutableMapOf<String, Pair<SharedTransitionScope?, AnimatedVisibilityScope?>>()
        val sharedStates = mutableMapOf<String, SharedContentState>()
        val graph = Graph(scopes, sharedStates)

        setContent {
            navController = rememberKompassNavController(ListDestination)
            KompassSharedTransitionHost(navController, persistentListOf(graph))
        }
        onNodeWithText("screen:list").assertExists()

        mainClock.autoAdvance = false
        runOnIdle { navController.navigate(DetailDestination.toKompassEntry()) }
        mainClock.advanceTimeBy(64)

        runOnIdle {
            val listScopes = assertNotNull(scopes[ListDestination.id])
            val detailScopes = assertNotNull(scopes[DetailDestination.id])
            val sharedTransitionScope = assertNotNull(listScopes.first)
            assertSame(sharedTransitionScope, assertNotNull(detailScopes.first))
            assertNotNull(listScopes.second)
            assertNotNull(detailScopes.second)
            assertEquals(setOf(ListDestination.id, DetailDestination.id), sharedStates.keys)
            assertTrue(sharedStates.values.all { it.isMatchFound })
            assertTrue(sharedTransitionScope.isTransitionActive)
        }

        mainClock.advanceTimeBy(1_000)
        onNodeWithText("screen:detail").assertExists()
    }

    @Test
    fun predictive_back_keeps_the_shared_match_before_navigation_commits() = runComposeUiTest {
        lateinit var navController: KompassNavController
        val scopes = mutableMapOf<String, Pair<SharedTransitionScope?, AnimatedVisibilityScope?>>()
        val sharedStates = mutableMapOf<String, SharedContentState>()
        val graph = Graph(scopes, sharedStates, SceneLayoutPredictive())

        setContent {
            navController = rememberKompassNavController(ListDestination)
            KompassSharedTransitionHost(navController, persistentListOf(graph))
        }

        lateinit var root: KompassEntry
        mainClock.autoAdvance = false
        runOnIdle {
            root = navController.currentEntry
            navController.navigate(DetailDestination.toKompassEntry())
        }
        mainClock.advanceTimeBy(1_000)
        runOnIdle {
            scopes.clear()
            sharedStates.clear()
            navController.predictiveBack.start(root.id)
            navController.predictiveBack.update(0.5f)
        }
        mainClock.advanceTimeBy(64)

        runOnIdle {
            assertEquals(2, navController.backStack.size)
            assertEquals(DetailDestination.id, navController.currentEntry.destinationId)
            assertEquals(setOf(ListDestination.id, DetailDestination.id), sharedStates.keys)
            assertTrue(sharedStates.values.all { it.isMatchFound })
            val sharedTransitionScope = assertNotNull(assertNotNull(scopes[ListDestination.id]).first)
            assertSame(sharedTransitionScope, assertNotNull(assertNotNull(scopes[DetailDestination.id]).first))
            assertTrue(sharedTransitionScope.isTransitionActive)
        }
    }

    @Test
    fun regular_host_keeps_the_shared_transition_coordinator_opt_in() = runComposeUiTest {
        lateinit var observedScopes: Pair<SharedTransitionScope?, AnimatedVisibilityScope?>
        val graph = Graph(mutableMapOf(), mutableMapOf())
        val observingGraph = object : KompassNavigationGraph by graph {
            @Composable
            override fun Content(
                entry: KompassEntry,
                destination: Destination,
                navController: KompassNavController,
            ) {
                val scopes = LocalKompassSharedTransitionScope.current to
                    LocalKompassAnimatedVisibilityScope.current
                SideEffect { observedScopes = scopes }
                BasicText("regular")
            }
        }

        setContent {
            KompassNavigationHost(
                navController = rememberKompassNavController(ListDestination),
                graphs = persistentListOf(observingGraph),
            )
        }
        onNodeWithText("regular").assertExists()

        runOnIdle {
            assertNull(observedScopes.first)
            assertNotNull(observedScopes.second)
        }
    }
}
