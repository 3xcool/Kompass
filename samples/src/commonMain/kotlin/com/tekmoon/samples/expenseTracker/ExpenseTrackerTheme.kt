package com.tekmoon.kompass.samples.expenseTracker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Theme Colors
object ExpenseTrackerColorsLight {
    val Primary = Color(0xFF1E40AF)        // Deep Blue
    val PrimaryLight = Color(0xFF3B82F6)   // Bright Blue
    val Secondary = Color(0xFF059669)      // Green (for amounts)
    val Background = Color(0xFFF8F9FB)     // Light Gray
    val Surface = Color(0xFFFFFFFF)        // White
    val OnSurface = Color(0xFF1F2937)      // Dark Gray
    val SurfaceVariant = Color(0xFFE5E7EB) // Light Border
    val ErrorLight = Color(0xFFEF4444)     // Red
    val WarningLight = Color(0xFFF59E0B)   // Amber
}

// Dark Theme Colors
object ExpenseTrackerColorsDark {
    val Primary = Color(0xFF60A5FA)        // Bright Blue
    val PrimaryLight = Color(0xFF93C5FD)   // Light Blue
    val Secondary = Color(0xFF10B981)      // Green
    val Background = Color(0xFF0F172A)     // Very Dark Blue
    val Surface = Color(0xFF1E293B)        // Dark Slate (darker for cards)
    val OnSurface = Color(0xFFE2E8F0)      // Light Gray (for text)
    val SurfaceVariant = Color(0xFF334155) // Dark Border
    val ErrorLight = Color(0xFFFCA5A5)     // Light Red
    val WarningLight = Color(0xFFFDE047)   // Light Amber
}

val ExpenseTrackerLightColorScheme = lightColorScheme(
    primary = ExpenseTrackerColorsLight.Primary,
    secondary = ExpenseTrackerColorsLight.Secondary,
    background = ExpenseTrackerColorsLight.Background,
    surface = ExpenseTrackerColorsLight.Surface,
    onSurface = ExpenseTrackerColorsLight.OnSurface,
    error = ExpenseTrackerColorsLight.ErrorLight
)

val ExpenseTrackerDarkColorScheme = darkColorScheme(
    primary = ExpenseTrackerColorsDark.Primary,
    secondary = ExpenseTrackerColorsDark.Secondary,
    background = ExpenseTrackerColorsDark.Background,
    surface = ExpenseTrackerColorsDark.Surface,
    onSurface = ExpenseTrackerColorsDark.OnSurface,
    error = ExpenseTrackerColorsDark.ErrorLight
)

@Composable
fun ExpenseTrackerTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) {
        ExpenseTrackerDarkColorScheme
    } else {
        ExpenseTrackerLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}