package com.tekmoon.kompass

import androidx.compose.animation.ContentTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Contract of the transition layer: the presets, the guards, and the fallback that lets a
 * direction-only implementation keep working after the context-aware overload was added.
 */
class SceneTransitionTest {

    private object A : Destination { override val id = "a" }
    private object B : Destination { override val id = "b" }

    @Test
    fun the_default_transition_rejects_values_that_cannot_animate() {
        assertFailsWith<IllegalArgumentException> { SceneTransitionDefault(parallaxFactor = 1.5f) }
        assertFailsWith<IllegalArgumentException> { SceneTransitionDefault(parallaxFactor = -0.1f) }
        assertFailsWith<IllegalArgumentException> { SceneTransitionDefault(durationMs = 0) }
    }

    @Test
    fun the_presets_describe_their_speed_and_depth() {
        assertEquals(200, SceneTransitionDefault.Fast().durationMs)
        assertEquals(0.3f, SceneTransitionDefault.Fast().parallaxFactor)
        assertEquals(500, SceneTransitionDefault.Slow().durationMs)
        assertEquals(0.5f, SceneTransitionDefault.Slow().parallaxFactor)
        assertEquals(0f, SceneTransitionDefault.Flat().parallaxFactor)
    }

    @Test
    fun every_built_in_transition_produces_a_transform_for_both_directions() {
        val transitions = listOf(
            SceneTransitionDefault(),
            SceneTransitionDefault(fadeEnabled = false),
            SceneTransitionVertical(),
            SceneTransitionStatic,
        )

        transitions.forEach { transition ->
            assertNotNull(transition.transition(NavDirection.Push))
            assertNotNull(transition.transition(NavDirection.Pop))
        }
    }

    @Test
    fun a_direction_only_transition_still_answers_a_context_aware_call() {
        val directions = mutableListOf<NavDirection>()
        val transition = object : SceneTransition {
            override fun transition(direction: NavDirection): ContentTransform {
                directions += direction
                return SceneTransitionStatic.transition(direction)
            }
        }
        val context = SceneTransitionContext(
            from = A.toBackStackEntry(),
            to = B.toBackStackEntry(),
            direction = NavDirection.Pop,
        )

        transition.transition(context)

        assertEquals(listOf(NavDirection.Pop), directions)
    }

    @Test
    fun a_context_aware_transition_sees_both_endpoints() {
        lateinit var seen: SceneTransitionContext
        val transition = object : SceneTransition {
            override fun transition(context: SceneTransitionContext): ContentTransform {
                seen = context
                return SceneTransitionStatic.transition(context.direction)
            }
        }
        val context = SceneTransitionContext(
            from = A.toBackStackEntry(args = """{"userId":"1"}"""),
            to = B.toBackStackEntry(),
            direction = NavDirection.Push,
        )

        transition.transition(context)

        assertEquals("a", seen.from.destinationId)
        assertEquals("""{"userId":"1"}""", seen.from.args)
        assertEquals("b", seen.to.destinationId)
        assertEquals(NavDirection.Push, seen.direction)
    }
}
