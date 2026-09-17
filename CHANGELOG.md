# Changelog

## Unreleased

Fixes found by a review of the library. Each one has a test that fails without it.

### Fixed

- A controller no longer clears a scope another controller still uses. Scopes live in a process-wide
  store, so two controllers that start at the same destination share `entry:<id>`. Closing one used
  to destroy the ViewModels of the other. Each controller now holds the scopes its back stack
  carries, and only the last holder clears them. A controller built with
  `rememberKompassNavController` also lets go of its scopes when its host leaves composition, which
  it never did before. Android activity recreation is excluded, so a rotation keeps its ViewModels.
- `reuseIfExists` keeps the result state of the occurrence it moves. It used to take the result
  fields from the new entry, which are empty, so an answer the screen had not read yet was dropped
  with no report.
- A result request is opened only when an entry stays directly below the new top. `reuseIfExists` on
  the caller's own destination used to record a request that nothing could ever answer, and reported
  nothing. It is now reported through `onNavigationError` and no entry is left waiting.
- `pop(count = 0)` and any negative count pop nothing. They used to pop one entry.
- `NavigationCommand.Pop` rejects a `count` above 1 together with a `popUntil`. The two name
  different stops, and `popUntil` used to be ignored in silence.
- A percent escape in a deep link decodes two hex digits and nothing else. `toIntOrNull(16)` accepts
  a sign, so `%-1` decoded to byte `0xFF` and `%+1` to a control character.
- The report for a lost result request names the parameter the caller actually used. An inclusive
  `popUpTo` used to be reported as `clearBackStack`.
- `KompassOwnerStore.release` and `NavigationScopes.release` ignore an unbalanced call instead of
  throwing. A disposal that runs twice no longer takes the application down.
- `:samples` tests compile again. `NavigationHandlerTestRobot` used the internal `KompassEntry`
  constructor from another module, so the whole samples test suite failed to build.

### Changed

- `DeepLinkChannel.observe` returns a `DeepLinkSubscription`, and `DeepLinkChannel` gains `close()`.
  The scope behind the channel was never cancelled and a registration could never be undone. The
  constructor also takes a `CoroutineContext`, which defaults to `Dispatchers.Main`.
- `KompassOwnerStore.owner` takes the platform `CreationExtras` as a parameter. The mutable
  `platformExtras` property is gone; two composables used to assign it during the composition phase.

### Performance

- `PredictiveBackState.progress` is backed by a `mutableFloatStateOf`, so a gesture no longer boxes a
  `Float` on every frame.
- `SceneLayoutDefaultAnimatedSinglePane` resolves its transition once per entry instead of resolving
  the graph and allocating a `SceneTransitionDefault` on every recomposition.
- `PathTemplateDeepLinkHandler` parses a URI once for the `matches` and `resolve` pair.
- `KompassOwnerStore.save` builds its scope keys once instead of once per entry.

## 2.1.0

Navigation results become an explicit request with three outcomes. This release contains breaking
API changes. It is published as a minor version because 2.0.0 has no consumers yet.

### Highlights

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
- The `KompassEntry` constructor is now internal, because an entry carries result state that only
  the reducer may set. Use `Destination.toKompassEntry(...)`, or the new `kompassEntry(...)` when
  only the destination ID is known, which is the usual case inside a `DeepLinkHandler`.
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
- Saved state written by 2.0.0 restores only when it carries no result. A result is now stored in an
  internal envelope instead of a bare `NavigationResult`, so an older entry that holds one fails to
  decode. The controller reports the failure and falls back to its initial state, as it does for any
  unreadable saved state. It never restores half a stack.
- A restored 2.0.0 entry that carries `pendingResultKey` arrives with a marker for a request that no
  longer exists, because the field kept its name and changed owner. The marker is harmless: it sits
  on a producer, whose screen never reads it, and it leaves the stack with that entry.

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
