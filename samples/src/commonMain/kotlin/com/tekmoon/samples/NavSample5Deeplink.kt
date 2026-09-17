package com.tekmoon.samples

import com.tekmoon.kompass.KompassEntry
import com.tekmoon.kompass.kompassEntry
import com.tekmoon.kompass.NavigationCommand
import com.tekmoon.kompass.newScope
import com.tekmoon.kompass.PathTemplateDeepLinkHandler
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.tekmoon.kompass.DeepLinkChannel
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassNavController
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.TypedDestination
import com.tekmoon.kompass.navigateTo
import com.tekmoon.kompass.requireArgs
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

/**
 * Deep linking, end to end.
 *
 * Kompass resolves a URI into navigation commands. Handing the URI to Kompass is the application's
 * job, and it looks different on each platform. The `composeApp` module in this repository does all
 * of it, so the commands below work as written.
 *
 * ## Try it
 *
 * The sample app opens on the sample list. A URI sent from outside opens this sample directly, with
 * the profile already on the stack.
 *
 * Android:
 * ```
 * adb shell am start \
 *   -a android.intent.action.VIEW \
 *   -d "myapp://profile/42" \
 *   com.tekmoon.kompasskmp
 * ```
 *
 * Desktop:
 * ```
 * ./gradlew :composeApp:run --args="myapp://profile/42"
 * ```
 *
 * iOS simulator:
 * ```
 * xcrun simctl boot "iPhone 16 Pro"
 * xcrun simctl openurl booted "myapp://profile/42"
 * ```
 *
 * iOS device: `simctl` reaches simulators only, and it answers "No devices are booted" when the app
 * is running on a phone. `devicectl` has no command for a URL either. Open Safari on the phone and
 * type `myapp://profile/42` in the address bar, not in a search field, or the browser searches for
 * the text instead of opening it.
 *
 * ## What an application has to provide
 *
 * A cold start hands the URI over once, through `deepLinkUri`. A URI that arrives while the app runs
 * has no such entry point, so it travels through a [DeepLinkChannel] instead. Kompass reads both.
 *
 * Android needs an intent filter in the manifest, and an Activity that reads the intent:
 * ```
 * <intent-filter>
 *     <action android:name="android.intent.action.VIEW" />
 *     <category android:name="android.intent.category.DEFAULT" />
 *     <category android:name="android.intent.category.BROWSABLE" />
 *     <data android:scheme="myapp" />
 * </intent-filter>
 *
 * // android:launchMode="singleTop", so a warm link reaches onNewIntent instead of a second Activity
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContent { App(deepLinkUri = intent?.dataString, deepLinkChannel = deepLinkChannel) }
 * }
 *
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     setIntent(intent)
 *     intent.dataString?.let(deepLinkChannel::send)
 * }
 * ```
 *
 * Desktop reads its first argument. It registers no scheme with the operating system, so a desktop
 * deep link is a cold start only:
 * ```
 * fun main(args: Array<String>) = application {
 *     Window(onCloseRequest = ::exitApplication) { App(deepLinkUri = args.firstOrNull()) }
 * }
 * ```
 *
 * iOS declares `CFBundleURLSchemes` in `Info.plist` and forwards `onOpenURL` into the channel. That
 * one callback covers the cold start and the warm one, because the channel buffers.
 * ```
 * ComposeView().onOpenURL { url in IosDeepLinks.shared.channel.send(uri: url.absoluteString) }
 * ```
 *
 * To fire it on a phone, open Safari and type `myapp://profile/42`. See "Try it" above: that is the
 * only way on a physical device, because `simctl` talks to simulators.
 */

/* -------------------------------------------
 * Destinations
 *
 * Sample5Dest is a sealed interface — Home is parameterless (plain Destination),
 * Profile carries typed args (TypedDestination<ProfileArgs>) so we can use
 * navController.navigateTo(...) and navController.requireArgs(...) at call sites
 * without manual Json.encodeToString / decodeFromString.
 * ------------------------------------------- */

