package com.syncbridge.core.sync.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Cooperative pause for a running sync: workers call [awaitIfPaused] between file-level units of work
 * (never mid-file-transfer) so a paused sync leaves no file half-written. Cancellation is handled
 * separately by the caller cancelling the executing coroutine's [kotlinx.coroutines.Job] — this class
 * only ever blocks, never cancels. Backed by [MutableStateFlow] rather than a single-slot channel so
 * any number of parallel transfer workers can wait on it simultaneously without starving each other.
 */
class PauseController {
    private val pausedState = MutableStateFlow(false)

    val isPaused: Boolean get() = pausedState.value

    fun pause() {
        pausedState.value = true
    }

    fun resume() {
        pausedState.value = false
    }

    suspend fun awaitIfPaused() {
        pausedState.first { paused -> !paused }
    }
}
