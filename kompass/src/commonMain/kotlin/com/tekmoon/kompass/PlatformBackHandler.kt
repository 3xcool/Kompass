package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.coroutines.channels.Channel

/**
 * Handles back press events from the platform.
 *
 * Supports both:
 * 1. System back button (Android)
 * 2. External back press events via channel
 * 3. Desktop ESC button
 *
 * This handler never receives a native event on iOS or on the web, so it only works there through
 * [backPressedChannel]. [KompassBackHandler] handles a native event on every target, and it keeps
 * the same parameters. Behaviour on Android and on desktop does not change.
 *
 * @param enabled Whether back handler is active
 * @param backPressedChannel Optional channel to listen for external back presses
 * @param onBack Callback when back is pressed
 */
@Deprecated(
    message = "Use KompassBackHandler, which handles a native back event on every target.",
    replaceWith = ReplaceWith("KompassBackHandler(enabled, backPressedChannel, onBack)"),
)
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    backPressedChannel: BackPressedChannel? = null,
    onBack: () -> Unit
)