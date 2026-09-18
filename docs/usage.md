# Using Kompass

This guide explains the most common Kompass navigation patterns. For migration from Kompass 2.0 or 1.x, see the [API v2 migration guide](api-v2-migration.md). For a design comparison, see [Kompass vs Navigation 3](kompass-vs-navigation3.md).

## 1. Model destinations

A destination owns a stable ID. Use a sealed hierarchy when the application wants exhaustive type checking:

```kotlin
sealed interface AppDestination : Destination {
    data object Home : AppDestination { override val id = "home" }
    data object Profile : AppDestination { override val id = "profile" }
}
```

For destinations with typed arguments, use `TypedDestination<T>`:

```kotlin
@Serializable
data class ProfileArgs(val userId: String)

object Profile : TypedDestination<ProfileArgs> {
    override val id = "profile"
    override val argsSerializer = ProfileArgs.serializer()
}
```

## 2. Create a graph

Graphs resolve destination IDs and render the resolved content:

```kotlin
object AppGraph : KompassNavigationGraph {
    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId == "home" || destinationId == "profile"

    override fun resolveDestination(destinationId: String, args: String?): Destination =
        when (destinationId) {
            "home" -> AppDestination.Home
            "profile" -> AppDestination.Profile
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
            AppDestination.Profile -> ProfileScreen(navController, entry)
            else -> error("Unsupported destination")
        }
    }
}
```

Applications can register multiple graphs. This is useful when feature modules own their destinations independently.

## 3. Create and render a controller

Use `rememberKompassNavController` when the controller belongs to the composition:

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberKompassNavController(AppDestination.Home)

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(AppGraph),
    )
}
```

Use `createKompassNavController` when the controller is owned outside composition. The owner must call `close()` when the controller is no longer needed.

## 4. Navigate

`navigate` pushes an entry and supports common stack policies:

```kotlin
// Basic navigation. Kompass builds the entry.
navController.navigate(Profile)

// Clear the complete stack before pushing.
navController.navigate(Profile, clearBackStack = true)

// Pop up to a destination before pushing.
navController.navigate(
    Profile,
    popUpTo = "home",
    popUpToInclusive = false,
)

// Move an existing matching entry to the top instead of creating an occurrence.
navController.navigate(Profile, reuseIfExists = true)

// Set args, a scope or presentation metadata on the way.
navController.navigate(Profile, scopeId = NavigationScopeId("checkout"))

// Pass an entry instead when you built one yourself.
navController.navigate(Profile.toKompassEntry())
```

The complete signature is:

```kotlin
navController.navigate(
    destination,               // or an entry you built yourself
    args = null,               // destination form only
    scopeId = destination.defaultScope(),   // destination form only
    metadata = emptyMap(),     // destination form only
    clearBackStack = false,
    popUpTo = null,
    popUpToInclusive = false,
    reuseIfExists = false,
    resultKey = null,          // opens a result request; see section 8
)
```

Without `reuseIfExists`, navigating to the same destination creates a new back-stack occurrence. Each occurrence has its own entry identity and UI lifecycle; entries can still share a `scopeId`.

## 5. Pop and go back

`pop` removes entries from the top of the stack:

```kotlin
// Pop one entry.
navController.pop()

// Pop one entry and deliver a result to the entry below. See section 8.
navController.pop(result = ProfileResult(userId = "123"), resultKey = Profile.Result)

// Pop multiple entries.
navController.pop(count = 2)

// Pop until the named destination becomes the top entry.
navController.pop(popUntil = "home")
```

A `count` below 1 pops nothing, so a computed count never removes a screen you did not ask for. A
`count` above 1 cannot be combined with `popUntil`: the two name different stops, and one would have
to be ignored. Passing both throws.

Every pop closes an open result request. A plain `pop`, Back and predictive Back all end the request
as `ResultState.Cancelled`, so the entry below can tell "the user gave up" from "the screen is still
open".

Use `canGoBack()` to check the stack or `popIfCan` to provide a root fallback:

```kotlin
navController.popIfCan(onFailure = onExit)
```

## 6. Replace the stack

`replaceStack` replaces the entire stack in one state update. The list is ordered from root to active entry and must not be empty:

```kotlin
navController.replaceStack(
    listOf(
        Home.toKompassEntry(),
        Profile.toKompassEntry(args = profileArgsJson),
    )
)
```

Use it for restored flows, server-provided routes and multi-level deep links. Building the same flow with multiple `navigate` calls publishes intermediate states and runs multiple transitions.

## 7. Arguments and metadata

Opaque arguments can be built safely with `buildArgs`:

```kotlin
val args = buildArgs {
    put("userId", userId)
    put("tab", 2)
}

