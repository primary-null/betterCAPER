package com.bettercaper.ssh

/**
 * Single-flight reconnect decisions for one bettercap session.
 *
 * SSH disconnect and WebSocket failure often arrive together. This policy
 * lets only one of them schedule a reconnect, ignores callbacks from a socket
 * that has already been replaced, and does not retry a failed first login.
 * Authentication and host-key failures never schedule another attempt.
 * Other losses schedule at most [MAX_AUTOMATIC_RECONNECTS] automatic reconnects.
 * The class is safe to call from the SSH thread and the main thread.
 */
class ReconnectPolicy {
    companion object {
        const val MAX_AUTOMATIC_RECONNECTS = 3
    }

    private val lock = Any()
    private var generation = 0
    private var intentional = false
    private var attemptOpen = false
    private var acceptLoss = false
    private var reconnectPending = false
    private var startClaimed = false
    private var automaticReconnects = 0

    /** User pressed Connect. Clears the automatic-reconnect budget. */
    fun beginUserAttempt(): Int = synchronized(lock) {
        automaticReconnects = 0
        startAttemptLocked()
    }

    /** Starts the next handshake after a scheduled reconnect. Does not reset the budget. */
    fun beginAttempt(): Int = synchronized(lock) {
        startAttemptLocked()
    }

    private fun startAttemptLocked(): Int {
        intentional = false
        generation += 1
        attemptOpen = true
        acceptLoss = true
        reconnectPending = false
        startClaimed = false
        generation
    }

    /** User disconnect or ViewModel teardown. Later loss events do nothing. */
    fun markIntentionalDisconnect() = synchronized(lock) {
        intentional = true
        generation += 1
        attemptOpen = false
        acceptLoss = false
        reconnectPending = false
        startClaimed = false
        automaticReconnects = 0
    }

    fun isCurrent(eventGeneration: Int): Boolean = synchronized(lock) {
        eventGeneration == generation && !intentional
    }

    fun onTransportLost(eventGeneration: Int): ReconnectAction = synchronized(lock) {
        if (intentional || eventGeneration != generation || !acceptLoss) return ReconnectAction.Ignore
        if (attemptOpen || reconnectPending) return ReconnectAction.Ignore
        if (automaticReconnects >= MAX_AUTOMATIC_RECONNECTS) {
            acceptLoss = false
            return ReconnectAction.GiveUp
        }
        automaticReconnects += 1
        reconnectPending = true
        ReconnectAction.Schedule
    }

    /**
     * @param retryOnFailure true only for an attempt that was itself a reconnect.
     * A failed first login surfaces the error and does not loop.
     * @param retryable false for authentication and host-key failures. Those stop
     * even when this attempt was a reconnect.
     */
    fun onAttemptFinished(
        eventGeneration: Int,
        success: Boolean,
        retryOnFailure: Boolean,
        retryable: Boolean = true
    ): ReconnectAction = synchronized(lock) {
        if (eventGeneration != generation) return ReconnectAction.Ignore
        attemptOpen = false
        if (intentional || success) {
            reconnectPending = false
            acceptLoss = success && !intentional
            if (success) automaticReconnects = 0
            return ReconnectAction.Ignore
        }
        if (!retryOnFailure || !retryable) {
            acceptLoss = false
            reconnectPending = false
            return ReconnectAction.Ignore
        }
        if (reconnectPending) return ReconnectAction.Ignore
        if (automaticReconnects >= MAX_AUTOMATIC_RECONNECTS) {
            acceptLoss = false
            reconnectPending = false
            return ReconnectAction.GiveUp
        }
        automaticReconnects += 1
        reconnectPending = true
        acceptLoss = true
        ReconnectAction.Schedule
    }

    /** The delay elapsed for a reconnect that [onTransportLost] or [onAttemptFinished] scheduled. */
    fun onDelayElapsed(scheduledForGeneration: Int): ReconnectAction = synchronized(lock) {
        if (intentional || scheduledForGeneration != generation || !reconnectPending || startClaimed) {
            return ReconnectAction.Ignore
        }
        startClaimed = true
        ReconnectAction.Start
    }
}

enum class ReconnectAction {
    Ignore,
    Schedule,
    Start,
    GiveUp
}
