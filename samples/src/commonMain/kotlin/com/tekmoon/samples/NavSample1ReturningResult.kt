package com.tekmoon.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import com.tekmoon.kompass.PlatformBackHandler
import com.tekmoon.kompass.BackStackEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.NavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.NavController
import com.tekmoon.kompass.NavigationResult
import com.tekmoon.kompass.rememberNavController
import com.tekmoon.kompass.toBackStackEntry
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal sealed interface Sample1Destination : Destination {

    data object First : Sample1Destination {
        override val id = "kompass/sample1/first"
    }

    data object Second : Sample1Destination {
        override val id = "kompass/sample1/second"
    }
}

@Serializable
internal data class NameResult(val name: String) : NavigationResult

private object Sample1Graph : NavigationGraph {

    override fun canResolveDestination(destinationId: String): Boolean =
        destinationId.startsWith("kompass/sample1/")

    override fun resolveDestination(
        destinationId: String,
        args: String?
    ): Destination =
        when (destinationId) {
            Sample1Destination.First.id -> Sample1Destination.First
            Sample1Destination.Second.id -> Sample1Destination.Second
            else -> error("Unknown Sample1 destination: $destinationId")
        }

    @Composable
    override fun Content(
        entry: BackStackEntry,
        destination: Destination,
        navController: NavController
    ) {
        when (destination) {
            Sample1Destination.First -> Sample1First(entry, navController)
            Sample1Destination.Second -> Sample1Second(navController)
        }
    }
}

@Composable
fun Sample1_ResultNavigation(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    val navigationSerializersModule = SerializersModule {
        polymorphic(NavigationResult::class) {
            subclass(NameResult::class, NameResult.serializer())
        }
    }

    val navController = rememberNavController(
        startDestination = Sample1Destination.First,
        serializersModule = navigationSerializersModule
    )

    PlatformBackHandler(
        backPressedChannel = backPressedChannel,
    ) {
        navController.popIfCan{
            onDismiss()
        }
    }

    KompassNavigationHost(
        navController = navController,
        graphs = persistentListOf(Sample1Graph),
    )
}


@Composable
private fun Sample1First(
    entry: BackStackEntry,
    navController: NavController
) {
    val navResultKey = "name"
    val name = (entry.results[navResultKey] as? NameResult)?.name

    Column {
        BasicText("Result: ${name ?: "-"}")

        Button(onClick = {
//            navController.navigate(
//                entry = BackStackEntry(
//                    destinationId = Sample1Destination.Second.id,
//                    scopeId = Sample1Destination.Second.defaultScope(),
//                    pendingResultKey = navResultKey
//                )
//            )
            // or
            navController.navigate(
                entry = Sample1Destination.Second.toBackStackEntry(
                    pendingResultKey = navResultKey
                )
            )
        }) {
            BasicText("Open Second")
        }
    }
}

@Composable
private fun Sample1Second(
    navController: NavController
) {
    Button(onClick = {
        navController.pop(
            result = NameResult("Luke Skywalker")
        )
    }) {
        BasicText("Return Result")
    }
}