navController.navigate(Profile.toKompassEntry(args = args))
```

Typed destinations can use their serializer helpers:

```kotlin
navController.navigateTo(Profile, ProfileArgs(userId = "123"))
val profileArgs = navController.requireArgs(Profile, entry)
```

`KompassEntry.metadata` carries presentation hints that belong to the shell rather than the screen:

```kotlin
val entry = Profile.toKompassEntry(
    metadata = mapOf("presentation" to "sheet")
)
```

Kompass persists metadata but does not define its keys or interpret their values.

## 8. Results

A result is a **request**, not a callback. One entry opens the request, the destination it opens
answers it or leaves without answering, and the request ends. All of it lives in the back stack, so
it survives a configuration change and a process death.

### Declare the contract on the destination that produces it

```kotlin
object Profile : Destination {
    override val id = "profile"
    val Result = ResultKey<ProfileResult>("profile/result")
}
```

`ResultKey<T>` carries the expected type to the call site. Only its name enters the navigation
state; `T` is compile-time information. Two keys with the same name are the same key at run time, so
declaring the key on its producer keeps the name unique without a convention to remember.

### Open, answer, close

```kotlin
// A opens the request.
navController.navigate(Profile.toKompassEntry(), resultKey = Profile.Result)

// Profile answers it.
navController.pop(result = ProfileResult(userId = "123"), resultKey = Profile.Result)
```

The `resultKey` of `navigate` is what makes the request exist. A `navigate` without it, followed by
`pop(result, key)`, delivers nothing: the entry below is not waiting, so Kompass rejects the delivery
and reports it. That is deliberate — without a record of who is waiting, Back could not be told
apart from "the screen is still open". `navigateTo` takes the same parameter for a typed destination.

A request needs an entry that stays directly below the new one, because that is the only entry a
`pop` can answer. Three combinations remove it, and each one drops the request and reports it:

| Combination | Why it cannot work |
| --- | --- |
| `resultKey` with `clearBackStack` | Nothing is left to receive the answer. |
| `resultKey` with an inclusive `popUpTo` that removes the last entry | Same, by a different route. |
| `resultKey` with `reuseIfExists` on the caller's own destination | The caller moves to the top, so it would be asking itself. |

A plain `popUpTo` is safe, and the request lands on whichever entry ends up below the new one.
`pop(result, resultKey)` takes no `count` or `popUntil`, because a result only reaches the entry one
step below.

`reuseIfExists` carries result state with the occurrence it moves. An answer the screen has not read
yet survives the move, the same way its ViewModel and its UI state do.

### Read the request

`peekResult` returns the state without changing it, and `consumeResult` closes it:

```kotlin
var userId by rememberSaveable { mutableStateOf<String?>(null) }

val request = entry.peekResult(Profile.Result)

LaunchedEffect(request) {
    when (val closed = navController.consumeResult(Profile.Result, entry.id)) {
        is ResultState.Delivered -> userId = closed.value.userId
        ResultState.Cancelled -> userId = null
        ResultState.Pending, null -> Unit
    }
}
```

| State | Meaning |
|-------|---------|
| `ResultState.Pending` | The request is open. The destination is still on the stack. |
| `ResultState.Delivered(value)` | The destination answered. |
| `ResultState.Cancelled` | The destination left without answering: Back, predictive Back or a plain `pop`. |
| `null` | This entry has no request for that key, or the delivered value has another type. |

`consumeResult` returns `Delivered` or `Cancelled` once and removes it. It never returns `Pending`:
an open request is left alone and reported as `null`, so a later call still collects the answer.
Call it from an effect or an event handler, never while rendering — it dispatches a command, and a
command dispatched during composition is a side effect in the render pass.

The effect above restarts when the request changes, closes it once, and restarts with `null` after
the removal. The second pass finds nothing and does no work.

### Previews and screen tests

At run time only the reducer writes result state, so a `@Preview` of the "answer arrived" screen had
to drive a whole navigation to reach it. Three builders write it directly, one per `ResultState`:

```kotlin
val waiting   = Checkout.toKompassEntry().withPendingResult(Address.Result)
val answered  = Checkout.toKompassEntry().withResult(Address.Result, AddressResult("221B Baker St"))
val abandoned = Checkout.toKompassEntry().withCancelledResult(Address.Result)

