package com.bettercaper.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReconnectPolicyTest {

    @Test
    fun twoLossesAfterSuccessScheduleOneReconnect() {
        val policy = ReconnectPolicy()
        val generation = policy.beginAttempt()
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(generation, success = true, retryOnFailure = false)
        )

        assertEquals(ReconnectAction.Schedule, policy.onTransportLost(generation))
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
        assertEquals(ReconnectAction.Start, policy.onDelayElapsed(generation))
        assertEquals(ReconnectAction.Ignore, policy.onDelayElapsed(generation))
    }

    @Test
    fun staleSocketCannotSchedule() {
        val policy = ReconnectPolicy()
        val first = policy.beginAttempt()
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(first, success = true, retryOnFailure = false)
        )
        val second = policy.beginAttempt()

        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(first))
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(first, success = false, retryOnFailure = true)
        )
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(second))
        assertTrue(policy.isCurrent(second))
        assertTrue(!policy.isCurrent(first))
    }

    @Test
    fun lossDuringAttemptDoesNotStackAndFailedRetrySchedulesOnce() {
        val policy = ReconnectPolicy()
        val generation = policy.beginAttempt()

        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
        assertEquals(
            ReconnectAction.Schedule,
            policy.onAttemptFinished(generation, success = false, retryOnFailure = true)
        )
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
        assertEquals(ReconnectAction.Ignore, policy.onAttemptFinished(generation, success = false, retryOnFailure = true))
    }

    @Test
    fun firstLoginFailureDoesNotRetryOrAcceptALateLoss() {
        val policy = ReconnectPolicy()
        val generation = policy.beginAttempt()
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(generation, success = false, retryOnFailure = false)
        )
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
    }

    @Test
    fun intentionalDisconnectDropsAPendingReconnect() {
        val policy = ReconnectPolicy()
        val generation = policy.beginAttempt()
        policy.onAttemptFinished(generation, success = true, retryOnFailure = false)
        assertEquals(ReconnectAction.Schedule, policy.onTransportLost(generation))

        policy.markIntentionalDisconnect()

        assertEquals(ReconnectAction.Ignore, policy.onDelayElapsed(generation))
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
    }

    @Test
    fun concurrentLossesScheduleOnce() {
        val policy = ReconnectPolicy()
        val generation = policy.beginAttempt()
        policy.onAttemptFinished(generation, success = true, retryOnFailure = false)

        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        val results = ConcurrentLinkedQueue<ReconnectAction>()
        repeat(8) {
            Thread {
                start.await()
                results.add(policy.onTransportLost(generation))
                done.countDown()
            }.start()
        }
        start.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, results.count { it == ReconnectAction.Schedule })
        assertEquals(7, results.count { it == ReconnectAction.Ignore })
    }
}
