# Kompass vs Navigation 3

An independent comparison of Kompass and Jetpack Navigation 3, based on the current Kompass
implementation and its tests rather than on marketing claims.

- Date: 2026-09-15
- Repository: `KompassKmp`
- Kompass scope: Compose Multiplatform navigation core, host/UI layouts, predictive Back, metadata,
  deep links, results, scopes, tabs, shared elements, and composite panes
- Coverage: Kover class coverage currently reported at 87.9%; total verification baseline is 60%

---

## 1. Verdict

**Kompass is the stronger foundation when navigation must be shared across platforms and treated as
application state. Navigation 3 is the stronger default when an Android team wants the official
AndroidX ecosystem and its ready-made integrations.**

The central difference is where the truth lives:

- Navigation 3 makes the developer-owned back stack of keys the primary model.
- Kompass makes an immutable `NavigationState`, transformed by a pure reducer, the primary model.

That distinction matters for testing, persistence, server-driven flows, modular routing, and
cross-platform behavior.

Kompass currently provides presentation metadata, shared-element transitions, deep-link helpers, typed
destination arguments, predictive Back, seekable transitions, tab models, and multi-pane/composite
layouts, with the core behavior covered by tests.

The remaining difference is not a missing navigation capability. Navigation 3 has a larger official
AndroidX ecosystem, while Kompass owns a more explicit and portable navigation model.

---

## 2. Footprint and ownership

The two libraries make different packaging choices.

| Concern | Navigation 3 | Kompass |
|---|---|---|
| Core model | Developer-owned list of navigation keys | Immutable state plus reducer and commands |
| Rendering | `NavDisplay` resolves keys into `NavEntry` content | `KompassNavigationHost` resolves entries through navigation graphs |
| Layout | `SceneStrategy` and `Scene` | Pluggable `SceneLayout` implementations |
| State persistence | Serializable keys through platform saved state | Serializable navigation document and composition-owned restoration |
| Lifecycle | AndroidX decorators and ViewModel integration | Built-in entry owners and navigation scopes |
| Adaptive UI | Official Material 3 Adaptive strategies | List-detail and composite multi-pane layouts |
| Platforms | Compose/AndroidX-oriented | Android, iOS, JVM/Desktop, and Wasm targets |

Kompass intentionally includes more of the lifecycle, persistence, routing, and multiplatform seam
inside its artifact. Navigation 3 delegates more responsibility to separate AndroidX libraries. A
smaller core artifact is not automatically a smaller application architecture.

Kompass's public model is built around a small set of concepts:

`Destination` · `BackStackEntry` · `NavigationState` · `NavigationCommand` · `NavigationHandler` ·
`NavController` · `NavigationGraph` · `SceneLayout` · `SceneTransition` · `NavigationScopeId`

The optional composite layout is isolated behind its opt-in package. Applications that only need a
single pane do not need to adopt the pane-tree API.

---

## 3. The two models

### Navigation 3

Navigation 3 exposes a list-backed model:

```kotlin
val backStack = rememberNavBackStack(Home)

NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Home> { HomeScreen() }
        entry<Profile> { key -> ProfileScreen(key.userId) }
    },
)
```

The application owns the back stack and adds or removes keys. `NavDisplay` renders it, while entry
decorators and scene strategies provide lifecycle and layout behavior.

### Kompass

Kompass exposes a controller over immutable navigation state:

```kotlin
val navController = rememberKompassNavController(Home)

KompassNavigationHost(
    navController = navController,
    graphs = persistentListOf(MainGraph),
)

navController.navigate(Profile.toKompassBackStackEntry(args = profileArgs))
```

Navigation commands are reduced into new states:

```text
NavigationState + NavigationCommand -> NavigationState
```

The reducer is pure and can be tested without Compose, platform lifecycle objects, or mocks. The host
is responsible for rendering the resulting state through the graph and layout abstractions.

---

## 4. Navigation as data

Kompass's most important advantage is that navigation state is a portable value.

This enables:

