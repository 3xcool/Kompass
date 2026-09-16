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
 * TO TEST run
 *
 * Android:
 * adb shell am start \
 *   -a android.intent.action.VIEW \
 *   -d "myapp://profile/42" \
 *   com.tekmoon.soccos
 *
 * Desktop:
 * For Desktop:
 * ./gradlew :composeApp:run --args="myapp://profile/42"
 *
 * On iOS
 * open safari and go to  myapp://profile/42
 * or via terminal
 * xcrun simctl openurl booted "myapp://profile/42"
 */


/**
 * Mimic deeplink
 *
 * URI
 * myapp://profile/42
 *
 * adb shell am start \
 *   -a android.intent.action.VIEW \
 *   -d "myapp://profile/42" \
 *   com.tekmoon.soccos
 *
 * What this does:
 *
 * Launches your app (cold start if needed)
 *
 * Passes the URI to the Activity
 *
 * Your app extracts the URI and feeds it into the Kompass deep-link handler
 *
 * For Android:
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *
 *     val uri = intent?.dataString
 *     setContent {
 *         AppRoot(deepLinkUri = uri)
 *     }
 * }
 * or
 * override fun onNewIntent(intent: Intent?) {
 *     super.onNewIntent(intent)
 *     setIntent(intent)
 * }
 *
 * For Desktop:
 * ./gradlew :composeApp:run --args="myapp://profile/42"
 * fun main(args: Array<String>) {
 *     val deepLinkUri = args.firstOrNull()
 *     launchApp(deepLinkUri)
 * }
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

    LaunchedEffect(navController) {
        deepLinkChannel?.observe { uri ->
            navController.applyDeepLink(uri = uri)
        }
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