private sealed interface Sample5Dest : Destination {

    data object Home : Sample5Dest {
        override val id: String = "kompass/sample5/main/home"
    }

    data object Profile : Sample5Dest, TypedDestination<ProfileArgs> {
        override val id: String = "kompass/sample5/main/profile"
        override val argsSerializer = ProfileArgs.serializer()
    }
}

/* -------------------------------------------
 * Args
 * ------------------------------------------- */

@Serializable
private data class ProfileArgs(
    val userId: String
)

/* -------------------------------------------
 * Deep link
 *
 * PathTemplateDeepLinkHandler parses the URI and gives us the path values. A
 * deep-link handler runs without a KompassNavController, so it can't call
 * navController.navigateTo. Instead we build entries with the destination's
 * own toKompassEntry helper, passing a Json instance ourselves.
 * Json.Default works fine for plain @Serializable args; if you need polymorphic
 * args, configure a SerializersModule and pass it here.
 * ------------------------------------------- */

private val profileDeepLinkHandler = PathTemplateDeepLinkHandler("myapp://profile/{userId}") { match ->
    val userId = match["userId"] ?: error("Missing userId in deep link")

    // One command applies the whole stack, so the deep link does not flash through Home first.
    listOf(
        NavigationCommand.ReplaceStack(
            listOf(
                kompassEntry(
                    destinationId = Sample5Dest.Home.id,
                    scopeId = newScope()
                ),
                Sample5Dest.Profile.toKompassEntry(
                    args = ProfileArgs(userId),
                    json = Json,
                    scopeId = newScope()
                )
            )
        )
    )
}

/* -------------------------------------------
 * Graph
 * ------------------------------------------- */

private object Sample5Graph : KompassNavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId == Sample5Dest.Home.id ||
                destinationId == Sample5Dest.Profile.id

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            Sample5Dest.Home.id -> Sample5Dest.Home
            Sample5Dest.Profile.id -> Sample5Dest.Profile
            else -> error("Unknown Sample5 destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController
    ) {
        when (destination) {
            Sample5Dest.Home ->
                HomeScreen(navController)

            Sample5Dest.Profile ->
                ProfileScreen(entry, navController)
        }
    }
}

/* -------------------------------------------
 * Root
 * ------------------------------------------- */

@Composable
fun Sample5_DeepLink(
    deepLinkUri: String? = null,
    deepLinkChannel: DeepLinkChannel? = null,
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navController =
        rememberKompassNavController(
            startDestination = Sample5Dest.Home,
            deepLinkUri = deepLinkUri,
            deepLinkHandlers = persistentListOf(profileDeepLinkHandler)
        )

    // A subscription needs an owner. One channel carries each URI to exactly one collector, so an
    // observer left behind by a previous navController would steal links from this one.
    DisposableEffect(navController, deepLinkChannel) {
        val subscription = deepLinkChannel?.observe { uri ->
            navController.applyDeepLink(uri = uri)
        }
        onDispose { subscription?.cancel() }
    }

    KompassBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan{
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample5Graph)
    )
}


/* -------------------------------------------
 * Screens
 * ------------------------------------------- */

@Composable
private fun HomeScreen(
    navController: KompassNavController
) {
    Column {
        Text("🏠 Home")

        Button(onClick = {
            navController.navigateTo(
                destination = Sample5Dest.Profile,
                args = ProfileArgs("manual"),
                scopeId = newScope()
            )
        }) {
            Text("Go to Profile (manual)")
        }
    }
}

@Composable
private fun ProfileScreen(
    entry: KompassEntry,
    navController: KompassNavController
) {
    val args = navController.requireArgs(Sample5Dest.Profile, entry)

    Column {
        Text("👤 Profile")
        Text("UserId = ${args.userId}")

        Button(onClick = {
            navController.pop()
        }) {
            Text("Back")
        }
    }
}