@Preview
@Composable
private fun CheckoutWithAddress() {
    CheckoutScreen(entry = answered)
}
```

They keep occurrence identity and return a copy, so the entry you started from is unchanged. Use them
in a preview or a test. Production code opens a request with the `resultKey` of `navigate` and closes
it with `pop`.

### Rules at the edges

- **A repeated request under the same key replaces the previous one.** The navigation happens, and
  an answer or a cancellation that was never consumed is dropped and reported through
  `onNavigationError`. Refusing the navigation instead would leave a button that does nothing.
  A result survives until it is consumed, including across a process death, so a state still sitting
  there at this point means the screen never called `consumeResult`. The report names that screen.
- **A rejected delivery changes nothing and is reported.** The pop does not happen either. Kompass
  calls `onNavigationError` with a `NavigationResultException` and leaves the back stack alone. It
  never throws, because the second `pop` of a double tap is one of these — and refusing the pop is
  what stops that second tap from taking an extra screen with it.
- **A multi-entry `pop` or a `popUntil` cancels the request of the entry it reveals.** The
  destination that had to answer is gone either way. A `popUntil` that names the entry already on
  top removes nothing, so it closes no request.
- **`replaceStack` never cancels.** A request on an entry the new stack keeps stays open; a request
  on an entry the new stack drops disappears with it.

### Serialization

Result types must be `@Serializable` and registered in the controller's `SerializersModule`:

```kotlin
@Serializable
@SerialName("profile-result")
data class ProfileResult(val userId: String) : NavigationResult

val resultSerializers = SerializersModule {
    polymorphic(NavigationResult::class) { subclass(ProfileResult::class) }
}
```

An unregistered result type fails when the state is saved, rather than silently dropping the data.
`Pending`, `Delivered` and `Cancelled` all survive restoration until they are consumed.

## 9. Restoration and external controllers

`rememberKompassNavController` restores navigation state through Compose saveable state. For a
controller owned outside composition, save and restore it explicitly:

```kotlin
val saved = controller.saveNavigationState()
val restored = createKompassNavController(
    startDestination = Home,
    savedNavigationState = saved,
    onRestoreFailure = ::reportNavigationFailure,
)
```

`saveNavigationState()` serializes the back stack, arguments, metadata and result requests only. It
does not serialize live ViewModels, `SavedStateHandle` values, or Compose UI state.

Invalid saved navigation, including an empty stack or duplicate occurrence IDs, falls back to a valid
initial state by default. `restorationFailure` retains the cause, and `onRestoreFailure` reports it once
per restoration; in composition, the callback runs from an effect. Select `NavigationRestorePolicy.Throw`
to reject restoration instead of falling back. Restoration does not check whether the application's
graphs still support every saved destination — graph resolution remains the application's routing
contract.

An externally owned controller must call `close()` when it is no longer needed; unmounting its host
temporarily does not close it. `stateFlow` is read-only and conflated, so it reflects the current state
rather than every intermediate command:

```kotlin
val controller = createKompassNavController(
    Home,
    serializersModule = resultSerializers,
    savedNavigationState = previouslySavedJson,
    onRestoreFailure = { cause -> reportNavigationFailure(cause) },
)
controller.stateFlow.collect { state -> /* observe the current immutable stack */ }
```

Mutation, saving and `close()` must run on the UI thread. Flow collection can use another dispatcher.

## 10. Scopes and state ownership

Use `rememberScoped` for lifecycle-aware objects associated with navigation scopes:

```kotlin
val viewModel = rememberScoped<ProfileViewModel>(
    scopeId = entry.scopeId,
    factory = ::ProfileViewModel,
    onCleared = { it.close() },
)
```

- `defaultScope()` shares state for repeated visits to a destination within one controller.
- `newScope()` creates isolated state for a new navigation occurrence.
- An explicit shared `NavigationScopeId` lets several entries use the same scoped object.

Cleanup follows the back stack, so the ID you pass decides the lifetime:

| Scope | Cleared by |
|-------|------------|
| `entry.scopeId`, `defaultScope()`, `newScope()`, or any ID an entry carries | Kompass, once the last entry using it leaves the back stack of the last controller that carries it. |
| An ID no entry carries, named for a flow | Nobody. It is a process-wide singleton until you call `NavigationScopes.clearScope(id)`. |

The scope store is process-wide, so two controllers can name the same scope. `defaultScope()` gives
the same ID to the same destination everywhere, which is exactly what two tabs of one screen want.
Each controller holds the scopes its back stack carries, and only the last holder to let go clears
it. Closing one controller therefore never destroys a ViewModel another controller still shows.

`NavigationScopes.clearScope` ignores that count. It is the explicit override for a manual scope you
own, not a way to clear a scope entries carry.

Prefer the first form. For the second, give the scope an owner where the flow ends:

```kotlin
private val CheckoutScope = NavigationScopeId("flow:checkout")

