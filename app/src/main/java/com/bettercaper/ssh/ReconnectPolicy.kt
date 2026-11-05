package com.bettercaper.ssh

/**
 * Single-flight reconnect decisions for one bettercap session.
 *
 * SSH disconnect and WebSocket failure often arrive together. This policy
 * lets only one of them schedule a reconnect, ignores callbacks from a socket
 * that has already been replaced, and does not retry a failed first login.
 * The class is safe to call from the SSH thread and the main thread.
 */
class ReconnectPolicy {
    private val lock = Any()
    private var generation = 0
    private var intentional = false
    private var attemptOpen = false
    private var acceptLoss = false
    private var reconnectPending = false
    private var startClaimed = false

    /** Starts a user-requested or delayed connection attempt. */
    fun beginAttempt(): Int = synchronized(lock) {
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
    }

    fun isCurrent(eventGeneration: Int): Boolean = synchronized(lock) {
        eventGeneration == generation && !intentional
    }

    fun onTransportLost(eventGeneration: Int): ReconnectAction = synchronized(lock) {
        if (intentional || eventGeneration != generation || !acceptLoss) return ReconnectAction.Ignore
        if (attemptOpen || reconnectPending) return ReconnectAction.Ignore
        reconnectPending = true
        ReconnectAction.Schedule
    }

    /**
     * @param retryOnFailure true only for an attempt that was itself a reconnect.
     * A failed first login surfaces the error and does not loop.
     */
    fun onAttemptFinished(
        eventGeneration: Int,
        success: Boolean,
        retryOnFailure: Boolean
    ): ReconnectAction = synchronized(lock) {
        if (eventGeneration != generation) return ReconnectAction.Ignore
        attemptOpen = false
        if (intentional || success) {
            reconnectPending = false
            acceptLoss = success && !intentional
            return ReconnectAction.Ignore
        }
        if (!retryOnFailure) {
            acceptLoss = false
            reconnectPending = false
            return ReconnectAction.Ignore
        }
        if (reconnectPending) return ReconnectAction.Ignore
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
    Start
}
