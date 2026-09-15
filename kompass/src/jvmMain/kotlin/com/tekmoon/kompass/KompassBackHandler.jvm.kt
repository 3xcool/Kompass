package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/** Uses the Compose desktop dispatcher, which carries the ESC key. */
@Composable
actual fun KompassBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    NavigationEventBackHandler(enabled, backPressedChannel, onBack)
}