DisposableEffect(CheckoutScope) {
    onDispose { NavigationScopes.clearScope(CheckoutScope) }
}
```

`NavSample3ViewModelScope` and `NavSample4Transitions` show the manual form with its disposal;
`NavSample2InnerGraphs` shows the automatic one, where the flow ID is the entry's own `scopeId`.

Entry UI state and scope state are separate. Reusing an entry can preserve its occurrence and UI state; sharing a scope shares scoped objects without merging entry lifecycles.

### Owners provided by the host

`KompassNavigationHost` provides owners automatically. Each back-stack occurrence has its own
`LifecycleOwner` and `SavedStateRegistryOwner`; entries with the same `scopeId` share a `ViewModelStore`
and `SavedStateHandle` storage within their controller. Screens need no ownership wrappers, and the
core library has no Koin dependency.

Use `viewModel()` or `koinViewModel()` inside graph content. Android supports the standard
`SavedStateViewModelFactory`, including `SavedStateHandle` constructors. Desktop supports no-arg and
`SavedStateHandle` constructors. On iOS and Wasm, supply an initializer or factory, as usual on
platforms without constructor reflection.

Entry owners remain alive while the entry is in the back stack or its content is still composed.
Removing an entry destroys its lifecycle after exit disposal. A shared ViewModel is cleared exactly
once, after the last entry of its scope is removed and its outgoing content is disposed — removing the
entry that originally created a ViewModel does not clear it while another entry still uses the scope.
Covered entries that are no longer rendered stay at `CREATED`; rendered non-top entries are `STARTED`;
the top entry can reach `RESUMED`, capped by the host lifecycle. `STARTED` still permits the default
`collectAsStateWithLifecycle` collection.

The host also wraps each entry in a `SaveableStateProvider`, so `rememberSaveable` state survives a
push-and-return; a new visit after a pop starts fresh.

### Reusing an entry versus sharing a scope

These are separate choices:

| Choice | Effect |
| --- | --- |
| `reuseIfExists = false` | Adds a new occurrence with its own UI state and lifecycle. Its ViewModels are shared when another entry in the controller uses the same scope. |
| `reuseIfExists = true` | Moves the last matching destination to the top, keeping other entries in their existing order, and applies the incoming arguments. With the same scope it retains the occurrence ID, its UI state and its result state; a different scope creates fresh entry state and uses that scope's ViewModels, creating them if the scope is new. |
| `defaultScope()` | Shares ViewModels across visits to the same destination within a controller. |
| `newScope()` | Isolates ViewModels for one occurrence. |
| Explicit shared `scopeId` | Shares a ViewModel across a flow of different routes. The ViewModel class or key must also match; factory parameters apply only on creation. |

For example, reusing `B` in `A → B → C` produces `A → C → B`, not `A → B`.

`KompassEntry.id` is a read-only occurrence key generated by the library, with no constructor or
`copy` parameter. It is saved with the stack and preserved when arguments or results are copied.
Copying to a different destination or scope creates a new identity. In a custom `AnimatedContent`
layout, use `contentKey = { it.id }` so updating an entry's arguments or results does not animate two
copies of the same occurrence.

## 11. Tabs, and the two models

Two different products hide behind the word "tabs". Decide which one the application needs first.

### Reorder model — one controller

Tapping a tab moves its entry to the top. Back then walks the visit history across tabs, so it never
lies about where the user came from. This is what YouTube and Instagram do. The whole bottom bar is one
line:

```kotlin
onClick = { navController.navigate(tab.toKompassEntry(), reuseIfExists = true) }
```

`reuseIfExists` retains the occurrence ID, so the moved entry keeps its ViewModel and its
`rememberSaveable` state. Its limit: it moves one entry, not a segment. `Home → Profile → ProfileDetail`,
then tab Home, then tab Profile lands on `Profile`, not back on `ProfileDetail`. That is correct for
this model.

### Per-tab model — one controller for each tab

Every tab keeps its own depth. Create each controller outside composition with
`createKompassNavController`, give each tab its own host, and compose only the active one:

```kotlin
val controllers = remember { tabs.associateWith { createKompassNavController(it) } }
DisposableEffect(controllers) {
    // An externally owned controller is released by close(), never by leaving composition.
    onDispose { controllers.values.forEach { it.close() } }
}

