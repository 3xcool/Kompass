package com.tekmoon.samples

import androidx.compose.runtime.Composable
import com.tekmoon.kompass.samples.expenseTracker.ExpenseTrackerApp
import com.tekmoon.kompass.samples.expenseTracker.ExpenseTrackerTheme
import com.tekmoon.kompass.util.BackPressedChannel

@Composable
fun Sample8_ExpenseTracker(
    backPressedChannel: BackPressedChannel?,
    onDismiss: () -> Unit = {}
) {
    ExpenseTrackerTheme {
        ExpenseTrackerApp(
            backPressedChannel = backPressedChannel,
            onDismiss = onDismiss
        )
    }
}