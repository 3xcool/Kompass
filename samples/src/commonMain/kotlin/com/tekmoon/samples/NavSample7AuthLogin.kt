package com.tekmoon.samples

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tekmoon.kompass.KompassEntry
import com.tekmoon.kompass.kompassEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassNavController
import com.tekmoon.kompass.KompassNavigationGraph
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.defaultScope
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassEntry
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
) : KompassNavigationGraph {

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
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController
    ) {
        when (destination) {
            LoginDestination.Email -> LoginEmailScreen(navController)
            LoginDestination.Password -> LoginPasswordScreen(navController, onLoginSuccess)
        }
    }
}

private object AppGraph : KompassNavigationGraph {

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
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController
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
    val navController = rememberKompassNavController(
        startDestination = LoginDestination.Email
    )

    KompassBackHandler(
        backPressedChannel = backPressedChannel
    ) {
        navController.popIfCan {
            onDismiss()
        }
    }

    // A graph that takes a parameter is a new instance on every recomposition unless it is
    // remembered. The host does `remember(graphs) { NavigationGraphRouter(graphs) }`, and LoginGraph
    // is a plain class, so a fresh instance would rebuild the router on every frame. A graph
    // declared as an `object`, or as a `data class` that compares equal, needs none of this.
    val graphs = remember(navController) {
        persistentListOf(
            LoginGraph(onLoginSuccess = {
                navController.replaceStack(
                    entry = kompassEntry(
                        destinationId = AppDestination.Home.id,
                        scopeId = AppDestination.Home.defaultScope()
                    )
                )
            }),
            AppGraph
        )
    }

    KompassNavigationHost(
        navController = navController,
        graphs = graphs
    )
}




// ======= Screens =======
@Composable
private fun LoginEmailScreen(
    navController: KompassNavController
) {
    Button(
        onClick = {
            navController.navigate(
                entry = LoginDestination.Password.toKompassEntry()
            )
        }
    ) {
        Text("Next (Email)")
    }
}

@Composable
private fun LoginPasswordScreen(
    navController: KompassNavController,
    onLoginSuccess: () -> Unit
) {
    Button(
        onClick = {
//            onLoginSuccess() // this way we hoist the logic to Main Nav Host
            // or we can call it directly from this screen like this:
            navController.replaceStack(
                entry = kompassEntry(
                    destinationId = AppDestination.Home.id,
                    scopeId = AppDestination.Home.defaultScope()
                )
            )
        }
    ) {
        Text("Login")
    }
}


@Composable
private fun MockedAppScreen() {
    Text("🏠 App Home")
}
