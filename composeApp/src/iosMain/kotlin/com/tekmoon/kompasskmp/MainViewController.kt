package com.tekmoon.kompasskmp

import androidx.compose.ui.window.ComposeUIViewController
import com.tekmoon.kompass.DeepLinkChannel

/**
 * The deep link bridge for Swift.
 *
 * SwiftUI delivers a URL through `onOpenURL`, on a cold start and while the app runs. The channel
 * buffers, so a URL that arrives before the composition observes it is not lost. Swift reaches this
 * as `IosDeepLinks.shared.channel`.
 */
object IosDeepLinks {
    val channel: DeepLinkChannel = DeepLinkChannel()
}

fun MainViewController() = ComposeUIViewController {
    App(deepLinkChannel = IosDeepLinks.channel)
}