1. **Session handoff** — serialize a stack and restore it on another process or device.
2. **Bug replay** — keep the navigation document from a report and reproduce the same stack in a test.
3. **Pure flow tests** — exercise multi-step navigation through the reducer without composing UI.
4. **Server-driven routing** — accept destination IDs, arguments, and presentation metadata from a
   server or configuration source.
5. **Analytics** — observe navigation commands as an explicit transition stream.
6. **Schema migration** — migrate serialized destination IDs and arguments when the application evolves.
7. **Atomic stack replacement** — apply a complete restored or deep-linked stack as one state change.
8. **Portable shell configuration** — persist a composite pane arrangement as serializable data.

Navigation 3 can model many of these behaviors, but applications must build the surrounding state,
serialization, and migration conventions themselves. Its official model is optimized for owning a
back stack in the application UI.

### Typing is not the real dividing line

Navigation 3 keys can be strongly typed, but the application still owns the key model and its
serialization. Kompass supports the same typed arguments through `TypedDestination<T>` while keeping
the transport identity opaque and serializable. This allows a feature to expose typed arguments at
its boundary without making the entire host depend on every feature key type.

---

## 5. Comparison

Rating: **K** Kompass advantage, **N3** Navigation 3 advantage, **=** practical parity.

### 5.1 Architecture

| Dimension | Navigation 3 | Kompass | Better |
|---|---|---|---|
| Back stack | Mutable developer-owned key list | Immutable state and pure reducer | **K** |
| Portable navigation snapshot | Application-defined | Built-in serialized navigation state | **K** |
| Dynamic or server-driven destinations | Possible with custom keys | Native destination ID and JSON argument model | **K** |
| Typed arguments | Native key model | `TypedDestination<T>` projection | **=** |
| Exhaustive destination content | Strong with sealed/typed keys | Strong with typed destination enums | **=** |
| Pure navigation testing | Usually requires state/UI setup | Reducer tests need no Compose runtime | **K** |
| Official ecosystem and governance | AndroidX, Google-maintained | Project-maintained | **N3** |

### 5.2 Everyday features

| Dimension | Navigation 3 | Kompass | Better |
|---|---|---|---|
| Results | Application pattern/recipe | First-class pending keys and typed consumption | **K** |
| Flow-wide shared state | Decorator or custom owner design | `NavigationScopeId` shared across entries | **K** |
| Entry lifecycle and saved state | AndroidX decorators | Built into the navigation host | **=** |
| Deep links | Official recipes and matchers | Template helper plus open handler API | **=** |
| Metadata | `NavEntry`/scene metadata | Serializable `BackStackEntry.metadata` | **=** |
| Predictive Back | Official AndroidX integration | Android, iOS, Desktop, and Wasm support | **K** |
| Shared elements | Compose/Navigation integration | `KompassSharedTransitionHost` | **=** |

### 5.3 Tabs and multiple back stacks

Both libraries can support the two common tab models:

- one controller per tab, preserving each tab's own stack;
- one shared stack where selecting a tab reuses or moves its root entry.

Kompass makes the second model explicit through occurrence identity and `reuseIfExists`, while the
first model uses multiple controllers. Navigation 3 provides the basic list primitives, but the app
owns the tab history and state-retention policy.

### 5.4 Layout and motion

| Dimension | Navigation 3 | Kompass | Better |
|---|---|---|---|
| Layout abstraction | `SceneStrategy` returns a scene | `SceneLayout` renders the stack through a resolver | **=** |
| Multiple entries at once | Scenes and Material Adaptive strategies | List-detail and composite layouts | **=** |
| Resizable panes | Material Adaptive expansion APIs with a drag handle | Composite layout resize handles and persisted split ratios | **=** |
| Rearrangeable panes | Application-defined | Composite pane tree with drag-and-drop docking | **K** |
| Presentation metadata | Official metadata conventions | Serializable metadata carried with the entry | **=** |
| Per-destination transitions | Entry/scene metadata and transition APIs | `SceneTransitionContext` with source and target entries | **=** |
| Predictive/seekable motion | Official scene animation APIs | Predictive Back, seekable scenes, edge-swipe fallback | **=** |

