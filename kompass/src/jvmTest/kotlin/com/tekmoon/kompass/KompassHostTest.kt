@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package com.tekmoon.kompass

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import org.koin.core.module.dsl.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.*

class KompassHostTest {
    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    private class Probe : ViewModel() {
        var clears = 0
        override fun onCleared() { clears++ }
    }

    private class Graph(
        override val sceneLayout: SceneLayout = SceneLayoutSinglePane,
        val content: @Composable (BackStackEntry, NavController) -> Unit,
    ) : NavigationGraph {
        override fun canResolveDestination(destinationId: String) = true
        override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
        @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
            assertEquals(entry.destinationId, destination.id)
            content(entry, navController)
        }
    }

    @Test fun an_initial_deep_link_gives_repeated_entries_distinct_occurrences() = runComposeUiTest {
        val repeated = B.toBackStackEntry()
        val handler = object : DeepLinkHandler {
            override fun matches(uri: String) = uri == "app://repeated"
            override fun resolve(uri: String) = listOf(NavigationCommand.ReplaceStack(listOf(repeated, repeated)))
        }
        lateinit var nav: NavController
        setContent {
            nav = rememberNavController(A, deepLinkUri = "app://repeated", deepLinkHandlers = persistentListOf(handler))
            KompassNavigationHost(nav, persistentListOf(Graph { entry, _ -> BasicText(entry.destinationId) }))
        }
        runOnIdle {
            assertEquals(listOf("b", "b"), nav.backStack.map { it.destinationId })
            assertEquals(2, nav.backStack.map { it.id }.toSet().size)
            assertEquals(repeated.id, nav.backStack.first().id)
        }
        onNodeWithText("b").assertExists()
    }

    @Test fun push_return_reuse_and_new_visit_preserve_the_right_state() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        var increment: () -> Unit = {}
        val graph = Graph { entry, _ ->
            val probe = viewModel<Probe> { Probe() }
            probes[entry.id] = probe
            var count by rememberSaveable { mutableStateOf(0) }
            increment = { count++ }
            BasicText("${entry.destinationId}:$count")
        }
        setContent {
            nav = rememberNavController(A)
            KompassNavigationHost(nav, persistentListOf(graph))
        }
        lateinit var first: BackStackEntry
        runOnIdle { first = nav.currentEntry; increment() }
        onNodeWithText("a:1").assertExists()
        runOnIdle { nav.navigate(B.toBackStackEntry()) }
        onNodeWithText("b:0").assertExists()
        runOnIdle { nav.navigate(A.toBackStackEntry(args = "updated"), reuseIfExists = true) }
        onNodeWithText("a:1").assertExists()
        runOnIdle { assertEquals(first.id, nav.currentEntry.id); assertEquals(0, probes[first.id]!!.clears) }
        runOnIdle { nav.pop() }
        runOnIdle { assertEquals(1, probes[first.id]!!.clears); nav.navigate(A.toBackStackEntry()) }
        onNodeWithText("a:0").assertExists()
    }

    @Test fun animated_pop_keeps_owner_and_scope_until_screen_disposal() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val disposal = mutableListOf<String>()
        var scopeClears = 0
        val graph = Graph(SceneLayoutDefaultAnimatedSinglePane) { entry, _ ->
            val probe = viewModel<Probe> { Probe() }
            probes[entry.id] = probe
            rememberScoped(entry.scopeId, onCleared = { _: Any -> scopeClears++ }) { Any() }
            DisposableEffect(entry.id) {
                onDispose { assertEquals(0, probe.clears); disposal.add(entry.id) }
            }
            Box(Modifier.size(100.dp)) { BasicText(entry.destinationId) }
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = newScope())) }
        waitForIdle()
        lateinit var popped: BackStackEntry
        runOnIdle { popped = nav.currentEntry }
        mainClock.autoAdvance = false
        runOnIdle { nav.pop() }
        mainClock.advanceTimeBy(100)
        runOnIdle { assertEquals(0, probes[popped.id]!!.clears); assertEquals(0, scopeClears) }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        runOnIdle { assertTrue(popped.id in disposal); assertEquals(1, probes[popped.id]!!.clears); assertEquals(1, scopeClears) }
    }

    @Test fun custom_layout_gets_distinct_owners_without_new_wrappers() = runComposeUiTest {
        lateinit var nav: NavController
        val owners = mutableMapOf<String, Any>()
        val layout = object : SceneLayout {
            @Composable override fun Render(backStack: kotlinx.collections.immutable.ImmutableList<BackStackEntry>,
                resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>, navController: NavController, direction: NavDirection) {
                backStack.forEach { entry -> key(entry.id) {
                    val (graph, destination) = resolve(entry)
                    graph.Content(entry, destination, navController)
                } }
            }
        }
        val graph = Graph(layout) { entry, _ -> owners[entry.id] = LocalViewModelStoreOwner.current!! }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        runOnIdle { nav.navigate(A.toBackStackEntry()) }
        runOnIdle { assertEquals(2, owners.size); assertNotSame(owners.values.first(), owners.values.last()) }
    }

    @Test fun closing_controller_disposes_all_entry_viewmodels() = runComposeUiTest {
        var visible by mutableStateOf(true)
        lateinit var probe: Probe
        val graph = Graph { _, _ -> probe = viewModel { Probe() } }
        setContent { if (visible) KompassNavigationHost(rememberNavController(A), persistentListOf(graph)) }
        runOnIdle { visible = false }
        runOnIdle { assertEquals(1, probe.clears) }
    }
    private class HandleProbe(val handle: androidx.lifecycle.SavedStateHandle) : ViewModel()

    @Test fun restore_recovers_stack_ids_ui_and_handles_including_covered_entries() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        lateinit var nav: NavController
        val handles = mutableMapOf<String, HandleProbe>()
        var editText: (String) -> Unit = {}
        val graph = Graph { entry, _ ->
            val probe = viewModel { HandleProbe(createSavedStateHandle()) }
            handles[entry.id] = probe
            var text by rememberSaveable { mutableStateOf("initial") }
            editText = { text = it }
            BasicText("${entry.destinationId}:$text:${probe.handle.get<String>("value")}")
        }
        setContent {
            if (mounted) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                nav = rememberNavController(A)
                KompassNavigationHost(nav, persistentListOf(graph))
            }
        }
        lateinit var firstId: String
        lateinit var secondId: String
        lateinit var original: HandleProbe
        runOnIdle {
            firstId = nav.currentEntry.id
            original = handles[firstId]!!
            editText("first")
            nav.navigate(B.toBackStackEntry())
        }
        runOnIdle {
            secondId = nav.currentEntry.id
            editText("second")
            // Mutating an off-screen handle must be included in the host's next save.
            original.handle["value"] = "saved-offscreen"
            handles[secondId]!!.handle["value"] = "saved-visible"
        }
        lateinit var saved: Map<String, List<Any?>>
        runOnIdle { saved = registry.performSave(); mounted = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; mounted = true }
        onNodeWithText("b:second:saved-visible").assertExists()
        runOnIdle {
            assertEquals(listOf(firstId, secondId), nav.backStack.map { it.id })
            nav.pop()
        }
        onNodeWithText("a:first:saved-offscreen").assertExists()
        runOnIdle { assertNotSame(original, handles[firstId]) }
    }

    @Test fun independent_hosts_do_not_share_viewmodels_or_saveable_state() = runComposeUiTest {
        lateinit var left: Probe
        lateinit var right: Probe
        val leftGraph = Graph { _, _ -> left = viewModel { Probe() } }
        val rightGraph = Graph { _, _ -> right = viewModel { Probe() } }
        setContent {
            KompassNavigationHost(rememberNavController(A), persistentListOf(leftGraph))
            KompassNavigationHost(rememberNavController(A), persistentListOf(rightGraph))
        }
        runOnIdle { assertNotSame(left, right) }
    }

    @Test fun nested_host_is_saved_with_its_parent_and_disposed_when_parent_is_popped() = runComposeUiTest {
        lateinit var outer: NavController
        lateinit var inner: NavController
        lateinit var innerProbe: Probe
        var editInner: () -> Unit = {}
        val innerGraph = Graph { _, _ ->
            innerProbe = viewModel { Probe() }
            var count by rememberSaveable { mutableStateOf(0) }
            editInner = { count++ }
            BasicText("nested:$count")
        }
        val outerGraph = Graph { entry, _ ->
            if (entry.destinationId == "b") {
                inner = rememberNavController(A)
                KompassNavigationHost(inner, persistentListOf(innerGraph))
            } else BasicText("outer")
        }
        setContent { outer = rememberNavController(A); KompassNavigationHost(outer, persistentListOf(outerGraph)) }
        runOnIdle { outer.navigate(B.toBackStackEntry()) }
        runOnIdle { editInner() }
        onNodeWithText("nested:1").assertExists()
        runOnIdle { outer.pop() }
        runOnIdle { assertEquals(1, innerProbe.clears) }
        onNodeWithText("outer").assertExists()
    }

    @Test fun koin_resolves_entry_saved_state_handle_without_consumer_owner_wiring() = runComposeUiTest {
        lateinit var probe: HandleProbe
        val module = org.koin.dsl.module {
            viewModel { HandleProbe(get()) }
        }
        val graph = Graph { _, _ -> probe = org.koin.compose.viewmodel.koinViewModel<HandleProbe>() }
        setContent {
            org.koin.compose.KoinApplication(application = { modules(module) }) {
                KompassNavigationHost(rememberNavController(A), persistentListOf(graph))
            }
        }
        runOnIdle { probe.handle["answer"] = "ok"; assertEquals("ok", probe.handle.get<String>("answer")) }
    }

    @Test fun changing_list_detail_width_preserves_each_entry_state() = runComposeUiTest {
        lateinit var nav: NavController
        var wide by mutableStateOf(false)
        val probes = mutableMapOf<String, Probe>()
        val firstSeen = mutableMapOf<String, Probe>()
        val graph = Graph(SceneLayoutListDetail(compactWidthThreshold = 200.dp)) { entry, _ ->
            val probe = viewModel { Probe() }
            probes[entry.id] = probe
            firstSeen.putIfAbsent(entry.id, probe)
            BasicText("pane:${entry.id}")
        }
        setContent {
            nav = rememberNavController(A)
            Box(Modifier.size(if (wide) 400.dp else 100.dp)) {
                KompassNavigationHost(nav, persistentListOf(graph))
            }
        }
        runOnIdle { nav.navigate(B.toBackStackEntry()) }
        waitForIdle()
        runOnIdle { wide = true }
        waitForIdle()
        runOnIdle { wide = false }
        waitForIdle()
        runOnIdle { probes.forEach { (id, probe) -> assertSame(firstSeen[id], probe); assertEquals(0, probe.clears) } }
    }

    @Test fun covering_parent_preserves_nested_controller_viewmodels_and_ui_state() = runComposeUiTest {
        lateinit var outer: NavController
        lateinit var nestedProbe: Probe
        var editNested: () -> Unit = {}
        val childGraph = Graph { _, _ ->
            nestedProbe = viewModel { Probe() }
            var text by rememberSaveable { mutableStateOf("initial") }
            editNested = { text = "edited" }
            BasicText("child:$text")
        }
        val outerGraph = Graph { entry, _ ->
            if (entry.destinationId == "a") {
                KompassNavigationHost(rememberNavController(B), persistentListOf(childGraph))
            } else BasicText("cover")
        }
        setContent { outer = rememberNavController(A); KompassNavigationHost(outer, persistentListOf(outerGraph)) }
        lateinit var original: Probe
        runOnIdle { original = nestedProbe; editNested() }
        runOnIdle { outer.navigate(B.toBackStackEntry()) }
        onNodeWithText("cover").assertExists()
        runOnIdle { assertEquals(0, original.clears); outer.pop() }
        onNodeWithText("child:edited").assertExists()
        runOnIdle { assertSame(original, nestedProbe) }
    }

    @Test fun saved_parent_includes_latest_handles_of_covered_nested_controller() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        lateinit var outer: NavController
        lateinit var child: HandleProbe
        val childGraph = Graph { _, _ ->
            child = viewModel { HandleProbe(createSavedStateHandle()) }
            BasicText("child-handle:${child.handle.get<String>("value")}")
        }
        val parentGraph = Graph { entry, _ ->
            if (entry.destinationId == "a") KompassNavigationHost(rememberNavController(B), persistentListOf(childGraph))
            else BasicText("covered")
        }
        setContent {
            if (mounted) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                outer = rememberNavController(A)
                KompassNavigationHost(outer, persistentListOf(parentGraph))
            }
        }
        runOnIdle { outer.navigate(B.toBackStackEntry()) }
        onNodeWithText("covered").assertExists()
        lateinit var saved: Map<String, List<Any?>>
        runOnIdle { child.handle["value"] = "changed-while-covered"; saved = registry.performSave(); mounted = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; mounted = true }
        onNodeWithText("covered").assertExists()
        runOnIdle { outer.pop() }
        onNodeWithText("child-handle:changed-while-covered").assertExists()
    }

    @Test fun shared_scope_reuses_viewmodel_but_keeps_each_occurrence_ui_and_lifecycle() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val owners = mutableMapOf<String, androidx.lifecycle.LifecycleOwner>()
        var increment: () -> Unit = {}
        val graph = Graph { entry, _ ->
            probes[entry.id] = viewModel { Probe() }
            owners[entry.id] = androidx.lifecycle.compose.LocalLifecycleOwner.current
            var count by rememberSaveable { mutableStateOf(0) }
            increment = { count++ }
            BasicText("${entry.destinationId}:$count")
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        lateinit var first: BackStackEntry
        lateinit var second: BackStackEntry
        runOnIdle { first = nav.currentEntry; increment() }
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = first.scopeId)) }
        onNodeWithText("b:0").assertExists()
        runOnIdle {
            second = nav.currentEntry
            assertSame(probes[first.id], probes[second.id])
            assertNotSame(owners[first.id], owners[second.id])
            assertEquals(androidx.lifecycle.Lifecycle.State.CREATED, owners[first.id]!!.lifecycle.currentState)
            increment()
            nav.navigate(A.toBackStackEntry(scopeId = first.scopeId))
        }
        onNodeWithText("a:0").assertExists()
        runOnIdle { assertSame(probes[first.id], probes[nav.currentEntry.id]); nav.pop() }
        onNodeWithText("b:1").assertExists()
        runOnIdle { assertEquals(0, probes[first.id]!!.clears); nav.pop() }
        onNodeWithText("a:1").assertExists()
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = newScope()), clearBackStack = true) }
        runOnIdle { assertEquals(1, probes[first.id]!!.clears); assertNotSame(probes[first.id], probes[nav.currentEntry.id]) }
    }

    @Test fun koin_shared_handle_survives_creator_removal_and_restoration() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        lateinit var nav: NavController
        lateinit var current: HandleProbe
        val module = org.koin.dsl.module { viewModel { HandleProbe(get()) } }
        val graph = Graph { _, _ ->
            current = org.koin.compose.viewmodel.koinViewModel<HandleProbe>()
            BasicText("value:${current.handle.get<String>("answer")}")
        }
        setContent {
            org.koin.compose.KoinApplication(application = { modules(module) }) {
                if (mounted) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    nav = rememberNavController(A)
                    KompassNavigationHost(nav, persistentListOf(graph))
                }
            }
        }
        lateinit var original: HandleProbe
        runOnIdle {
            original = current
            current.handle["answer"] = "shared"
            nav.navigate(B.toBackStackEntry(scopeId = A.defaultScope()))
        }
        runOnIdle {
            assertSame(original, current)
            nav.navigate(A.toBackStackEntry(), reuseIfExists = true)
        }
        runOnIdle { nav.pop() }
        runOnIdle { assertSame(original, current); current.handle["answer"] = "after-creator-pop" }
        lateinit var saved: Map<String, List<Any?>>
        runOnIdle { saved = registry.performSave(); mounted = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; mounted = true }
        onNodeWithText("value:after-creator-pop").assertExists()
        lateinit var recreated: HandleProbe
        runOnIdle {
            assertNotSame(original, current)
            recreated = current
            nav.navigate(A.toBackStackEntry())
        }
        runOnIdle { assertSame(recreated, current) }
    }

    @Test fun final_shared_entry_clears_viewmodel_only_after_its_exit_animation() = runComposeUiTest {
        lateinit var nav: NavController
        val probes = mutableMapOf<String, Probe>()
        val graph = Graph(SceneLayoutDefaultAnimatedSinglePane) { entry, _ ->
            probes[entry.id] = viewModel { Probe() }
            Box(Modifier.size(100.dp)) { BasicText(entry.destinationId) }
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        lateinit var first: BackStackEntry
        runOnIdle { first = nav.currentEntry; nav.navigate(B.toBackStackEntry(scopeId = first.scopeId)) }
        waitForIdle()
        runOnIdle { assertSame(probes[first.id], probes[nav.currentEntry.id]); nav.pop() }
        waitForIdle()
        runOnIdle { assertEquals(0, probes[first.id]!!.clears) }
        mainClock.autoAdvance = false
        runOnIdle { nav.navigate(B.toBackStackEntry(scopeId = newScope()), clearBackStack = true) }
        mainClock.advanceTimeBy(100)
        runOnIdle { assertEquals(0, probes[first.id]!!.clears) }
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        runOnIdle { assertEquals(1, probes[first.id]!!.clears) }
    }

    @Test fun broken_saved_navigation_restores_root_and_reports_once_without_host_crash() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var mounted by mutableStateOf(true)
        var reports = 0
        lateinit var nav: NavController
        val graph = Graph { entry, _ -> BasicText("restored:${entry.destinationId}") }
        setContent {
            if (mounted) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                nav = rememberNavController(A, onRestoreFailure = { reports++ })
                KompassNavigationHost(nav, persistentListOf(graph))
            }
        }
        runOnIdle { nav.navigate(B.toBackStackEntry()) }
        lateinit var saved: Map<String, List<Any?>>
        runOnIdle {
            saved = registry.performSave().mapValues { (_, values) -> values.map {
                if (it is String && it.contains("backStack")) "{broken" else it
            } }
            mounted = false
        }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; mounted = true }
        onNodeWithText("restored:a").assertExists()
        runOnIdle { assertEquals(1, reports); assertNotNull(nav.restorationFailure); nav.navigate(B.toBackStackEntry()) }
        onNodeWithText("restored:b").assertExists()
        runOnIdle { assertEquals(1, reports) }
    }

    @Test fun externally_created_controller_renders_and_survives_host_unmount_until_closed() = runComposeUiTest {
        var mounted by mutableStateOf(true)
        val nav = createNavController(A)
        lateinit var probe: Probe
        val graph = Graph { entry, _ -> probe = viewModel { Probe() }; BasicText("external:${entry.destinationId}") }
        try {
            nav.navigate(B.toBackStackEntry())
            setContent { if (mounted) KompassNavigationHost(nav, persistentListOf(graph)) }
            onNodeWithText("external:b").assertExists()
            lateinit var original: Probe
            runOnIdle { original = probe; mounted = false }
            runOnIdle { assertEquals(0, original.clears); mounted = true }
            runOnIdle { assertSame(original, probe); mounted = false }
            runOnIdle { nav.close(); assertEquals(1, original.clears) }
        } finally { nav.close() }
    }

    @Test fun transition_context_observes_command_direction_and_legacy_specs_still_work() = runComposeUiTest {
        lateinit var nav: NavController
        val seen = mutableListOf<SceneTransitionContext>()
        val contextual = object : SceneTransition {
            override fun transition(context: SceneTransitionContext): androidx.compose.animation.ContentTransform {
                seen += context
                return SceneTransitionDefault().transition(context.direction)
            }
        }
        val graph = object : NavigationGraph {
            override fun canResolveDestination(destinationId: String) = true
            override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
            override val sceneTransition = contextual
            @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
                Box(Modifier.size(100.dp)) { BasicText(entry.destinationId) }
            }
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        runOnIdle { nav.replaceStack(B.toBackStackEntry(args = "new-root")) }
        waitForIdle()
        runOnIdle {
            assertTrue(seen.any { it.from.destinationId == "a" && it.to.args == "new-root" && it.direction == NavDirection.Push })
            nav.navigate(A.toBackStackEntry())
        }
        waitForIdle()
        runOnIdle { nav.pop() }
        waitForIdle()
        runOnIdle { assertTrue(seen.any { it.from.destinationId == "a" && it.to.destinationId == "b" && it.direction == NavDirection.Pop }) }
        var legacyDirection: NavDirection? = null
        val legacy = object : SceneTransition {
            override fun transition(direction: NavDirection): androidx.compose.animation.ContentTransform {
                legacyDirection = direction
                return SceneTransitionStatic.transition(direction)
            }
        }
        runOnIdle {
            legacy.transition(SceneTransitionContext(A.toBackStackEntry(), B.toBackStackEntry(), NavDirection.Push))
            assertEquals(NavDirection.Push, legacyDirection)
        }
    }

    @Test fun list_detail_uses_the_graph_transition_when_the_layout_has_no_override() = runComposeUiTest {
        val nav = createNavController(
            NavigationState(persistentListOf(A.toBackStackEntry(), B.toBackStackEntry()))
        )
        val seen = mutableListOf<SceneTransitionContext>()
        val contextual = object : SceneTransition {
            override fun transition(context: SceneTransitionContext): androidx.compose.animation.ContentTransform {
                seen += context
                return SceneTransitionStatic.transition(context.direction)
            }
        }
        val graph = object : NavigationGraph {
            override val sceneLayout: SceneLayout = SceneLayoutListDetail()
            override val sceneTransition: SceneTransition = contextual
            override fun canResolveDestination(destinationId: String) = true
            override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
            @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
                Box(Modifier.size(100.dp)) { BasicText(entry.destinationId) }
            }
        }
        try {
            setContent { KompassNavigationHost(nav, persistentListOf(graph)) }

            runOnIdle { nav.navigate(A.toBackStackEntry()) }
            waitForIdle()
            onAllNodesWithText("a").assertCountEquals(2)

            runOnIdle { nav.pop() }
            waitForIdle()
            onNodeWithText("b").assertExists()

            runOnIdle {
                assertTrue(
                    seen.any {
                        it.from.destinationId == "b" && it.to.destinationId == "a" &&
                            it.direction == NavDirection.Push
                    },
                    "SceneLayoutListDetail must use the graph transition on forward navigation",
                )
                assertTrue(
                    seen.any {
                        it.from.destinationId == "a" && it.to.destinationId == "b" &&
                            it.direction == NavDirection.Pop
                    },
                    "SceneLayoutListDetail must use the graph transition on pop",
                )
            }
        } finally {
            runOnIdle { nav.close() }
        }
    }

    @Test fun seekable_progress_moves_both_ways_and_keeps_outgoing_owner_until_completion() = runComposeUiTest {
        lateinit var nav: NavController
        var progress by mutableStateOf<Float?>(0f)
        val probes = mutableMapOf<String, Probe>()
        val graph = object : NavigationGraph {
            override fun canResolveDestination(destinationId: String) = true
            override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
            override val sceneLayout get() = SceneLayoutSeekable(progress, SceneTransitionDefault(durationMs = 1000, fadeEnabled = false))
            @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
                probes[entry.id] = viewModel { Probe() }
                Box(Modifier.size(100.dp)) { BasicText("seek:${entry.destinationId}") }
            }
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        lateinit var initial: BackStackEntry
        mainClock.autoAdvance = false
        runOnIdle {
            initial = nav.currentEntry
            nav.replaceStack(B.toBackStackEntry(scopeId = newScope(), results = mapOf("pending" to object : NavigationResult {})))
        }
        mainClock.advanceTimeBy(64)
        runOnIdle { progress = 0.6f }
        mainClock.advanceTimeBy(64)
        val forwardX = onNodeWithText("seek:b").fetchSemanticsNode().positionInRoot.x
        runOnIdle { assertNotNull(nav.consumeResult<NavigationResult>("pending")) }
        mainClock.advanceTimeBy(64)
        assertEquals(forwardX, onNodeWithText("seek:b").fetchSemanticsNode().positionInRoot.x, 0.5f)
        runOnIdle { assertEquals(0, probes[initial.id]!!.clears); progress = 0.2f }
        mainClock.advanceTimeBy(64)
        val backwardX = onNodeWithText("seek:b").fetchSemanticsNode().positionInRoot.x
        assertTrue(backwardX > forwardX, "Seeking backward must move the incoming screen back toward its starting position")
        runOnIdle { assertEquals("b", nav.currentEntry.destinationId); progress = 1f }
        mainClock.advanceTimeBy(64)
        waitForIdle()
        runOnIdle { assertEquals(1, probes[initial.id]!!.clears) }
        onNodeWithText("seek:b").assertExists()
    }

    @Test fun seekable_survives_interruption_and_can_resume_automatically() = runComposeUiTest {
        lateinit var nav: NavController
        var progress by mutableStateOf<Float?>(0.3f)
        val probes = mutableMapOf<String, Probe>()
        val graph = object : NavigationGraph {
            override fun canResolveDestination(destinationId: String) = true
            override fun resolveDestination(destinationId: String, args: String?) = if (destinationId == "a") A else B
            override val sceneLayout get() = SceneLayoutSeekable(progress)
            @Composable override fun Content(entry: BackStackEntry, destination: Destination, navController: NavController) {
                probes[entry.id] = viewModel { Probe() }
                Box(Modifier.size(100.dp)) { BasicText(entry.id) }
            }
        }
        setContent { nav = rememberNavController(A); KompassNavigationHost(nav, persistentListOf(graph)) }
        mainClock.autoAdvance = false
        runOnIdle { nav.navigate(B.toBackStackEntry()) }
        mainClock.advanceTimeBy(64)
        runOnIdle { nav.replaceStack(A.toBackStackEntry(scopeId = newScope())); progress = 0.6f }
        mainClock.advanceTimeBy(64)
        runOnIdle { progress = null }
        mainClock.advanceTimeBy(2000)
        waitForIdle()
        runOnIdle {
            assertEquals(3, probes.size)
            probes.forEach { (id, probe) -> assertEquals(if (id == nav.currentEntry.id) 0 else 1, probe.clears) }
        }
    }

}
