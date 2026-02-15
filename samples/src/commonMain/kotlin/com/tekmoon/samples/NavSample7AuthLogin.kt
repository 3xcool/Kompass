package com.tekmoon.samples

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.defaultScope
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf

private sealed interface LoginDestination : Destination {
    data object Email : LoginDestination { override val id = "kompass/sample7/login/email" }
    data object Password : LoginDestination { override val id = "kompass/sample7/login/password" }
}

private sealed interface AppDestination : Destination {
    data object Home : AppDestination { override val id = "kompass/sample7/app/home" }
}

private class LoginGraph(
    private val onLoginSuccess: () -> Unit
) : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("kompass/sample7/login/")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            LoginDestination.Email.id -> LoginDestination.Email
            LoginDestination.Password.id -> LoginDestination.Password
            else -> error("Unknown login destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            LoginDestination.Email -> LoginEmailScreen(navController)
            LoginDestination.Password -> LoginPasswordScreen(navController, onLoginSuccess)
        }
    }
}

private object AppGraph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("kompass/sample7/app/")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            AppDestination.Home.id -> AppDestination.Home
            else -> error("Unknown app destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            AppDestination.Home -> MockedAppScreen()
        }
    }
}

@Composable
fun Sample7_AuthLogin(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navController = rememberNavController(
        startDestination = LoginDestination.Email
    )

    PlatformBackHandler(
        backPressedChannel = backPressedChannel
    ) {
        navController.popIfCan {
            onDismiss()
        }
    }

    val graphs = persistentListOf(
        LoginGraph( onLoginSuccess = {
            navController.replaceRoot(
                entry = BackStackEntry(
                    destinationId = AppDestination.Home.id,
                    scopeId = AppDestination.Home.defaultScope()
                )
            )
        }),
        AppGraph
    )

    KompassNavigationHost(
        navController = navController,
        graphs = graphs
    )
}




// ======= Screens =======
@Composable
private fun LoginEmailScreen(
    navController: NavController
) {
    Button(
        onClick = {
            navController.navigate(
                entry = LoginDestination.Password.toBackStackEntry()
            )
        }
    ) {
        BasicText("Next (Email)")
    }
}

@Composable
private fun LoginPasswordScreen(
    navController: NavController,
    onLoginSuccess: () -> Unit
) {
    Button(
        onClick = {
//            onLoginSuccess() // this way we hoist the logic to Main Nav Host
            // or we can call it directly from this screen like this:
            navController.replaceRoot(
                entry = BackStackEntry(
                    destinationId = AppDestination.Home.id,
                    scopeId = AppDestination.Home.defaultScope()
                )
            )
        }
    ) {
        BasicText("Login")
    }
}


@Composable
private fun MockedAppScreen() {
    BasicText("🏠 App Home")
}