package com.tekmoon.kompass

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel

/** Uses the activity back dispatcher, so it keeps the priority order of every other back handler. */
@Composable
actual fun KompassBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?,
    onBack: () -> Unit,
) {
    BackHandler(enabled, onBack)
    BackPressedChannelEffect(enabled, backPressedChannel, onBack)
}
