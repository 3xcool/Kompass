package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/**
 * A browser gives Compose no system back button to intercept, so this actual does nothing.
 *
 * The browser back button drives history, not the Compose back stack. To wire the two together, a
 * host application must observe the History API itself and push the event into
 * [backPressedChannel]. The library does not do that for the application, because the correct
 * behaviour depends on whether the application maps its back stack onto browser history at all.
 *
 * Show a back control in your own UI on this platform, the same way the iOS actual expects.
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit
) {
    // No system back button in a browser. See the note above.
}
