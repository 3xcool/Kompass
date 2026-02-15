package com.tekmoon.kompass.samples.expenseTracker

import com.tekmoon.kompass.SceneTransition
import com.tekmoon.kompass.SceneTransitionDefault

/**
 * Fast transition for the Expense Tracker demo
 * 150ms duration
 */
val ExpenseTrackerFastTransition: SceneTransition =
    SceneTransitionDefault(
        durationMs = 200,
        parallaxFactor = 0.3f,
        fadeEnabled = false
    )

/**
 * Ultra-fast transition (100ms)
 */
val ExpenseTrackerUltraFastTransition: SceneTransition =
    SceneTransitionDefault(
        durationMs = 100,
        parallaxFactor = 0.2f,
        fadeEnabled = true
    )

/**
 * Pre-configured fast transitions for different scenarios
 */
object FastTransitions {
    // Snappy: 150ms
    val Snappy = SceneTransitionDefault(durationMs = 150, parallaxFactor = 0.3f)

    // Lightning: 100ms
    val Lightning = SceneTransitionDefault(durationMs = 100, parallaxFactor = 0.2f)

    // Instant: 75ms (absolute minimum)
    val Instant = SceneTransitionDefault(durationMs = 75, parallaxFactor = 0.1f)

    // NoParallax: fast with no parallax distraction
    val NoParallax = SceneTransitionDefault(durationMs = 150, parallaxFactor = 0f)
}