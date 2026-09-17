package com.tekmoon.kompass

import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A subscription and the channel itself both need an owner that can stop them.
 *
 * The channel runs on [Dispatchers.Unconfined], so a send reaches the collector before `send`
 * returns. That keeps the test free of a scheduler and of a test-only dependency.
 */
class DeepLinkChannelTest {

    private fun channel() = DeepLinkChannel(Dispatchers.Unconfined)

    @Test fun an_observer_receives_a_link() {
        val channel = channel()
        val seen = mutableListOf<String>()
        channel.observe { seen += it }

        channel.send("app://home")

        assertEquals(listOf("app://home"), seen)
        channel.close()
    }

    @Test fun a_link_sent_before_an_observer_arrives_is_buffered() {
        val channel = channel()
        channel.send("app://early")
        val seen = mutableListOf<String>()

        channel.observe { seen += it }

        assertEquals(listOf("app://early"), seen)
        channel.close()
    }

    @Test fun a_cancelled_subscription_stops_receiving() {
        val channel = channel()
        val seen = mutableListOf<String>()
        val subscription = channel.observe { seen += it }

        subscription.cancel()
        channel.send("app://after")

        assertEquals(emptyList(), seen, "a cancelled observer must not steal the link")
        channel.close()
    }

    @Test fun a_new_observer_picks_up_what_a_cancelled_one_left() {
        val channel = channel()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        channel.observe { first += it }.cancel()

        channel.send("app://later")
        channel.observe { second += it }

        assertEquals(emptyList(), first)
        assertEquals(listOf("app://later"), second)
        channel.close()
    }

    @Test fun cancelling_twice_is_safe() {
        val channel = channel()
        val subscription = channel.observe { }
        subscription.cancel()
        subscription.cancel()
        channel.close()
    }

    @Test fun closing_stops_every_observer() {
        val channel = channel()
        val seen = mutableListOf<String>()
        channel.observe { seen += it }

        channel.close()
        channel.send("app://after")

        assertEquals(emptyList(), seen)
    }

    @Test fun closing_twice_is_safe() {
        val channel = channel()
        channel.close()
        channel.close()
    }
}
