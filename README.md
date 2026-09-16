# Kompass

[![Maven Central](https://img.shields.io/maven-central/v/com.tekmoon/kompass)](https://central.sonatype.com/artifact/com.tekmoon/kompass)
[![Kover](https://img.shields.io/badge/Kover-87.9%25%20class%20coverage-brightgreen)](#testing)
[![Android Weekly](https://img.shields.io/badge/Android%20Weekly-%23719-blue.svg)](https://androidweekly.net/issues/issue-719)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.10.0-blue?logo=jetpackcompose)](https://www.jetbrains.com/compose-multiplatform/)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-lightgrey)](#installation)

<p align="center">
  <strong>A predictable, testable navigation library for Compose Multiplatform.</strong><br/>
  It provides a shared Kotlin API for Android, iOS, Desktop and Web.
</p>

![Kompass overview](assets/KompassOverview.png)

[Watch the usage overview](https://github.com/user-attachments/assets/180b7805-c2e5-4b3f-9d61-570d7d092ac5)

## Contents

- [Why Kompass?](#why-kompass)
- [Kompass vs Navigation 3](#kompass-vs-navigation-3)
- [Glossary](#glossary)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Common operations](#common-operations)
- [Back handling](#back-handling)
- [Layouts and transitions](#layouts-and-transitions)
- [Metadata, scopes and results](#metadata-scopes-and-results)
- [Deep linking](#deep-linking)
- [Testing](#testing)
- [Documentation](#documentation)
- [Resources](#resources)
- [Contributing](#contributing)
- [License](#license)

## Why Kompass?

Kompass separates navigation state from rendering. Commands are reduced into immutable state, while configurable graphs and scene layouts render that state.

Highlights:

- immutable and serializable navigation state;
- type-safe destinations and arguments;
- multi-graph and nested-host navigation;
- lifecycle-aware scopes and automatic state ownership;
- navigation results, deep links and entry metadata;
- predictive Back with progress and cancellation;
- animated, seekable, multi-scene and adaptive layouts;
- optional resizable and rearrangeable panes;
- shared element transitions;
- Kover coverage with separate core and UI smoke-test workflows.


## Kompass vs Navigation 3

Both libraries provide an explicit back stack, Compose-driven rendering, deep links, serialization, results, predictive Back and adaptive layouts. Kompass differentiates itself through a multiplatform-first reducer core, built-in lifecycle scopes, serializable entry metadata, atomic stack commands, and a composite pane model that supports resizing and drag-and-drop rearrangement. Navigation 3 differentiates itself through first-party AndroidX integration and a broader set of ready-made Android adaptive strategies.

| Aspect | Kompass | Navigation 3 |
| --- | --- | --- |
| Platforms | Android, iOS, Desktop, Web | Android |
| Source of truth | Immutable state and a pure reducer | Developer-owned back stack of keys |
| Predictive Back | Built in, on every target | Android system integration |
| Scopes and ViewModels | Built in, lifecycle-aware, automatic cleanup | Via entry decorators |
| Deep links and results | Built in | Manual wiring |
| Pure navigation testing | Reducer tests need no Compose runtime | Usually requires state/UI setup |
| Rearrangeable panes | Composite pane tree with drag-and-drop docking | Application-defined |
| State persistence | The complete state is serializable | Keys must be saveable |
| Ecosystem | Standalone library | Official AndroidX, ready-made adaptive layouts |

Read the complete comparison in [Kompass vs Navigation 3](docs/kompass-vs-navigation3.md).

## Glossary

- **`Destination`** — the application's type-safe description of a screen or route.
- **`KompassEntry`** — one occurrence of a destination in the back stack, including arguments, metadata and scope identity.
- **`NavigationState`** — the immutable, serializable navigation document containing the current back stack.
- **`NavigationCommand`** — a data value describing a navigation operation such as `Navigate`, `Pop` or `ReplaceStack`.
- **`NavigationHandler`** — the pure reducer that applies commands to navigation state.
- **`KompassNavController`** — the API used by screens to dispatch commands and observe navigation.
- **`KompassNavigationGraph`** — resolves entries to destinations and renders their content.
- **`SceneLayout`** — decides which entries are visible and how they are arranged and animated.
- **`SceneTransition`** — defines the motion between source and target entries.
- **`NavigationScopeId`** — identifies state shared by entries in the same navigation flow.

For practical examples and the complete usage guide, see [Using Kompass](docs/usage.md).

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.tekmoon:kompass:2.0.0")
}
```

Add the dependency to the shared source set of your Compose Multiplatform application.

## Quick start

### Define destinations

```kotlin
sealed interface AppDestination : Destination {
    data object Home : AppDestination { override val id = "home" }

    data object Profile : AppDestination {
        override val id = "profile"

        // The result this destination returns. Declaring it here keeps the contract
        // next to the screen that fulfils it. See "Metadata, scopes and results".
        val Result = ResultKey<ProfileResult>("profile/result")
    }
}
```

### Create a graph

```kotlin
object AppGraph : KompassNavigationGraph {
    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId == AppDestination.Home.id || destinationId == AppDestination.Profile.id

    override fun resolveDestination(destinationId: String, args: String?): Destination =
        when (destinationId) {
            AppDestination.Home.id -> AppDestination.Home
            AppDestination.Profile.id -> AppDestination.Profile
            else -> error("Unknown destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController,
    ) {
        when (destination) {
            AppDestination.Home -> HomeScreen(navController)
            AppDestination.Profile -> ProfileScreen(navController)
            else -> error("Unsupported destination")
        }
    }
}
```

### Render the host and navigate

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberKompassNavController(AppDestination.Home)

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(AppGraph),
    )
}

@Composable
fun HomeScreen(navController: KompassNavController) {
    Button(onClick = {
        navController.navigate(AppDestination.Profile.toKompassEntry())
    }) {
        Text("Open profile")
    }
}
```

## Common operations

```kotlin
// Push one entry.
navController.navigate(Profile.toKompassEntry())

// Remove the current entry.
navController.pop()

// Remove several entries.
navController.pop(count = 2)

// Remove entries until a destination is reached.
navController.pop(popUntil = "home")

// Open a destination that must answer, then return the result while popping.
navController.navigateForResult(Profile.toKompassEntry(), Profile.Result)
navController.pop(result = ProfileResult(saved = true), resultKey = Profile.Result)

// Replace the complete stack in one state update.
navController.replaceStack(
    listOf(Home.toKompassEntry(), Profile.toKompassEntry())
)
```

Use `replaceStack` when restoring a complete flow or handling a multi-level deep link. It gives the application explicit control over the back stack in one state update.

## Back handling

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberKompassNavController(Home)

    KompassPredictiveBackHandler(navController)
    KompassNavigationHost(navController, persistentListOf(AppGraph))
}
```

`KompassPredictiveBackHandler` supports preview progress, commit and cancellation. Use `KompassBackHandler` for ordinary back handling. See the [predictive Back sample](samples/src/commonMain/kotlin/com/tekmoon/samples/NavSample9PredictiveBack.kt).

## Layouts and transitions

Layouts, transitions and input handlers are independent building blocks. The library provides ready-made implementations, but they are optional: a graph can use one of them, combine compatible capabilities, or implement its own `SceneLayout` and `SceneTransition`.

Available layouts include:

- `SceneLayoutSinglePane` for static content;
- `SceneLayoutDefaultAnimatedSinglePane` for standard transitions;
- `SceneLayoutPredictive` for gesture-driven Back;
- `SceneLayoutSeekable` for externally controlled progress;
- multi-scene and list-detail layouts for adaptive UIs;
- `SceneLayoutComposite` for opt-in pane resizing and drag-and-drop rearrangement.

For example, `SceneLayoutPredictive` already uses the seekable transition engine internally. It combines gesture-driven Back with progress-controlled rendering, while `SceneLayoutSeekable` lets the application provide the progress itself. A custom layout can choose which entries are visible, compose multiple panes, and connect its own gesture or progress source to the same navigation state.

Transitions are configurable per graph and can inspect source and target entries through `SceneTransitionContext`. For shared elements, use `KompassSharedTransitionHost` with matching Compose shared-content keys. Layouts are not mutually exclusive at the application level: an adaptive shell can select different layouts by window size, and a custom layout can combine behavior that is not covered by the built-ins.

## Metadata, scopes and results

`KompassEntry` carries `metadata: Map<String, String>` for presentation hints that belong to the shell rather than to screen arguments:

```kotlin
navController.navigate(
    Profile.toKompassEntry(
        args = profileArgsJson,
        metadata = mapOf("presentation" to "sheet"),
    )
)
```

Kompass transports and restores metadata but does not assign meaning to its keys. Use `defaultScope()` to share state for a destination within a controller, or `newScope()` for an isolated scope. A result is a request: `navigateForResult` opens it, `pop(result, resultKey)` answers it, and `peekResult` / `consumeResult` read and close it as `Pending`, `Delivered` or `Cancelled`. Back cancels an open request.

## Deep linking

A deep link is resolved into ordinary `NavigationCommand` values and then applied by the same navigation reducer used by normal navigation. This keeps deep links subject to the same back-stack and state invariants as user-driven navigation.

### Template-based links

`PathTemplateDeepLinkHandler` matches URI path segments and exposes captured values and query parameters through `DeepLinkMatch`:

```kotlin
val profileLink = PathTemplateDeepLinkHandler("app://profile/{userId}") { match ->
    listOf(
        NavigationCommand.ReplaceStack(
            listOf(
                AppDestination.Home.toKompassEntry(),
                AppDestination.Profile.toKompassEntry(args = match.args),
            )
        )
    )
}

val navController = rememberKompassNavController(
    initialState = defaultNavigationState(AppDestination.Home.toKompassEntry()),
    deepLinkHandlers = persistentListOf(profileLink),
)
```

For `app://profile/42?tab=orders`, `match["userId"]` is `"42"`, `match["tab"]` is `"orders"`, and `match.args` is serializer-generated JSON ready for `KompassEntry.args`. Path values take precedence over query parameters with the same name. Values are percent-decoded safely; use `buildArgs` or typed destination encoders rather than constructing JSON by string concatenation.

Return one `ReplaceStack` command for a multi-level link so the destination stack is applied in one state update. Returning several commands applies them sequentially and produces intermediate states and transitions.

### Applying links at runtime

When the URI arrives after the controller has been created, apply it directly:

```kotlin
val handled = navController.applyDeepLink(
    uri = uri,
    deepLinkHandlers = listOf(profileLink),
)
```

The method returns `true` when a handler resolves the URI and applies its commands, and `false` when no handler matches. Handlers are checked in registration order; the first match wins. Keep specific handlers before general ones.

### Custom handlers and platform events

For routes that do not fit a path template, implement the small platform-agnostic `DeepLinkHandler` interface:

```kotlin
val customLink = object : DeepLinkHandler {
    override fun matches(uri: String): Boolean = uri.startsWith("myapp://search")

    override fun resolve(uri: String): List<NavigationCommand> =
        listOf(NavigationCommand.Navigate(Search.toKompassEntry()))
}
```

Android intents, iOS scene callbacks and desktop URI handlers can forward their URI to shared code through `DeepLinkChannel`. The channel only transports the event; matching and navigation remain in the shared navigation layer.

## Testing

Navigation state is a plain, immutable value, and `NavigationHandler` is a pure reducer. A test can
call the reducer directly and check the resulting state, with no Compose runtime, no UI tree and no
instrumentation. This keeps most navigation logic in fast JVM unit tests.

Tests are split by responsibility:

- **Core tests** in `kompass/src/commonTest` cover reducers, state, commands, serialization, metadata, results, scopes, deep links and gesture state.
- **UI smoke tests** in platform test source sets cover hosts, layouts, transitions, tabs, predictive Back and ownership behavior.

Generate the local Kover report with:

```bash
./gradlew :kompass:koverHtmlReport
```

See [Coverage and Test Workflow](docs/coverage-workflow.md) for local validation, badge maintenance and iOS build guidance.

## Documentation

- [Using Kompass](docs/usage.md)
- [API v2 migration guide](docs/api-v2-migration.md)
- [Kompass vs Navigation 3](docs/kompass-vs-navigation3.md)
- [Coverage and Test Workflow](docs/coverage-workflow.md)
- [Samples](samples/src/commonMain/kotlin/com/tekmoon/samples/NavSampleRoot.kt)
- [Changelog](CHANGELOG.md)

## Resources

- [Compose Multiplatform documentation](https://www.jetbrains.com/compose-multiplatform/)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlinx Collections Immutable](https://github.com/Kotlin/kotlinx.collections.immutable)
- [GitHub Repository](https://github.com/3xcool/kompass)

## Contributing

Keep navigation logic deterministic and testable. New behavior should include the appropriate core test or UI smoke test. Run the relevant Gradle checks before opening a pull request.

## License

Kompass is licensed under the [Apache License 2.0](LICENSE).
