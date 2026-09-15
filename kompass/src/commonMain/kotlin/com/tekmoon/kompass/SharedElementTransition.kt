@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.tekmoon.kompass

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.collections.immutable.ImmutableList

/** Marks Kompass APIs backed by Compose's experimental shared-transition support. */
@RequiresOptIn(
    message = "Shared element transitions depend on an experimental Compose animation API.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalKompassSharedTransitionApi

/**
 * Shared-transition coordinator installed by [KompassSharedTransitionHost].
 *
 * It is nullable so destination content can remain reusable under a regular
 * [KompassNavigationHost], where shared elements are intentionally disabled.
 */
@ExperimentalKompassSharedTransitionApi
val LocalKompassSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    staticCompositionLocalOf { null }

/**
 * Visibility scope of the built-in animated [SceneLayout] currently rendering destination content.
 *
 * Static layouts provide no scope. Custom animated layouts can provide their own scope around
 * [KompassNavigationGraph.Content] with [CompositionLocalProvider].
 */
@ExperimentalKompassSharedTransitionApi
val LocalKompassAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    staticCompositionLocalOf { null }

/**
 * Kompass host with one shared-transition coordinator spanning outgoing and incoming destinations.
 *
 * Destination content reads [LocalKompassSharedTransitionScope] and
 * [LocalKompassAnimatedVisibilityScope], then applies Compose's `sharedElement` or `sharedBounds`
 * modifier. Use [KompassNavigationHost] when shared element transitions are not needed.
 */
@ExperimentalKompassSharedTransitionApi
@Composable
fun KompassSharedTransitionHost(
    navController: KompassNavController,
    graphs: ImmutableList<KompassNavigationGraph>,
): Unit {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalKompassSharedTransitionScope provides this) {
            KompassNavigationHost(navController = navController, graphs = graphs)
        }
    }
}
