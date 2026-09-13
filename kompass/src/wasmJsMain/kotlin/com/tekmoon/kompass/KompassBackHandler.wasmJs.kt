package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/**
 * Uses the Compose web dispatcher, which carries the ESC key.
 *
 * The browser back button drives history, not the Compose back stack. To wire the two together, a
 * host application must observe the History API itself and push the event into
 * [backPressedChannel]. The library does not do that for the application, because the correct
 * behaviour depends on whether the application maps its back stack onto browser history at all.
 */
@Composable
actual fun KompassBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    NavigationEventBackHandler(enabled, backPressedChannel, onBack)
}
