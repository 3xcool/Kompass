package com.tekmoon.samples

import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.DeepLinkHandler
import com.tekmoon.kompass.NavigationCommand
import com.tekmoon.kompass.newScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import com.tekmoon.kompass.DeepLinkChannel
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

/**
 * TO TEST run
 *
 * Android:
 * adb shell am start \
 *   -a android.intent.action.VIEW \
 *   -d "myapp://profile?userId=42" \
 *   com.tekmoon.soccos
 *
 * Desktop:
 * For Desktop:
 * ./gradlew :composeApp:run --args="myapp://profile?userId=42"
 *
 * On iOS
 * open safari and go to  myapp://profile?userId=42
 * or via terminal
 * xcrun simctl openurl booted "myapp://profile?userId=42"
 */


/**
 * Mimic deeplink
 *
 * URI
 * myapp://profile?userId=42
 *
 * adb shell am start \
 *   -a android.intent.action.VIEW \
 *   -d "myapp://profile?userId=42" \
 *   com.tekmoon.soccos
 *
 * What this does:
 *
 * Launches your app (cold start if needed)
 *
 * Passes the URI to the Activity
 *
 * Your app extracts the URI and feeds it into DsDeepLinkRegistry
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
 * ./gradlew :composeApp:run --args="myapp://profile?userId=42"
 * fun main(args: Array<String>) {
 *     val deepLinkUri = args.firstOrNull()
 *     launchApp(deepLinkUri)
 * }
 */

/* -------------------------------------------
 * Destinations
 * ------------------------------------------- */

private enum class Sample5Dest : Destination {
    Home,
    Profile;

    override val id: String get() = "kompass/sample5/main/${name.lowercase()}"
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
 * ------------------------------------------- */

private object ProfileDeepLinkHandler : DeepLinkHandler {

    override fun matches(uri: String): Boolean = uri.startsWith("myapp://profile")

    override fun resolve(uri: String): List<NavigationCommand> {
        val userId = uri.substringAfter("userId=")

        val argsJson = Json.encodeToString(ProfileArgs(userId))

        // List all Routes, for the real app this can be tricky
        return listOf(
            NavigationCommand.ReplaceRoot(
                BackStackEntry(
                    destinationId = Sample5Dest.Home.id,
                    scopeId = newScope()
                )
            ),
            NavigationCommand.Navigate(
                BackStackEntry(
                    destinationId = Sample5Dest.Profile.id,
                    args = argsJson,
                    scopeId = newScope()
                )
            )
        )
    }
}

/* -------------------------------------------
 * Graph
 * ------------------------------------------- */

private object Sample5Graph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        Sample5Dest.entries.any { it.id == destinationId }

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        Sample5Dest.entries.first { it.id == destinationId }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
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
        rememberNavController(
            startDestination = Sample5Dest.Home,
            deepLinkUri = deepLinkUri,
            deepLinkHandlers = persistentListOf(ProfileDeepLinkHandler)
        )

    LaunchedEffect(navController) {
        deepLinkChannel?.observe { uri ->
            navController.applyDeepLink(uri = uri)
        }
    }

    PlatformBackHandler(
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
    navController: NavController
) {
    Column {
        BasicText("🏠 Home")

        Button(onClick = {
            navController.navigate(
                entry = BackStackEntry(
                    destinationId = Sample5Dest.Profile.id,
                    args = Json.encodeToString(
                        ProfileArgs("manual")
                    ),
                    scopeId = newScope()
                )
            )
        }) {
            BasicText("Go to Profile (manual)")
        }
    }
}

@Composable
private fun ProfileScreen(
    entry: BackStackEntry,
    navController: NavController
) {
    val args =
        entry.args?.let {
            Json.decodeFromString<ProfileArgs>(it)
        }

    Column {
        BasicText("👤 Profile")
        BasicText("UserId = ${args?.userId}")

        Button(onClick = {
            navController.pop()
        }) {
            BasicText("Back")
        }
    }
}

