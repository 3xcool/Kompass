package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Handles a back action on every supported target.
 *
 * Android uses the activity back dispatcher. iOS, desktop and web use the Compose window's
 * navigation event dispatcher, which carries the native edge swipe and the ESC key. This replaces
 * [PlatformBackHandler], which never handled a native event on iOS or on the web.
 *
 * Use this for a back action that no [KompassNavController] owns, such as leaving a flow at its root. For
 * the back action that pops a controller, use [KompassPredictiveBackHandler] instead. Two enabled
 * handlers compete for the same event, so install only one for each action.
 *
 * ```
 * KompassPredictiveBackHandler(navController)
 * KompassBackHandler(enabled = !navController.canGoBack()) { onDismiss() }
 * ```
 *
 * @param enabled Whether the handler listens for a back event.
 * @param backPressedChannel Optional channel for a back event that the host application raises.
 * @param onBack Called once for each back event.
 */
@Composable
expect fun KompassBackHandler(
    enabled: Boolean = true,
    backPressedChannel: BackPressedChannel? = null,
    onBack: () -> Unit,
)

/**
 * Collects the optional host channel.
 *
 * Every actual of [KompassBackHandler] calls this, so the channel behaves the same on every target.
 */
@Composable
internal fun BackPressedChannelEffect(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    val latestOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(enabled, backPressedChannel) {
        if (enabled && backPressedChannel != null) {
            backPressedChannel.channel.receiveAsFlow().collect { pressed ->
                if (pressed) latestOnBack()
            }
        }
    }
}
