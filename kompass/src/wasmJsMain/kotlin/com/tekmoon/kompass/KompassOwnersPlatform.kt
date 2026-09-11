package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider

internal actual fun kompassViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {}

@Composable
internal actual fun rememberKompassHostRecreation(): () -> Boolean = { false }

@Composable
internal actual fun kompassPlatformCreationExtras(): CreationExtras = CreationExtras.Empty
