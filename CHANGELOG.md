# Changelog

## 2.1.0

Navigation results become an explicit request with three outcomes. This release contains breaking
API changes.

See [docs/api-v2-migration.md](docs/api-v2-migration.md) for the call-site changes and a checklist.

### Highlights

- Added `navigate(destination, ...)` and `replaceStack(destination, ...)`, which build the entry for
  you. `navigate(Profile)` replaces `navigate(Profile.toKompassEntry())`, and both take `args`,
  `scopeId` and `metadata` when the defaults do not fit. Build the entry yourself when you need to
  hold it: a whole stack, the command list of a `DeepLinkHandler`, or an initial state.
- Added a `resultKey` parameter to `navigate` and `navigateTo`. It opens a result request and records
  it on the entry that starts it. Do not combine it with `clearBackStack`, which removes the entry
  that would receive the answer; the request is dropped and reported.
- Added `ResultState`: `Pending`, `Delivered(value)` and `Cancelled`. Back, predictive Back and a
  plain `pop` now end an open request as `Cancelled`, which an application could not detect before.
- Added `ResultKey<T>`, a value class that carries the expected result type to the call site. Only
  its name enters the navigation state.
- Added `peekResult(key)`, which reads the request state while rendering without closing it.
- Added `onNavigationError` on the controller factories. Kompass never throws for either case below,
  because a repeated tap produces both.
  - A delivery that nobody waits for is refused whole: nothing pops, nothing is stored, and the
    reason is reported. Refusing the pop is what stops a repeated tap from removing an extra screen.
  - A new request that replaces an unconsumed answer or cancellation still navigates, and the drop
    is reported. Refusing it would leave a button that does nothing.

### Compose stability

- `KompassEntry` is `@Immutable`, and the promise now holds: `metadata` is an `ImmutableMap`, the
  stored results are a `PersistentMap`, and the occurrence `id` is a constructor `val` instead of a
  property assigned after construction. A composable that takes an entry can skip recomposition.
- The builders still take a plain `Map` and convert at the boundary, so `mapOf(...)` keeps working
  and an entry never holds a map the caller can still change.
- The serialized form is unchanged. `metadata` is still a plain JSON object.
- `NavigationCommand.ReplaceStack.entries` is an `ImmutableList`. A second constructor takes a plain
  `List`, so `ReplaceStack(listOf(...))` keeps working and the command holds no caller list.
- `DeepLinkMatch` is `@Immutable`, and its `path` and `query` are `ImmutableMap`.

### Breaking changes

- `KompassEntry.pendingResultKey` keeps its name and changes both its owner and its meaning. It used
  to sit on the destination that would return a result, and it routed the answer. It now sits on the
  entry that is waiting, and it records only that. The key that routes an answer travels with `pop`.
- `KompassEntry.results` is now internal. Use `peekResult` and `consumeResult`.
- The `KompassEntry` constructor drops its `pendingResultKey` and `results` parameters. It keeps
  `destinationId`, `args`, `scopeId` and `metadata`, and the reducer sets the rest.
- `KompassEntry` drops `component4()` and `component5()`. Destructuring covers the first three.
- `KompassEntry.metadata` is an `ImmutableMap<String, String>`. Reading it is unchanged; code that
  assigned it to a `MutableMap` variable no longer compiles.
- `pop(result, resultKey)` no longer takes `count` or `popUntil`. A result only reaches the entry
  one step below.
- `consumeResult(key, entryId)` returns `ResultState<T>?` instead of `T?`.
- `toKompassEntry` and `navigateTo` drop their `pendingResultKey` and `results` parameters.
- A result delivered to an entry that did not open a request is rejected and reported. A `navigate`
  without `resultKey`, followed by `pop(result, key)`, no longer delivers anything.
- `replaceRoot` is removed, on the controller and on the typed helper, together with
  `NavigationCommand.ReplaceRoot`. Use `replaceStack`, which applies one entry or a whole stack.
- A restored 2.0.0 entry that carries `pendingResultKey` arrives with a marker for a request that no
  longer exists, because the field kept its name and changed owner. The marker is harmless: it sits
  on a producer, whose screen never reads it, and it leaves the stack with that entry.

### Saved state

State written by 2.1.0 restores in full, results included.

2.1.0 stores a closed request in an internal envelope instead of a bare `NavigationResult`. State
written by 2.0.0 that holds a result fails to decode, and the controller falls back to its initial
state. Kompass carries no reader for the old shape.

## 2.0.0

### Highlights

- Introduced the explicit Kompass 2.0 navigation API: `KompassNavController`, `KompassEntry` and `KompassNavigationGraph`.
- Added entry metadata, typed destinations and arguments, atomic `replaceStack`, and immutable public back-stack access.
- Added predictive Back, seekable and source/target-aware transitions, and shared element transitions.
- Added multi-scene and adaptive layouts, including resizable and rearrangeable panes through `SceneLayoutComposite`.
- Added deep-link templates, typed result consumption, expanded restoration, nested-host ownership and tab navigation support.
- Added Kover coverage reporting and separate core and UI smoke-test workflows.

### Migration

Kompass 2.0 contains breaking API renames and signature changes. See the [API v2 migration guide](docs/api-v2-migration.md) before upgrading from 1.x.

## 1.2.0

### Highlights

- Automatic AndroidX ownership in KompassNavigationHost, with lifecycle and saveable UI state per navigation occurrence.
- ViewModels and SavedStateHandles shared by scopeId within each controller, including safe cleanup after outgoing animations and retention for nested hosts.
- Typed one-time result consumption, explicit restoration recovery, command-based transition direction, and externally owned controllers with StateFlow observation.
- Destination-aware transition context and optional visual seeking through SceneLayoutSeekable.
- Wasm browser target, alongside Android, JVM/Desktop and iOS.

### Migration

Recompile consumers for this release. The new helper signatures and transition interface methods change binary compatibility.

BackStackEntry is now a regular class with the original five constructor/copy parameters and destructuring fields. Its occurrence ID is generated by Kompass and read-only. Equality includes this identity. Code depending on data-class reflection must adapt.

AndroidX ViewModels now follow scopeId within a controller. Use newScope() when repeated visits require independent ViewModels; share an explicit scopeId across routes for a shared ViewModel. Lifecycle and rememberSaveable UI state remain entry-specific.

NavigationCommand adds ConsumeResult. Update exhaustive when expressions over commands. Use consumeResult<T>(key, entryId) for one-time handling; direct entry.results access remains a read-only lookup. Result implementations still require serializer registration.

Controller restoration falls back to a valid initial state and reports the cause through onRestoreFailure. Select NavigationRestorePolicy.Throw for strict failure. The navigation serializer no longer converts invalid saved state into an empty stack.

Externally created controllers require explicit close(). saveNavigationState() stores navigation payloads only. rememberNavController remains the automatic Compose ownership/restoration path.

SceneLayoutSeekable controls an already committed visual transition. Seeking backward does not undo navigation; platform gestures and predictive Back are not included.

### Validation

- 40 library JVM tests passed.
- 23 Android/Robolectric tests passed.
- 21 common tests passed in the Wasm browser.
- 19 sample JVM tests passed.
- iosArm64, iosSimulatorArm64 and iosX64 compilation passed.

iOS validation was compilation-only. Restoration tests exercise registries and Android Parcel round trips rather than a device process kill.
