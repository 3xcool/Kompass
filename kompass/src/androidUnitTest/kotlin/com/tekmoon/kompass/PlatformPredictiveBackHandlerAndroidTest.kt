package com.tekmoon.kompass

import androidx.activity.BackEventCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Covers the gesture rules of the Android actual. Robolectric is here only because
 * [BackEventCompat] is an Android class; no composition and no activity are involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlatformPredictiveBackHandlerAndroidTest {

    private class Record {
        val calls = mutableListOf<String>()
        val progress = mutableListOf<Float>()
        val gate = BackGestureGate()

        /** What the composable does when `enabled` turns false while the finger is still down. */
        fun disable() {
            if (gate.finish()) calls += "cancel"
        }
    }

    private fun event(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private suspend fun Record.run(events: List<Float>, failWith: Throwable? = null) = run(
        flow {
            events.forEach { emit(event(it)) }
            failWith?.let { throw it }
        }
    )

    private suspend fun Record.run(events: Flow<BackEventCompat>) = runBackGesture(
        events = events,
        gate = gate,
        onStart = { calls += "start" },
        onProgress = { progress += it; calls += "progress" },
        onCommit = { calls += "commit" },
        onCancel = { calls += "cancel" },
    )

    @Test fun a_completed_gesture_starts_reports_every_step_then_commits_once() = runBlocking {
        val record = Record()
        record.run(listOf(0.2f, 0.5f, 0.9f))
        assertEquals(listOf("start", "progress", "progress", "progress", "commit"), record.calls)
        assertEquals(listOf(0.2f, 0.5f, 0.9f), record.progress)
    }

    @Test fun a_back_press_with_no_drag_still_starts_and_commits() = runBlocking {
        // What the system sends below API 34: the flow completes without a single drag event.
        val record = Record()
        record.run(emptyList())
        assertEquals(listOf("start", "commit"), record.calls)
    }

    @Test fun an_abandoned_gesture_cancels_instead_of_committing() = runBlocking {
        val record = Record()
        assertFailsWith<CancellationException> {
            record.run(listOf(0.4f), failWith = CancellationException("the user let go"))
        }
        assertEquals(listOf("start", "progress", "cancel"), record.calls)
    }

    @Test fun cancellation_travels_on_so_the_caller_scope_does_not_leak() = runBlocking {
        val record = Record()
        val thrown = assertFailsWith<CancellationException> {
            record.run(emptyList(), failWith = CancellationException("gone"))
        }
        assertEquals("gone", thrown.message)
        assertEquals(listOf("start", "cancel"), record.calls)
    }

    @Test fun disabling_the_handler_mid_gesture_cancels_once_and_ignores_the_release() = runBlocking {
        // AndroidX keeps the gesture running when the handler is disabled, and the system still
        // delivers the release to it. The release must not reach onCommit.
        val record = Record()
        record.run(
            flow {
                emit(event(0.3f))
                record.disable()
                emit(event(0.8f))
            }
        )
        assertEquals(listOf("start", "progress", "cancel"), record.calls)
        assertEquals(listOf(0.3f), record.progress)
    }

    @Test fun a_gesture_abandoned_after_disabling_does_not_cancel_twice() = runBlocking {
        val record = Record()
        assertFailsWith<CancellationException> {
            record.run(
                flow {
                    emit(event(0.4f))
                    record.disable()
                    throw CancellationException("the user let go")
                }
            )
        }
        assertEquals(listOf("start", "progress", "cancel"), record.calls)
    }

    @Test fun an_ordinary_failure_is_not_reported_as_a_cancel() = runBlocking {
        val record = Record()
        assertFailsWith<IllegalStateException> {
            record.run(listOf(0.3f), failWith = IllegalStateException("broken source"))
        }
        assertEquals(listOf("start", "progress"), record.calls)
    }
}
