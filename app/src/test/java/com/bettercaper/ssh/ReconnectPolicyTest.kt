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

    @Test
    fun authenticationAndHostKeyFailuresDoNotRetry() {
        val policy = ReconnectPolicy()
        val first = policy.beginUserAttempt()
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(first, success = true, retryOnFailure = false)
        )
        assertEquals(ReconnectAction.Schedule, policy.onTransportLost(first))
        assertEquals(ReconnectAction.Start, policy.onDelayElapsed(first))
        val retry = policy.beginAttempt()

        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(retry, success = false, retryOnFailure = true, retryable = false)
        )
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(retry))
    }

    @Test
    fun automaticReconnectsStopAfterThree() {
        val policy = ReconnectPolicy()
        var generation = policy.beginUserAttempt()
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(generation, success = true, retryOnFailure = false)
        )
        assertEquals(ReconnectAction.Schedule, policy.onTransportLost(generation))
        assertEquals(ReconnectAction.Start, policy.onDelayElapsed(generation))

        repeat(2) {
            generation = policy.beginAttempt()
            assertEquals(
                ReconnectAction.Schedule,
                policy.onAttemptFinished(generation, success = false, retryOnFailure = true, retryable = true)
            )
            assertEquals(ReconnectAction.Start, policy.onDelayElapsed(generation))
        }

        generation = policy.beginAttempt()
        assertEquals(
            ReconnectAction.GiveUp,
            policy.onAttemptFinished(generation, success = false, retryOnFailure = true, retryable = true)
        )
        assertEquals(ReconnectAction.Ignore, policy.onTransportLost(generation))
    }

    @Test
    fun successfulReconnectResetsTheBudget() {
        val policy = ReconnectPolicy()
        var generation = policy.beginUserAttempt()
        assertEquals(
            ReconnectAction.Ignore,
            policy.onAttemptFinished(generation, success = true, retryOnFailure = false)
        )
        repeat(ReconnectPolicy.MAX_AUTOMATIC_RECONNECTS) {
            assertEquals(ReconnectAction.Schedule, policy.onTransportLost(generation))
            assertEquals(ReconnectAction.Start, policy.onDelayElapsed(generation))
            generation = policy.beginAttempt()
            assertEquals(
                ReconnectAction.Ignore,
                policy.onAttemptFinished(generation, success = true, retryOnFailure = true)
            )
        }
        assertEquals(ReconnectAction.Schedule, policy.onTransportLost(generation))
    }
}
