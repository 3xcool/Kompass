package com.tekmoon.kompasskmp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * Desktop entry point.
 *
 * The first argument, when there is one, is treated as a deep link. Sample 5 uses it:
 *
 * ```
 * ./gradlew :composeApp:run --args="myapp://profile/42"
 * ```
 *
 * A desktop build registers no URL scheme with the operating system, so this is a cold start only.
 * There is no warm-start channel here, unlike Android.
 */
fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KompassKmp",
    ) {
        // Compose dispatches ESC to the active back handler; forwarding it through a channel
        // would bypass native handler precedence and could handle the same key twice.
        App(deepLinkUri = args.firstOrNull())
    }
}
