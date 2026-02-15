package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?, // for desktop
    onBack: () -> Unit
) {
    if (backPressedChannel == null) return

    LaunchedEffect(backPressedChannel, onBack) {
        backPressedChannel.channel.receiveAsFlow().collect { event ->
            if (event) {
                onBack()
            }
        }
    }
}