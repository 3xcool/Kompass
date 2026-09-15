# Kompass 2.0 API Migration

Kompass 2.0 makes the ownership of its most commonly used public helpers explicit. This is a
breaking API change: update imports and call sites when moving from 1.x to 2.0.

## Features added since 1.2.0

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

## API and signature changes

### Core types

| Kompass 1.2.0 | Kompass 2.0 |
|---|---|
| `NavController` | `KompassNavController` |
| `BackStackEntry` | `KompassEntry` |
| `NavigationGraph` | `KompassNavigationGraph` |
| `List<BackStackEntry>` from `backStack` | `ImmutableList<KompassEntry>` |

`KompassEntry` now carries `metadata: Map<String, String> = emptyMap()`. Direct constructors and
copying code should preserve or intentionally set this field.

### Factories and typed helpers

| Kompass 1.2.0 | Kompass 2.0 |
|---|---|
| `rememberNavController(...)` | `rememberKompassNavController(...)` |
| `createNavController(...)` | `createKompassNavController(...)` |
| `Destination.toBackStackEntry(...)` | `Destination.toKompassEntry(...)` |
| `NavController.replaceRootTo(destination, ...)` | `NavController.replaceRoot(destination, ...)` |
| `NavController.replaceStackTo(destination, ...)` | `NavController.replaceStack(destination, ...)` |

These helper names remain unchanged: `newScope`, `defaultScope`, `buildArgs`, `applyDeepLink`,
`encodeArgs`, `argsFrom`, `argsOrNull`, `requireArgs`, and `navigateTo`.

### Behavioral changes

- `backStack` is immutable; consumers must not mutate the returned list.
- Repeated entries receive distinct occurrence IDs, keeping ownership and UI state independent.
- `replaceStack` applies a complete stack atomically rather than publishing intermediate states.
- Restoration includes metadata, arguments, pending results, and occurrence identity; live ViewModels
  and arbitrary in-memory objects are not serialized.
- Predictive Back progress is visual state and changes committed navigation only after completion.
- Composite pane arrangements are serializable; use the saveable composite state helper for process
  death restoration.

## Example

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

The core `KompassNavController` methods remain unchanged: `navigate`, `pop`, `replaceRoot`, `replaceStack`,
`runNavCommands`, `canGoBack`, and `close`. The typed `replaceRoot` and `replaceStack` helpers now
share those names as overloads.

## Migration checklist

1. Update the Kompass dependency to 2.0.
2. Replace the three renamed imports and call sites.
3. Replace `replaceRootTo` with `replaceRoot` and `replaceStackTo` with `replaceStack`.
4. Update samples, tests, previews, and documentation.
5. Run:

   ```bash
   ./gradlew :kompass:jvmTest :kompass:koverVerify :samples:compileKotlinJvm
   ```

## Snapshot validation

Before publishing the stable `2.0.0`, publish `2.0.0-SNAPSHOT01` and validate it in a real consumer
application. Confirm dependency resolution, Android compilation, shared KMP compilation, and iOS
framework integration. The snapshot is for integration validation and is not the final stable release.