controllers.forEach { (tab, controller) ->
    if (tab == active) KompassNavigationHost(controller, graphs)
}
```

An inactive tab keeps its stack, its ViewModels and its UI state while its host is unmounted.

Do not write one host and swap its `navController`. That is not the same as unmounting a host: changing
the controller of a mounted host reconciles the outgoing controller's entry owners away and clears its
ViewModels.

See [Sample 11](../samples/src/commonMain/kotlin/com/tekmoon/samples/NavSample11Tabs.kt), which switches
between the two models, counts visits per tab, and prints the stack so the Back history is visible.

## 12. Deep links

A deep link handler converts a URI into ordinary navigation commands:

```kotlin
val profileLink = PathTemplateDeepLinkHandler("app://profile/{userId}") { match ->
    listOf(
        NavigationCommand.ReplaceStack(
            listOf(
                Home.toKompassEntry(),
                Profile.toKompassEntry(args = match.args),
            )
        )
    )
}
```

Register handlers when creating the controller:

```kotlin
val navController = rememberKompassNavController(
    startDestination = Home,
    deepLinkHandlers = persistentListOf(profileLink),
)
```

Or apply a URI after creation:

```kotlin
val handled = navController.applyDeepLink(uri)
```

Path placeholders and query parameters are percent-decoded and exposed through `DeepLinkMatch`. For
example, `app://profile/123?tab=orders` provides `userId = "123"`, `tab = "orders"`, and serializer-
generated JSON in `match.args`. Path values take precedence over query values with the same name.

Handlers are checked in order and the first match wins. Implement `DeepLinkHandler` directly for routes
that need custom parsing. Platform code can deliver Android intents, iOS callbacks or desktop URI events
through `DeepLinkChannel`; matching and navigation remain in shared code.

`DeepLinkChannel.observe` returns a `DeepLinkSubscription`. Keep it and cancel it when the observer
goes away, then `close()` the channel when its owner does:

```kotlin
val subscription = deepLinkChannel.observe { uri -> navController.applyDeepLink(uri) }
// later
subscription.cancel()
deepLinkChannel.close()
```

One channel carries each URI to exactly one collector, so an abandoned subscription does not just
leak, it also steals links from the collector that replaced it. A desktop JVM host without a main
dispatcher artifact can pass its own dispatcher to the constructor.

## 13. Back handling and predictive Back

Use the controller-aware handler with the predictive layout:

```kotlin
KompassPredictiveBackHandler(navController)

object AppGraph : KompassNavigationGraph {
    override val sceneLayout = SceneLayoutPredictive()
}
```

Predictive Back previews the previous entry, reports progress, commits a pop on completion and restores
the current screen when cancelled. Use `KompassBackHandler` for ordinary back events. Do not enable both
handlers for the same action — they compete for the same system back event.

| API | Use |
| --- | --- |
| `KompassBackHandler` | Handle an ordinary back event with your own `onBack` callback; also accepts a `BackPressedChannel`. |
| `KompassPredictiveBackHandler` | Connect platform back events to a `KompassNavController`; manages preview progress, commits a pop, and handles cancellation. |
| `PlatformPredictiveBackHandler` | Low-level platform callbacks (`onStart`, `onProgress`, `onCommit`, `onCancel`). Use for custom integration; it does not pop a controller by itself. |
| `PlatformBackHandler` | Deprecated. Replace each call with `KompassBackHandler`, which keeps the same parameters. |

### Platform support

