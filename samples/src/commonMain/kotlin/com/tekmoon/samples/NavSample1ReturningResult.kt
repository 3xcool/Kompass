package com.tekmoon.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.KompassEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassNavController
import com.tekmoon.kompass.NavigationResult
import com.tekmoon.kompass.rememberKompassNavController
import com.tekmoon.kompass.toKompassEntry
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

private object Sample1Graph : KompassNavigationGraph {

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
        entry: KompassEntry,
        destination: Destination,
        navController: KompassNavController
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

    val navController = rememberKompassNavController(
        startDestination = Sample1Destination.First,
        serializersModule = navigationSerializersModule
    )

    KompassBackHandler(
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
    entry: KompassEntry,
    navController: KompassNavController
) {
    val navResultKey = "name"
    val name = (entry.results[navResultKey] as? NameResult)?.name

    Column {
        Text("Result: ${name ?: "-"}")

        Button(onClick = {
//            navController.navigate(
//                entry = KompassEntry(
//                    destinationId = Sample1Destination.Second.id,
//                    scopeId = Sample1Destination.Second.defaultScope(),
//                    pendingResultKey = navResultKey
//                )
//            )
            // or
            navController.navigate(
                entry = Sample1Destination.Second.toKompassEntry(
                    pendingResultKey = navResultKey
                )
            )
        }) {
            Text("Open Second")
        }
    }
}

@Composable
private fun Sample1Second(
    navController: KompassNavController
) {
    Button(onClick = {
        navController.pop(
            result = NameResult("Luke Skywalker")
        )
    }) {
        Text("Return Result")
    }
}
