package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Uses the Compose web dispatcher. ESC completes back; browser history is host-owned. */
@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    NavigationEventPredictiveBackHandler(enabled, onStart, onProgress, onCommit, onCancel)
}

@Composable
internal actual fun Modifier.platformPredictiveBackInput(): Modifier = predictiveBackEdgeSwipe()
