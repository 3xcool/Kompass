package com.tekmoon.kompasskmp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tekmoon.kompass.util.BackPressedChannel
import com.tekmoon.samples.KompassNavSample
import com.tekmoon.samples.Sample8_ExpenseTracker
import org.jetbrains.compose.resources.painterResource

import kompasskmp.composeapp.generated.resources.Res
import kompasskmp.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App(
    backPressedChannel: BackPressedChannel? = null
) {
    MaterialTheme {
        // See basic samples
//        KompassNavSample(
//            backPressedChannel = backPressedChannel
//        )

        Sample8_ExpenseTracker(
            backPressedChannel = backPressedChannel,
            onDismiss = {}
        )
    }
}