There is no longer a meaningful claim that Kompass lacks adaptive or multi-scene layouts. The two
libraries provide different abstractions and different ready-made implementations. Navigation 3
supports resizing an existing pane split through `PaneExpansionState` and a drag handle, but it does
not provide Kompass's pane rearrangement workflow: dragging a pane, previewing dock targets, and
moving it into a different position in a serializable pane tree.

### 5.5 Ecosystem

| Dimension | Navigation 3 | Kompass | Better |
|---|---|---|---|
| AndroidX integration | Official | Compatible, but independently maintained | **N3** |
| Documentation and recipes | Large official ecosystem | Focused repository documentation and samples | **N3** |
| Cross-platform ownership | Less KMP-first | Designed around shared KMP code | **K** |
| Fix turnaround | External release process | Maintainer controls the implementation | **K** |
| Long-term support confidence | Google governance | Depends on project maintenance | **N3** |

---

## 6. Where Kompass wins

1. **Navigation is a value.** State can be serialized, compared, replayed, migrated, and tested.
2. **The reducer is pure.** Core navigation rules are deterministic and fast to test.
3. **Results are first-class.** Result delivery and one-time consumption are part of the state model.
4. **Scopes model flows.** A scope can span several destinations and is cleared after its final user.
5. **The render loop is open.** A custom layout receives the complete stack and can render any scene.
6. **Multiplatform behavior is intentional.** The same model serves Android, iOS, Desktop, and Wasm.
7. **Advanced motion is already included.** Predictive Back, seekable transitions, and shared elements
   are not future roadmap items.
8. **Composite layouts are serializable.** Pane placement and split ratios can be restored or shared.

---

## 7. Where Navigation 3 wins

Navigation 3's strongest advantages are institutional rather than architectural:

- official AndroidX ownership and release process;
- broader Android documentation, recipes, samples, and community knowledge;
- direct integration with the Android lifecycle and Material 3 Adaptive ecosystem;
- lower adoption risk for teams targeting Android only;
- a larger pool of developers already familiar with its APIs.

These are legitimate reasons to choose Navigation 3. They should not be described as missing Kompass
features when Kompass already provides the equivalent behavior through its own APIs.

---

## 8. Roadmap status

The comparison roadmap is complete for the capabilities currently desired by the project.

| Capability | Status |
|---|---|
| Atomic stack replacement | ✅ Implemented |
| Typed destination arguments | ✅ Implemented and tested |
| Presentation metadata | ✅ Implemented and serialized |
| Predictive Back and seekable transitions | ✅ Implemented and tested |
| Resizable and adaptive layouts | ✅ Implemented |
| Composite multi-pane layout | ✅ Implemented with resize and docking |
| Multiple tab models | ✅ Implemented and tested on JVM |
| Per-destination transitions | ✅ Implemented |
| Deep-link templates and safe argument encoding | ✅ Implemented and tested |
| Shared-element transitions | ✅ Implemented and tested |
| Read-only direction and immutable back stack | ✅ Implemented |
| Core coverage measurement | ✅ Kover configured with a 60% verification baseline |
| Core/host test separation | ✅ Documented and maintained |

No additional Navigation 3-derived capability is currently required for the intended Kompass scope.
Future work should be driven by a concrete product need, not by matching names in the AndroidX API.

---

## 9. Choosing

**Choose Kompass when:**

- the app is Kotlin Multiplatform;
- navigation state must be stored, restored, sent, diffed, or replayed;
- core navigation rules should be tested without Compose;
- feature modules should route without exposing every destination type to the host;
- flow-wide lifecycle scopes, first-class results, or custom multi-pane shells matter;
- the team values control over the navigation implementation.

**Choose Navigation 3 when:**

- the product is Android-first or Android-only;
- official AndroidX governance is the primary requirement;
- the team wants the broadest existing Android recipes and ecosystem integrations;
- the application benefits directly from the official Material 3 Adaptive strategy artifacts.

The short version: **Navigation 3 is the safer Android default. Kompass is the more capable state and
multiplatform foundation when the application needs navigation to be a durable, portable document.**
