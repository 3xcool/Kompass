package com.tekmoon.kompasskmp

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.coroutines.launch

var backPressedChannel = BackPressedChannel()

fun main() = application {
    val coroutineScope = rememberCoroutineScope()
    var lastBackPressTime = 0L

    Window(
        onCloseRequest = ::exitApplication,
        title = "KompassKmp",
        onKeyEvent = { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
            if (keyEvent.isBackPressed()) {
                val currentTime = System.currentTimeMillis()
                // Debounce: only send if more than 200ms since last back press
                if (currentTime - lastBackPressTime > 200) {
                    lastBackPressTime = currentTime
                    coroutineScope.launch {
                        backPressedChannel.channel.trySend(true)
                    }
                }
                true
            } else {
                false
            }
        }
    ) {
        App(
            backPressedChannel = backPressedChannel,
        )
    }
}

fun androidx.compose.ui.input.key.KeyEvent.isBackPressed(): Boolean {
    return this.utf16CodePoint == 27 || this.utf16CodePoint == 8
}