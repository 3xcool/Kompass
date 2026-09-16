package com.tekmoon.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tekmoon.kompass.KompassBackHandler
import com.tekmoon.kompass.KompassEntry
import com.tekmoon.kompass.Destination
import com.tekmoon.kompass.KompassNavigationGraph
import com.tekmoon.kompass.KompassNavigationHost
import com.tekmoon.kompass.KompassNavController
import com.tekmoon.kompass.NavigationResult
import com.tekmoon.kompass.ResultKey
import com.tekmoon.kompass.ResultState
import com.tekmoon.kompass.peekResult
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

        /**
         * The result contract of this destination.
         *
         * Declaring the key on the producer keeps the contract next to the screen that fulfils it,
         * and makes the name unique without a convention to remember.
         */
        val Name = ResultKey<NameResult>("kompass/sample1/second/name")
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
    // A result is state in the back stack, not a callback. It stays in the entry until the screen
    // closes the request. So read the state in an effect, consume it there, and keep what the
    // screen must show in its own state.
    var name by rememberSaveable { mutableStateOf<String?>(null) }

    val request = entry.peekResult(Sample1Destination.Second.Name)

    LaunchedEffect(request) {
        when (val closed = navController.consumeResult(Sample1Destination.Second.Name, entry.id)) {
            is ResultState.Delivered -> name = closed.value.name
            ResultState.Cancelled -> name = "cancelled"
            // Pending is never returned by consumeResult. Null means there was nothing to close.
            ResultState.Pending, null -> Unit
        }
    }

    Column {
        Text(if (request == ResultState.Pending) "Result: waiting…" else "Result: ${name ?: "-"}")

        Button(onClick = {
            // navigateForResult records that this entry waits for an answer. Without it, Back
            // could not be told apart from "the screen is still open".
            navController.navigateForResult(
                entry = Sample1Destination.Second.toKompassEntry(),
                resultKey = Sample1Destination.Second.Name,
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
    Column {
        Button(onClick = {
            navController.pop(
                result = NameResult("Luke Skywalker"),
                resultKey = Sample1Destination.Second.Name
            )
        }) {
            Text("Return Result")
        }

        // Back does the same thing, and the caller reads ResultState.Cancelled.
        Button(onClick = { navController.pop() }) {
            Text("Cancel")
        }
    }
}