- **Android API 34+**: streams the system back gesture, including progress and cancellation.
- **Earlier Android versions**: handles an ordinary back press without streamed progress.
- **iOS**: uses the native Compose edge-swipe dispatcher, including progress and cancellation. Keep back
  gestures enabled in `ComposeUIViewController` (do not set `enableBackGesture = false`).
- **Desktop and web**: `SceneLayoutPredictive` supports a primary mouse or touch drag starting in the
  leftmost 24 dp of the navigation area. Release beyond 35% of its width, or swipe forward quickly, to
  commit; drag back to cancel. **ESC** performs an ordinary animated back through the Compose window's
  dispatcher.

Web edge drags apply to the Kompass navigation area only. They do not synchronize the browser's History
API, address bar, toolbar Back button, or browser-owned trackpad gestures. A host with browser-history
routing must integrate those events separately and avoid navigating twice.

See the [predictive Back sample](../samples/src/commonMain/kotlin/com/tekmoon/samples/NavSample9PredictiveBack.kt).

## 14. Layouts and custom rendering

Set `sceneLayout` on a graph. Built-in layouts include static single pane, animated single pane,
predictive, seekable, list-detail, multi-scene and composite layouts. `SceneLayoutPredictive` already
uses the seekable engine internally; `SceneLayoutSeekable` is for application-controlled progress.

These are independent building blocks, not a mandatory framework. An application can combine a layout
with a custom transition, choose different layouts for different window sizes, or implement
`SceneLayout` to render a completely custom arrangement. `SceneLayoutComposite` adds optional resize
handles and drag-and-drop pane rearrangement without changing back-stack behavior.

For shared elements, replace `KompassNavigationHost` with `KompassSharedTransitionHost` and use matching
Compose shared-content keys in both destinations.

## 15. Seekable transitions and controlled progress

Direction follows the command that changes the active occurrence: `Navigate` and `ReplaceStack` are
`Push`, `Pop` is `Pop`. Consuming results, updating the current occurrence and no-op commands do not
overwrite the last direction.

Override `SceneTransition.transition(context: SceneTransitionContext)` to inspect source and target
entries, including their destination IDs and arguments. Built-in animated layouts supply this context.
Custom layouts can use `entryTransition(direction, transition)` or the destination-aware overload
`entryTransition(direction, resolve, transition)`.

For controlled visual progress, select `SceneLayoutSeekable(progress, transition)` as the graph's
`sceneLayout`. A `null` transition uses the target graph's `sceneTransition`, then the default.

- `progress = null`: animate automatically, or finish from the current fraction.
- `progress` between 0 and 1: seek the visual transition forward or backward.
- `progress = 1f`: complete the transition and dispose outgoing content.

Keep this layout mounted while adjusting progress. Source and target owners remain available while
their content is composed. This API controls the visual transition after navigation has committed;
moving back to zero does not undo the command. For platform-driven predictive Back before navigation
commits, use `SceneLayoutPredictive` with `KompassPredictiveBackHandler` instead — see
[Back handling and predictive Back](#13-back-handling-and-predictive-back).

The implementation uses Compose's
[SeekableTransitionState](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SeekableTransitionState).

## 16. Persistence and restoration

`rememberKompassNavController` restores navigation state through Compose saveable state. For an
externally owned controller, use `saveNavigationState()` and pass the result as `savedNavigationState`
when creating it:

```kotlin
val saved = controller.saveNavigationState()
val restored = createKompassNavController(
    startDestination = Home,
    savedNavigationState = saved,
    onRestoreFailure = ::reportNavigationFailure,
)
```

Navigation payloads, arguments, metadata and result requests are serializable. Live ViewModels, scopes
and arbitrary in-memory objects are not serialized. On Android, an enclosing ViewModelStore retains
entry owners across Activity recreation; permanent controller disposal releases them. Other targets
release entry owners when the controller composition leaves.

## 17. Thread safety

- Navigation state is immutable and thread-safe.
- Call navigation and `NavigationScopes` operations on the UI thread.
- Volatile map publication does not make compound operations or stored objects thread-safe.

## 18. Testing

Keep reducer and navigation-contract tests in `kompass/src/commonTest`. Use platform UI smoke tests for host ownership, layouts, transitions, tabs and predictive Back. Generate the local Kover report with:

```bash
./gradlew :kompass:koverHtmlReport
```

See [Coverage and Test Workflow](coverage-workflow.md) for the local validation workflow and iOS build guidance.
