package com.tekmoon.kompass.util

import androidx.compose.runtime.Stable
import kotlinx.coroutines.channels.Channel

@Stable
class BackPressedChannel {
    val channel = Channel<Boolean>(1)
}