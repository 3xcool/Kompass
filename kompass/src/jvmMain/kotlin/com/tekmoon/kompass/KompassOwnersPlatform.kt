package com.tekmoon.kompass

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

internal actual fun kompassViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
        val constructor = modelClass.java.constructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(SavedStateHandle::class.java))
        }
        @Suppress("UNCHECKED_CAST")
        return if (constructor != null) constructor.newInstance(extras.createSavedStateHandle()) as T
        else modelClass.java.getDeclaredConstructor().newInstance()
    }
}

@Composable
internal actual fun rememberKompassHostRecreation(): () -> Boolean = { false }

@Composable
internal actual fun kompassPlatformCreationExtras(): CreationExtras = CreationExtras.Empty
