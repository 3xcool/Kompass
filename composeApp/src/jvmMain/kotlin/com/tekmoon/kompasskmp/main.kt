package com.tekmoon.kompasskmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KompassKmp",
    ) {
        // Compose dispatches ESC to the active back handler; forwarding it through a channel
        // would bypass native handler precedence and could handle the same key twice.
        App()
    }
}
