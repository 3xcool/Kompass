# Kompass (KMP Navigation)

[![Maven Central](https://img.shields.io/maven-central/v/com.tekmoon/kompass)](https://central.sonatype.com/artifact/com.tekmoon/kompass)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-blue?logo=android)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen)](https://www.android.com)

<p align="center">
  <strong>A state-first, testable navigation library for Compose / Compose Multiplatform.</strong><br/>
  Pure reducer-driven navigation + pluggable layouts, deep links, results, and lifecycle-aware scopes.
</p>


<p align="center">
  <img src="assets/KompassOverview.png" alt="Kompass Overview" />
</p>


[▶ Watch usage overview](https://github.com/user-attachments/assets/180b7805-c2e5-4b3f-9d61-570d7d092ac5)


## About

Kompass is the next-generation navigation library designed from the ground up for Jetpack Compose. Unlike traditional navigation approaches, Kompass embraces functional programming principles and reactive architecture patterns:

- **Pure State Management** - Navigation state is immutable, serializable, and completely decoupled from UI logic. The entire navigation system is built on predictable, deterministic state transitions.
- **Reducer Pattern** - All navigation rules are side-effect free, making the navigation core trivial to test without mocking frameworks or instrumentation.
- **Multi-Graph Architecture** - Organize large applications across multiple modular navigation graphs with independent layouts and transitions.
- **Lifecycle-Aware Scopes** - Built-in scope management provides ViewModel-like instance storage with automatic cleanup and memory leak prevention.
- **Deep Linking Made Simple** - Extensible deep link handlers convert URIs into navigation commands with type-safe argument parsing.
- **Result Passing** - Deliver typed results between destinations without tight coupling or callback hell.
- **Persistent State** - Automatic serialization and restoration across configuration changes, process death, and app relaunches.
- **Customizable Layouts & Transitions** - Per-graph scene layouts support any composition pattern: single-stack, master-detail, split-screen, or custom multi-pane designs.


Perfect for applications that need robust, scalable, and testable navigation without the complexity of over-engineered frameworks.

## Contents

* [Key Features](#key-features)
* [Architecture](#architecture)
* [Installation](#installation)
* [Quick Start](#quick-start)
* [Navigation Commands](#navigation-commands)
* [Navigation Scopes](#navigation-scopes)
* [Navigation Results](#navigation-results)
* [Custom Layouts & Transitions](#custom-layouts--transitions)
* [Deep Linking](#deep-linking)
* [State Serialization](#state-serialization)
* [Testing](#testing)
* [Configuration & Customization](#configuration--customization)
* [Performance Considerations](#performance-considerations)
* [Thread Safety](#thread-safety)
* [Contributing](#contributing)
* [Resources](#resources)

## Key Features

- **Pure State Management** - Navigation state is immutable and serializable, separate from UI logic
- **Testable Reducer Pattern** - All navigation rules are deterministic and side-effect free
- **Pluggable Layouts & Transitions** - Customize screen transitions and multi-pane layouts per graph
- **Multi-Graph Support** - Organize destinations across multiple navigation graphs
- **Navigation Scopes** - Lifecycle-aware instance management (ViewModel-like) with automatic cleanup
- **Deep Linking** - Built-in deep link support with extensible handlers
- **Result Passing** - Deliver navigation results between destinations
- **Persistent State** - Automatic serialization and restoration across configuration changes

## Architecture

Kompass follows a clean separation of concerns:

```
Navigation Logic
    ↓
NavigationHandler (pure reducer: State + Command → State)
    ↓
NavigationState (immutable back stack)
    ↓
NavController (facade & effects)
    ↓
KompassNavigationHost (renders via NavigationGraph)
    ↓
Screen Content
```

### Core Components

- **NavigationState** - Immutable representation of the back stack
- **NavigationHandler** - Pure reducer applying navigation commands
- **NavController** - Public API for performing navigation
- **KompassNavigationHost** - Root composable orchestrating rendering
- **NavigationGraph** - Maps destinations to UI content
- **NavigationScopes** - Thread-safe lifecycle-aware instance storage
- **BackStackEntry** - Represents a single stack entry with destination, args, and scope

## Installation

Add to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.tekmoon:kompass:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

## Quick Start

### 1. Define Destinations

```kotlin
sealed interface MainDestination : Destination {
    data object Home : MainDestination {
        override val id: String = "home"
    }
    data object Profile : MainDestination {
        override val id: String = "profile"
    }
    data object Settings : MainDestination {
        override val id: String = "settings"
    }
}
```

### 2. Create a Navigation Graph

```kotlin
class MainNavigationGraph : NavigationGraph {
    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId in setOf("home", "profile", "settings")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination = when (destinationId) {
        "home" -> MainDestination.Home
        "profile" -> MainDestination.Profile
        "settings" -> MainDestination.Settings
        else -> error("Unknown destination: $destinationId")
    }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            is MainDestination.Home -> HomeScreen(navController)
            is MainDestination.Profile -> ProfileScreen(navController)
            is MainDestination.Settings -> SettingsScreen(navController)
        }
    }
}
```

### 3. Setup Navigation Host

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController(
        startDestination = MainDestination.Home
    )

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(MainNavigationGraph())
    )
}
```

### 4. Navigate from Screens

```kotlin
@Composable
fun HomeScreen(navController: NavController) {
    Button(
        onClick = {
            navController.navigate(
                entry = BackStackEntry(
                    destinationId = "profile",
                    scopeId = newScope()
                )
            )
        }
    ) {
        Text("Go to Profile")
    }
}
```

## Navigation Commands

### Navigate

Push a new destination onto the back stack:

```kotlin
navController.navigate(
    entry = BackStackEntry(
        destinationId = "profile",
        args = """{"userId":"123"}""",
        scopeId = newScope()
    ),
    clearBackStack = false,        // Clear entire stack
    popUpTo = "home",              // Pop up to destination
    popUpToInclusive = false,      // Include the destination in pop
    reuseIfExists = false          // Reuse existing entry
)
```

### Pop

Remove one or more entries from the back stack:

```kotlin
// Pop single entry
navController.pop()

// Pop with result
navController.pop(result = ProfileResult(userId = "123"))

// Pop multiple entries
navController.pop(count = 2)

// Pop until destination
navController.pop(popUntil = "home")
```

### Replace Root

Replace the entire back stack with a single entry:

```kotlin
navController.replaceRoot(
    entry = BackStackEntry(
        destinationId = "home",
        scopeId = newScope()
    )
)
```

## Navigation Scopes

Navigation Scopes provide lifecycle-aware instance storage similar to ViewModels:

```kotlin
@Composable
fun ProfileScreen(navController: NavController, entry: BackStackEntry) {
    val viewModel = rememberScoped<ProfileViewModel>(
        scopeId = entry.scopeId,
        factory = { ProfileViewModel() },
        onCleared = { it.close() }
    )

    // ViewModel survives recomposition but is cleared when entry is popped
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }
}
```

### Scope Types

**Default Scope** - Shared state across multiple navigations to same destination:
```kotlin
val entry = BackStackEntry(
    destinationId = "profile",
    scopeId = destination.defaultScope()  // "entry:profile"
)
```

**Unique Scope** - Isolated state for each navigation:
```kotlin
val entry = BackStackEntry(
    destinationId = "profile",
    scopeId = newScope()  // "entry:{randomUUID}"
)
```

## Navigation Results

Use `pendingResultKey` when opening the destination, then return a result with `pop`:

```kotlin
navController.navigate(
    Profile.toBackStackEntry(pendingResultKey = "profile_result")
)

// Inside Profile: remove this destination and deliver the result to the previous entry.
navController.pop(result = ProfileResult(userId = "123"))
```

For one-time processing, use `consumeResult<T>()` from an effect or event handler:

```kotlin
@Composable
fun HomeScreen(
    navController: NavController,
    entry: BackStackEntry,
    onProfileResult: (ProfileResult) -> Unit,
) {
    LaunchedEffect(navController, entry.id, entry.results["profile_result"]) {
        val result = navController.consumeResult<ProfileResult>(
            key = "profile_result",
            entryId = entry.id,
        ) ?: return@LaunchedEffect
        onProfileResult(result)
    }
}
```

For consumption semantics and serializer registration, see
[Results, restoration and external controllers](#results-restoration-and-external-controllers).

## Custom Layouts & Transitions

Customize screen transitions and multi-pane layouts:

```kotlin
class MainNavigationGraph : NavigationGraph {
    override val sceneLayout: SceneLayout = object : SceneLayout {
        @Composable
        override fun Render(
            backStack: ImmutableList<BackStackEntry>,
            resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
            navController: NavController,
            direction: NavDirection
        ) {
            AnimatedContent(
                targetState = backStack.last(),
                transitionSpec = {
                    slideInHorizontally() togetherWith slideOutHorizontally()
                }
            ) { entry ->
                val (graph, destination) = resolve(entry)
                graph.Content(entry, destination, navController)
            }
        }
    }

    override val sceneTransition: SceneTransition? = null
}
```

### Multi-Pane Layouts

For tablet layouts with master-detail patterns:

```kotlin
override val sceneLayout: SceneLayout = object : SceneLayout {
    @Composable
    override fun Render(
        backStack: ImmutableList<BackStackEntry>,
        resolve: (BackStackEntry) -> Pair<NavigationGraph, Destination>,
        navController: NavController,
        direction: NavDirection
    ) {
        Row {
            // Master pane (static)
            Box(modifier = Modifier.weight(1f)) {
                val masterEntry = backStack.first()
                val (graph, destination) = resolve(masterEntry)
                graph.Content(masterEntry, destination, navController)
            }

            // Detail pane (animated)
            AnimatedContent(
                targetState = backStack.last(),
                modifier = Modifier.weight(1f),
                label = "DetailPane"
            ) { entry ->
                val (graph, destination) = resolve(entry)
                graph.Content(entry, destination, navController)
            }
        }
    }
}
```

## Deep Linking

Resolve deep link URIs to navigation commands:

```kotlin
interface DeepLinkHandler {
    fun matches(uri: String): Boolean
    fun resolve(uri: String): List<NavigationCommand>
}

class ProfileDeepLinkHandler : DeepLinkHandler {
    override fun matches(uri: String): Boolean = uri.startsWith("app://profile/")

    override fun resolve(uri: String): List<NavigationCommand> {
        val userId = uri.removePrefix("app://profile/")
        return listOf(
            NavigationCommand.Navigate(
                entry = BackStackEntry(
                    destinationId = "profile",
                    args = """{"userId":"$userId"}""",
                    scopeId = newScope()
                )
            )
        )
    }
}

// Apply deep link
val success = navController.applyDeepLink("app://profile/user123")
```

## State Serialization

Navigation state is automatically serialized and restored:

```kotlin
@Composable
fun rememberNavController(
    initialState: NavigationState,
    serializersModule: SerializersModule = SerializersModule {},
    deepLinkUri: String? = null,
    deepLinkHandlers: ImmutableList<DeepLinkHandler> = persistentListOf()
): NavController {
    // State is saved via rememberSaveable and restored on configuration changes
}
```

## Testing

Since navigation logic is pure and deterministic, testing is straightforward:

```kotlin
@Test
fun testNavigateCommand() {
    val handler = NavigationHandler()
    val initialState = defaultNavigationState(
        BackStackEntry(
            destinationId = "home",
            scopeId = NavigationScopeId("home")
        )
    )

    val command = NavigationCommand.Navigate(
        entry = BackStackEntry(
            destinationId = "profile",
            scopeId = newScope()
        )
    )

    val newState = handler.reduce(initialState, command)

    assertEquals(2, newState.backStack.size)
    assertEquals("profile", newState.backStack.last().destinationId)
}

@Test
fun testPopCommand() {
    val handler = NavigationHandler()
    val state = NavigationState(
        backStack = persistentListOf(
            BackStackEntry("home", scopeId = NavigationScopeId("home")),
            BackStackEntry("profile", scopeId = newScope())
        ).toImmutableList()
    )

    val newState = handler.reduce(state, NavigationCommand.Pop())

    assertEquals(1, newState.backStack.size)
    assertEquals("home", newState.backStack.last().destinationId)
}
```

## Configuration & Customization

### iOS Signing

Create the local Xcode configuration before opening the iOS project:

```bash
cp iosApp/Configuration/Config.xcconfig.template \
   iosApp/Configuration/Config.xcconfig
```

Set `TEAM_ID` in `Config.xcconfig` to your Apple Developer Team ID when signing for a device.
The simulator does not require a Team ID. The local file is ignored by Git.

### Custom Serialization

Register custom serializers for destination arguments:

```kotlin
val serializersModule = SerializersModule {
    polymorphic(NavigationResult::class) {
        subclass(ProfileResult::class)
        subclass(SettingsResult::class)
    }
}

val navController = rememberNavController(
    startDestination = MainDestination.Home,
    serializersModule = serializersModule
)
```

### Custom Navigation Scopes

Use an explicit `NavigationScopeId` and the `key`, `factory`, and `onCleared` parameters of
`rememberScoped` to share and manage arbitrary objects. `NavigationScope` is final.

## Performance Considerations

- **Immutable Collections** - Uses `kotlinx-collections-immutable` for efficient structural sharing
- **Lazy Graph Resolution** - Destinations are only resolved when rendered
- **Efficient Recomposition** - State changes only trigger recomposition of affected content
- **Scope Cleanup** - Scopes are automatically cleaned when entries are removed, preventing memory leaks

## Thread Safety

- Navigation state is immutable and thread-safe
- Call navigation and `NavigationScopes` operations on the UI thread.
- Volatile map publication does not make compound operations or stored objects thread-safe.

## Contributing

Contributions are welcome! Please ensure:
- All navigation logic remains pure and testable
- New features maintain backward compatibility
- Comprehensive tests accompany changes
- Code follows existing style and patterns

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Resources

- [Compose Documentation](https://developer.android.com/jetpack/compose)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlinx Collections Immutable](https://github.com/Kotlin/kotlinx.collections.immutable)
- [GitHub Repository](https://github.com/3xcool/kompass)

## Automatic ownership and UI state

`KompassNavigationHost` provides owners automatically. Each back-stack occurrence has its
own LifecycleOwner and SavedStateRegistryOwner; entries with the same `scopeId` share a
ViewModelStore and SavedStateHandle storage within their controller. Existing graphs and custom layouts
that render the graph returned by `resolve(entry)` receive these automatically; screens need
no ownership wrappers and the core library has no Koin dependency.

Use `viewModel()` or `koinViewModel()` inside graph content. Android supports the standard
SavedStateViewModelFactory, including SavedStateHandle constructors. Desktop supports no-arg
and SavedStateHandle constructors. On iOS and Wasm, supply an initializer/factory (including
Koin's factory), as usual on platforms without constructor reflection.

Entry owners remain alive while the entry is in the back stack **or** its content is still
composed. Removing an entry destroys its lifecycle after exit disposal. Shared ViewModels
are cleared exactly once after the last entry of their scope is removed and its outgoing
content is disposed. Removing the entry that originally created a ViewModel does not clear
that ViewModel while another entry still uses the scope. Covered
entries that are no longer rendered remain at CREATED; rendered non-top entries are STARTED;
the top entry can reach RESUMED, capped by the host lifecycle. STARTED still permits the default
collectAsStateWithLifecycle collection. Each visible pane receives its own owner.

The host also wraps each entry in a SaveableStateProvider. `rememberSaveable` state survives
push-and-return. A new visit after pop starts fresh. Each host owns an independent holder;
nested hosts save inside their parent entry's saveable state and retain their ViewModels
while that parent entry remains in the back stack.

### Reusing an entry versus sharing a scope

These are separate choices:

- `reuseIfExists = false` adds a new occurrence with its own UI state and lifecycle. Its
  ViewModels are shared when another entry in the controller uses the same scope.
- `reuseIfExists = true` moves the last matching destination to the top, keeping other entries
  in their existing order. It uses the incoming arguments and result fields. With the same
  scope it retains the occurrence ID and UI state. Supplying a different scope creates fresh
  entry state and uses that scope's ViewModels (creating them if the scope is new).
- `defaultScope()` shares ViewModels across visits to the same destination within a controller;
  `newScope()` isolates them. Pass the same explicit `scopeId` to different routes to share
  a ViewModel across a flow. The ViewModel class/key must also match. Factory parameters only
  apply on creation; another route does not recreate an existing shared ViewModel.
- `rememberScoped` retains its existing process-wide scope registry. AndroidX ViewModel
  sharing is controller-local, so independent hosts do not accidentally share ViewModels.
  Neither form of sharing merges entry lifecycle or UI state.
- Existing direct NavigationScopes access remains available. Explicit clearScope/clearAll
  clears immediately; automatic navigation cleanup waits for rendered content to leave.

For example, reusing B in A → B → C produces A → C → B, not A → B.

`BackStackEntry.id` is a read-only occurrence key generated by the library, with no constructor
or `copy` parameter. It is saved with the stack and preserved when arguments or results are
copied. Copying to a different destination or scope creates a new identity. Navigating again
with an entry already in the stack creates a distinct occurrence unless reuse is requested. In a custom AnimatedContent layout,
use `contentKey = { it.id }` so updating an entry's arguments or results does not animate two
copies of that same occurrence. Pass the lambda's entry to `resolve`, and do not cache a
resolved destination solely by ID: the arguments may change on reuse.

### Restoration and disposal

Navigation entries, `rememberSaveable` values, and SavedStateHandle values are saved through
the enclosing saveable registry. Handles are saved once per scope, including scopes whose
screens are not composed. Restoration recreates one shared ViewModel per scope and class/key;
its state does not depend on which route first requests it. Saved values must satisfy the platform's saveable types.

On Android, an enclosing ViewModelStore retains entry owners across Activity recreation;
permanent controller disposal releases them. Other targets release entry owners when the
controller composition leaves. Process recreation creates new ViewModels from saved values:
arbitrary objects held by rememberScoped are not serialized. Persistence across a full app
relaunch depends on the platform's enclosing saved-state infrastructure.

Arguments remain opaque strings. Kompass does not guess how to expand them into SavedStateHandle
fields; use the typed helpers to read navigation arguments.

### Typed navigation

```kotlin
@Serializable
data class ProfileArgs(val userId: String)

object Profile : TypedDestination<ProfileArgs> {
    override val id = "profile"
    override val argsSerializer = ProfileArgs.serializer()
}

// All navigation flags and explicit scope choices remain available.
navController.navigateTo(Profile, ProfileArgs("example"), scopeId = newScope())
val args = navController.requireArgs(Profile, entry)
```

For commands built outside a controller, use `Profile.toBackStackEntry(args, json)` with the
appropriate serializers. With a controller, `navController.toBackStackEntry(Profile, args)`
and `navController.encodeArgs(Profile, args)` use its configured Json. Existing opaque argument
strings, polymorphic NavigationResult serializers, deep-link handlers and command sequences
remain supported.

### Compatibility notes

BackStackEntry keeps its original five constructor/copy parameters and destructuring fields.
It is now a regular class with explicit copy/equality behavior so occurrence identity cannot
be supplied by callers. Equality includes identity; separately constructed equal payloads
represent different occurrences. Consumers relying on data-class reflection must adapt.
Legacy serialized entries without an ID are accepted and assigned one during restoration.


## Results, restoration and external controllers

`consumeResult<T>(key, entryId)` returns and removes one result from the specified occurrence.
Omitting entryId targets the current entry. A missing key/entry or mismatched type returns null
without deleting anything. Consumption removes the result from the receiving entry's map,
not the entry from the back stack; `pop` already removed the sending destination.

Reading `entry.results[key]` only inspects the value and leaves it available for later reads.
Use `consumeResult` for one-time processing, from an event handler or effect rather than the
composable body. See [Navigation Results](#navigation-results) for a complete send/receive example.

NavigationResult implementations must be serializable and registered for saved navigation:

```kotlin
@Serializable
@SerialName("name-result")
data class NameResult(val name: String) : NavigationResult

val resultSerializers = SerializersModule {
    polymorphic(NavigationResult::class) { subclass(NameResult::class) }
}
val controller = createNavController(Home, serializersModule = resultSerializers)
// After a destination has delivered a result with pendingResultKey = "name":
val result = controller.consumeResult<NameResult>("name")
```

This consumes stored data; it is not a transactional guarantee for application side effects.
Unregistered result types fail when saving rather than silently dropping data. Registered
results survive restoration until consumed. Reusing a result key before consumption replaces
its previous value, as before.

Invalid saved navigation (including an empty stack or duplicate occurrence IDs) falls back to
the valid initial state by default. `restorationFailure` retains the cause and `onRestoreFailure`
reports it once per restoration. In composition, the callback runs from an effect. Select
`NavigationRestorePolicy.Throw` to reject restoration instead. Direct deserialization throws;
the serializer no longer disguises a failure as an empty stack. Recovery does not validate
whether the app's graphs still support every saved destination; graph resolution remains the
app's routing contract.

```kotlin
val controller = createNavController(
    Home,
    serializersModule = resultSerializers,
    savedNavigationState = previouslySavedJson,
    onRestoreFailure = { cause -> reportNavigationFailure(cause) },
)
controller.stateFlow.collect { state -> /* observe the current immutable stack */ }
```

`createNavController` does not require composition. Pass it directly to KompassNavigationHost.
Its owner must call `close()` when finished; temporary host unmount does not close it. Mutation,
saving and close must run on the UI thread. Flow collection can use another dispatcher.
`stateFlow` is read-only and conflated: it represents current state, not every intermediate
command. The existing `state`, `currentEntry`, typed helpers and deep-link methods remain available.

`saveNavigationState()` serializes the stack, arguments and pending results only. It does not
serialize live ViewModels, SavedStateHandles or Compose UI state. Use rememberNavController
for automatic composition-owned restoration/retention. An external controller's lifetime and
persistence belong to its owner; close is idempotent, and navigation after close throws.

## Transition context and controlled progress

Direction now follows the command that changes the active occurrence: Navigate and ReplaceRoot
are Push, Pop is Pop. Consuming results, updating the current occurrence and no-op commands do
not overwrite the last direction. Recomposition no longer changes the direction.

Existing `SceneTransition.transition(direction)` implementations remain supported. Override
`transition(context: SceneTransitionContext)` to inspect source/target entries, including their
destination IDs and arguments. Built-in animated layouts supply this context. Custom layouts
can use `entryTransition(direction, transition)`; the generic directionalTransition helper
remains available for direction-only use.

For controlled visual progress, select `SceneLayoutSeekable(progress, transition)` as the graph's
sceneLayout. A null transition uses the target graph's sceneTransition, then the default.

- `progress = null`: animate automatically, or finish from the current fraction.
- `progress` between 0 and 1: seek the visual transition forward or backward.
- `progress = 1f`: complete the transition and dispose outgoing content.

Keep this layout mounted while adjusting progress. Source and target owners remain available
while their content is composed. Payload/result updates do not restart progress. This API
controls the visual transition after navigation has committed; moving back to zero does not
undo the command. Platform gestures and predictive Back are not included.

The implementation uses Compose's
[SeekableTransitionState](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SeekableTransitionState).

### API compatibility for these additions

Existing constructor/helpers and direction-only transition implementations remain usable from
Kotlin source. New optional parameters and interface methods can change binary compatibility;
recompile consumers. NavigationCommand adds ConsumeResult, so exhaustive consumer `when`
expressions over commands must handle it. Invalid direct deserialization now throws explicitly.
