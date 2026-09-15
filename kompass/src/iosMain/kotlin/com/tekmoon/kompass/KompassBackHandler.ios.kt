package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/** Uses the Compose iOS dispatcher, which carries the native edge swipe. */
@Composable
actual fun KompassBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    NavigationEventBackHandler(enabled, backPressedChannel, onBack)
}
