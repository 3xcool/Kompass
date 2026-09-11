package com.tekmoon.kompass

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras

internal actual fun kompassViewModelFactory(): ViewModelProvider.Factory = SavedStateViewModelFactory()

@Composable
internal actual fun rememberKompassHostRecreation(): () -> Boolean {
    val context = LocalContext.current
    return remember(context) { { context.activity()?.isChangingConfigurations == true } }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal actual fun kompassPlatformCreationExtras(): CreationExtras {
    val application = LocalContext.current.applicationContext as? Application
    return remember(application) {
        MutableCreationExtras().apply {
            if (application != null) set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }
    }
}
