package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/**
 * Shared body of [KompassBackHandler] on every target that uses the Compose dispatcher.
 *
 * Ordinary back uses the same dispatcher as the predictive handler, so a root handler and a nested
 * handler share one precedence order.
 */
@Composable
internal fun NavigationEventBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    NavigationEventPredictiveBackHandler(
        enabled = enabled,
        onStart = {},
        onProgress = {},
        onCommit = onBack,
        onCancel = {},
    )
    BackPressedChannelEffect(enabled, backPressedChannel, onBack)
}
