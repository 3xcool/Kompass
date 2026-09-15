# Kompass 2.0 API Migration

Kompass 2.0 makes the ownership of its most commonly used public helpers explicit. This is a
breaking API change: update imports and call sites when moving from 1.x to 2.0.

## Renamed APIs

| Kompass 1.x | Kompass 2.0 |
|---|---|
| `rememberNavController` | `rememberKompassNavController` |
| `createNavController` | `createKompassNavController` |
| `toBackStackEntry` | `toKompassBackStackEntry` |
| `newScope` | `newScope` |
| `defaultScope` | `defaultScope` |
| `buildArgs` | `buildArgs` |
| `applyDeepLink` | `applyDeepLink` |
| `encodeArgs` | `encodeArgs` |
| `argsFrom` | `argsFrom` |
| `argsOrNull` | `argsOrNull` |
| `requireArgs` | `requireArgs` |
| `navigateTo` | `navigateTo` |
| `replaceRootTo` | `replaceRoot` |
| `replaceStackTo` | `replaceStack` |

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
import com.tekmoon.kompass.toKompassBackStackEntry

val navController = rememberKompassNavController(Home)
navController.navigate(Profile.toKompassBackStackEntry())
```

The core `NavController` methods remain unchanged: `navigate`, `pop`, `replaceRoot`, `replaceStack`,
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
