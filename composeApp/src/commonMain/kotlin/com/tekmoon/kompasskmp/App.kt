package com.tekmoon.kompasskmp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tekmoon.kompass.DeepLinkChannel
import com.tekmoon.kompass.util.BackPressedChannel
import com.tekmoon.samples.KompassNavSample
import com.tekmoon.samples.Sample8_ExpenseTracker
import org.jetbrains.compose.resources.painterResource

import kompasskmp.composeapp.generated.resources.Res
import kompasskmp.composeapp.generated.resources.compose_multiplatform

/**
 * The sample shell.
 *
 * @param deepLinkUri The URI that started the app, or null. A cold start delivers it here.
 * @param deepLinkChannel Receives a URI that arrives while the app is already running. Each platform
 * feeds it from its own entry point: `onNewIntent` on Android, the URL handler on iOS.
 */
@Composable
@Preview
fun App(
    backPressedChannel: BackPressedChannel? = null,
    deepLinkUri: String? = null,
    deepLinkChannel: DeepLinkChannel? = null,
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            KompassNavSample(
                backPressedChannel = backPressedChannel,
                deepLinkUri = deepLinkUri,
                deepLinkChannel = deepLinkChannel,
            )
        }
    }
}
