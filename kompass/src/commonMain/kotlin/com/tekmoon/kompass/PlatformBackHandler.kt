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
 * @param enabled Whether back handler is active
 * @param backPressedChannel Optional channel to listen for external back presses
 * @param onBack Callback when back is pressed
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    backPressedChannel: BackPressedChannel? = null,
    onBack: () -> Unit
)