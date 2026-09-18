# Kompass 2.x API Migration

This guide covers two moves. Read the one you need:

- [Moving from 2.0 to 2.1](#moving-from-20-to-21). Navigation results change shape.
- [Moving from 1.x to 2.0](#moving-from-1x-to-20). The public helpers change name.

Coming from 1.x, apply both, in that order: first the names, then the results.

## Moving from 2.0 to 2.1

Kompass 2.1 makes a navigation result an explicit request. A request now has three outcomes, and the
caller opens it instead of the destination declaring it.

### What is new

- `ResultKey<T>` names a request and carries its expected type to the call site. Declare it on the
  destination that produces the result.
- `ResultState` reports the outcome: `Pending`, `Delivered(value)` or `Cancelled`. Back, predictive
  Back and a plain `pop` now end an open request as `Cancelled`. An application could not detect
  this before.
- `peekResult(key)` reads the state of a request while rendering. It never closes the request.
- `withResult(key, value)`, `withCancelledResult(key)` and `withPendingResult(key)` build an entry
  that already carries result state, for a `@Preview` or a screen test. The storage shape stays
  internal, so these take the public `ResultKey` and `NavigationResult` instead.
- `onNavigationError` on the controller factories reports a request that Kompass cannot honour.
  Kompass never throws for one of these, because a repeated tap produces them.
- `navigate(destination, ...)` and `replaceStack(destination, ...)` build the entry for you, so a
  plain `Destination` now reads the same as a typed one. Nothing forces you to migrate: the entry
  forms keep working, and you still need them to build a whole stack, the command list of a
  `DeepLinkHandler`, or an initial state.

  ```kotlin
  navController.navigate(Profile)                                  // new
  navController.navigate(Profile.toKompassEntry())                 // still valid
  navController.navigate(Profile, scopeId = NavigationScopeId("checkout"))
  ```

### API and signature changes

| Kompass 2.0 | Kompass 2.1 |
|---|---|
| `navigateForResult(entry, key)` | `navigate(entry, resultKey = key)` |
| `navigateTo(destination, pendingResultKey = ...)` | `navigateTo(destination, resultKey = key)` |
| `pop(result, key, count, popUntil)` | `pop(result, resultKey = key)` |
| `consumeResult(key, entryId): T?` | `consumeResult(key, entryId): ResultState<T>?` |
| `entry.results[name]` | `entry.peekResult(key)` |
| `KompassEntry(id, args, scope, metadata, pendingResultKey, results)` | `KompassEntry(id, args, scope, metadata)` |
| `replaceRoot(...)` and `NavigationCommand.ReplaceRoot` | `replaceStack(...)` |

### Behavioral changes

- `KompassEntry.pendingResultKey` keeps its name and changes its owner. It used to sit on the
  destination that returns a result, and it routed the answer. It now sits on the entry that waits,
  and it records only that. The key that routes an answer travels with `pop`.
- `KompassEntry.results` is internal. Read a result with `peekResult` and close it with
  `consumeResult`.
- The `KompassEntry` constructor stays public and drops its `pendingResultKey` and `results`
  parameters. It keeps `destinationId`, `args`, `scopeId` and `metadata`, the same four fields
  `toKompassEntry` and `kompassEntry` take.
- `KompassEntry` drops `component4()` and `component5()`. Destructuring covers the first three.
- `KompassEntry.metadata` is an `ImmutableMap<String, String>`. Reading it is unchanged. Code that
  assigned it to a `MutableMap` variable no longer compiles.
- `NavigationCommand.ReplaceStack.entries` is an `ImmutableList`. A second constructor takes a plain
  `List`, so `ReplaceStack(listOf(...))` keeps working.
- `pop(result, key)` takes no `count` or `popUntil`. A result only reaches the entry one step below.
- A result delivered to an entry that opened no request is refused whole. Nothing pops, nothing is
  stored, and `onNavigationError` reports the reason.
- Do not combine `resultKey` with `clearBackStack`, or with an inclusive `popUpTo` that removes the
  last entry. Both remove the entry that would receive the answer. Kompass drops the request and
  reports it.
- Saved state written by 2.0 does not restore when it holds a result. See
  [Saved state](#saved-state) below.

### Example

Before:

```kotlin
navController.navigateForResult(Profile.toKompassEntry(), "profile/result")
// in the destination
navController.pop(result = ProfileResult(userId = "123"), resultKey = "profile/result")
// in the caller
val userId = navController.consumeResult<ProfileResult>("profile/result")?.userId
```

After:

```kotlin
data object Profile : Destination {
    override val id = "app/profile"
    val Result = ResultKey<ProfileResult>("profile/result")
}

navController.navigate(Profile.toKompassEntry(), resultKey = Profile.Result)
// in the destination
navController.pop(result = ProfileResult(userId = "123"), resultKey = Profile.Result)
```

The caller reads the outcome from an effect, and handles a cancellation it could not see before:

```kotlin
val request = entry.peekResult(Profile.Result)

LaunchedEffect(request) {
    when (val closed = navController.consumeResult(Profile.Result, entry.id)) {
        is ResultState.Delivered -> userId = closed.value.userId
        ResultState.Cancelled -> userId = null
        ResultState.Pending, null -> Unit
    }
}
```

### Saved state

Kompass 2.1 stores a closed request in an internal envelope instead of a bare `NavigationResult`, so
an entry saved by 2.0 that holds a result fails to decode. The controller reports the failure and
falls back to its initial state. It never restores half a stack.

A restored 2.0 entry that carries `pendingResultKey` arrives with a marker for a request that no
longer exists, because the field kept its name and changed owner. The marker is harmless. It sits on
a producer, whose screen never reads it, and it leaves the stack with that entry.

Kompass 2.1 carries no reader for the 2.0 shape. State written by 2.1 restores in full, results
included.

### Migration checklist

1. Update the Kompass dependency to 2.1.0.
2. Declare a `ResultKey<T>` on each destination that produces a result.
3. Replace `navigateForResult` with `navigate(..., resultKey = ...)`.
4. Remove `count` and `popUntil` from every `pop` that delivers a result.
5. Handle `ResultState.Cancelled` at each call site that reads a result.
6. Replace `replaceRoot` with `replaceStack`.
7. Drop the `pendingResultKey` and `results` arguments from every direct `KompassEntry(...)` call.
8. Pass `onNavigationError` to the controller factory, and log what it reports.
9. Run:

   ```bash
   ./gradlew :kompass:jvmTest :kompass:koverVerify :samples:compileKotlinJvm
   ```

## Moving from 1.x to 2.0

Kompass 2.0 makes the ownership of its most commonly used public helpers explicit. This is a
breaking API change: update imports and call sites when moving from 1.x to 2.0.

### Features added since 1.2.0

Kompass 2.0 is more than a naming release. The following capabilities were added after 1.2.0:

- Atomic stack replacement with `NavigationCommand.ReplaceStack` and `replaceStack`.
- Presentation metadata on `KompassEntry`, serialized with navigation state.
- Typed destinations and arguments through `TypedDestination<T>` and serializers.
- Path-template deep links with safe `buildArgs` encoding.
- Predictive Back, gesture state, and edge-swipe fallback across supported targets.
- Seekable and source/target-aware transitions.
- Shared-element transitions through `KompassSharedTransitionHost`.
- Multiple tab models, including root reuse and independent per-tab controllers.
- Adaptive and multi-pane layouts, including the serializable composite pane tree with resize handles
  and drag-and-drop docking.
- Expanded lifecycle, nested-host, restoration, saveable-state, and shared-scope behavior.
- Immutable public back-stack access through `ImmutableList`.

Consumers should specifically verify back handling, restoration, deep links, typed arguments, and
custom layouts during the migration.

### API and signature changes

#### Core types

| Kompass 1.2.0 | Kompass 2.0 |
|---|---|
| `NavController` | `KompassNavController` |
| `BackStackEntry` | `KompassEntry` |
| `NavigationGraph` | `KompassNavigationGraph` |
| `List<BackStackEntry>` from `backStack` | `ImmutableList<KompassEntry>` |

`KompassEntry` now carries `metadata: Map<String, String> = emptyMap()`. Direct constructors and
copying code should preserve or intentionally set this field.

#### Factories and typed helpers

| Kompass 1.2.0 | Kompass 2.0 |
|---|---|
| `rememberNavController(...)` | `rememberKompassNavController(...)` |
| `createNavController(...)` | `createKompassNavController(...)` |
| `Destination.toBackStackEntry(...)` | `Destination.toKompassEntry(...)` |
| `NavController.replaceRootTo(destination, ...)` | `NavController.replaceRoot(destination, ...)` |
| `NavController.replaceStackTo(destination, ...)` | `NavController.replaceStack(destination, ...)` |

These helper names remain unchanged: `newScope`, `defaultScope`, `buildArgs`, `applyDeepLink`,
`encodeArgs`, `argsFrom`, `argsOrNull`, `requireArgs`, and `navigateTo`.

#### Behavioral changes

- `backStack` is immutable; consumers must not mutate the returned list.
- Repeated entries receive distinct occurrence IDs, keeping ownership and UI state independent.
- `replaceStack` applies a complete stack atomically rather than publishing intermediate states.
- Restoration includes metadata, arguments, pending results, and occurrence identity; live ViewModels
  and arbitrary in-memory objects are not serialized.
- Predictive Back progress is visual state and changes committed navigation only after completion.
- Composite pane arrangements are serializable; use the saveable composite state helper for process
  death restoration.

### Example

Before:

```kotlin
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry

val navController = rememberNavController(Home)
navController.navigate(Profile.toBackStackEntry())
```

After:

```kotlin
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassEntry

val navController = rememberKompassNavController(Home)
navController.navigate(Profile.toKompassEntry())
```

In 2.0 the core `KompassNavController` methods remain unchanged: `navigate`, `pop`, `replaceRoot`,
`replaceStack`, `runNavCommands`, `canGoBack`, and `close`. The typed `replaceRoot` and
`replaceStack` helpers share those names as overloads.

Kompass 2.1 removes `replaceRoot`. Go straight to `replaceStack` if you move to 2.1 in the same
step.

### Migration checklist

1. Update the Kompass dependency to 2.0.
2. Replace the three renamed imports and call sites.
3. Replace `replaceRootTo` with `replaceRoot` and `replaceStackTo` with `replaceStack`. Use
   `replaceStack` for both if you move to 2.1 in the same step.
4. Update samples, tests, previews, and documentation.
5. Run:

   ```bash
   ./gradlew :kompass:jvmTest :kompass:koverVerify :samples:compileKotlinJvm
   ```

