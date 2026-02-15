package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.util.BackPressedChannel
import kotlinx.coroutines.channels.Channel

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    backPressedChannel: BackPressedChannel?, // for desktop
    onBack: () -> Unit
) {
//    if (enabled) {
//        val viewController = getTopViewController()
//        viewController.inputViewController.
//        viewController?.popViewControllerAnimated(true) ?: onBack()
//    }
    // Let's show header icon for iOS
}
