package com.tekmoon.kompass

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset


/**
 * Represents the direction of a navigation change.
 *
 * This information is derived from differences between
 * consecutive [NavigationState] instances and is used
 * primarily to drive directional animations.
 */
enum class NavDirection {

    /**
     * Indicates that a new destination was added to the back stack.
     */
    Push,

    /**
     * Indicates that one or more destinations were removed
     * from the back stack.
     */
    Pop
}

/**
 * Determines the navigation direction by comparing this state
 * with a previous [NavigationState].
 *
 * @param previous The previous navigation state.
 *
 * @return [NavDirection.Push] if the back stack grew,
 * [NavDirection.Pop] otherwise.
 */
internal fun NavigationState.directionFrom(
    previous: NavigationState
): NavDirection =
    if (backStack.size > previous.backStack.size)
        NavDirection.Push
    else
        NavDirection.Pop

/**
 * Abstraction defining how transitions between destinations are animated.
 *
 * A [SceneTransition] is responsible only for describing animation behavior.
 * It does not know about destinations, layouts, or navigation rules.
 *
 * Implementations are expected to return a [ContentTransform] compatible
 * with Compose animation APIs.
 */
interface SceneTransition {

    /**
     * Produces a [ContentTransform] based on the navigation direction.
     *
     * @param direction The direction of navigation that triggered
     * the transition.
     *
     * @return A [ContentTransform] describing how content should enter
     * and exit.
     */
    fun transition(
        direction: NavDirection
    ): ContentTransform
}

/**
 * Utility function that adapts a [SceneTransition] into a form
 * consumable by [AnimatedContent].
 *
 * This allows navigation direction to be injected into the
 * transition logic without leaking navigation concepts into UI code.
 *
 * @param direction The current navigation direction.
 *
 * @param transition The [SceneTransition] implementation to use.
 *
 * @return A lambda suitable for [AnimatedContentTransitionScope].
 */
internal fun <T> directionalTransition(
    direction: NavDirection,
    transition: SceneTransition = SceneTransitionDefault()
): AnimatedContentTransitionScope<T>.() -> ContentTransform = {
    transition.transition(direction)
}


/**
 * Customizable slide transition with optional parallax and fade effects.
 *
 * This is the default animated transition used by Kompass.
 * It supports both push and pop directions with mirrored behavior.
 *
 * @param durationMs Animation duration in milliseconds.
 *
 * @param parallaxFactor Controls how much of the exiting screen
 * remains visible during the transition. A value of 0.33 means
 * one third of the screen remains visible.
 *
 * @param easing Easing curve applied to the animation timing.
 *
 * @param fadeEnabled Whether fade-in and fade-out effects
 * should be applied in addition to the slide animation.
 */
data class SceneTransitionDefault(
    val durationMs: Int = 300,
    val parallaxFactor: Float = 0.33f,
    val easing: Easing = FastOutSlowInEasing,
    val fadeEnabled: Boolean = true
) : SceneTransition {

    init {
        require(parallaxFactor in 0f..1f) { "parallaxFactor must be between 0 and 1" }
        require(durationMs > 0) { "durationMs must be positive" }
    }

    /**
     * Creates a directional slide animation with optional parallax
     * and fade effects.
     *
     * @param direction The direction of navigation driving the animation.
     *
     * @return A [ContentTransform] describing enter and exit animations.
     */
    override fun transition(direction: NavDirection): ContentTransform {
        val enterSpec = tween<IntOffset>(durationMs, easing = easing)
        val exitSpec = tween<IntOffset>(durationMs, easing = easing)

        return when (direction) {
            NavDirection.Push -> {
                val enter = slideInHorizontally(enterSpec) { fullWidth -> fullWidth } +
                        if (fadeEnabled) fadeIn(tween(durationMs, easing = easing)) else EnterTransition.None

                val exit = slideOutHorizontally(exitSpec) { fullWidth -> (-fullWidth * parallaxFactor).toInt() } +
                        if (fadeEnabled) fadeOut(tween(durationMs, easing = easing)) else ExitTransition.None

                enter togetherWith exit
            }

            NavDirection.Pop -> {
                val enter = slideInHorizontally(enterSpec) { fullWidth -> (-fullWidth * parallaxFactor).toInt() } +
                        if (fadeEnabled) fadeIn(tween(durationMs, easing = easing)) else EnterTransition.None

                val exit = slideOutHorizontally(exitSpec) { fullWidth -> fullWidth } +
                        if (fadeEnabled) fadeOut(tween(durationMs, easing = easing)) else ExitTransition.None

                enter togetherWith exit
            }
        }
    }

    companion object {

        /**
         * Creates a fast transition with minimal parallax.
         *
         * Useful for lightweight navigation flows or
         * high-frequency transitions.
         */
        fun Fast() = SceneTransitionDefault(
            durationMs = 200,
            parallaxFactor = 0.3f
        )

        /**
         * Creates a slower transition with deeper parallax.
         *
         * Useful for emphasizing navigation hierarchy changes.
         */
        fun Slow() = SceneTransitionDefault(
            durationMs = 500,
            parallaxFactor = 0.5f
        )

        /**
         * Creates a flat slide transition with no parallax.
         *
         * Useful when parallax is undesirable or visually distracting.
         */
        fun Flat() = SceneTransitionDefault(
            durationMs = 300,
            parallaxFactor = 0f
        )
    }
}

/**
 * A [SceneTransition] that disables all animations.
 *
 * This transition is useful for:
 * - Static layouts
 * - Performance-sensitive scenarios
 * - Accessibility preferences
 */
data object SceneTransitionStatic : SceneTransition {

    /**
     * Returns a no-op [ContentTransform] with no enter or exit animations.
     *
     * @param direction The navigation direction. This parameter
     * is ignored for static transitions.
     */
    override fun transition(direction: NavDirection): ContentTransform {
        return EnterTransition.None togetherWith ExitTransition.None
    }
}
