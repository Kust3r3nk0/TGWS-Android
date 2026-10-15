package io.github.kust3r3nk0.tgwsandroid.core

import java.util.concurrent.TimeUnit

/** Monotonic, per-route retry budget. No payload replay, sleeping workers or retry loop. */
internal class RouteBackoff<K>(
    initialMillis: Int = 1_000,
    maximumMillis: Int = 30_000,
    private val now: () -> Long = System::nanoTime,
) {
    private val initial = TimeUnit.MILLISECONDS.toNanos(initialMillis.toLong())
    private val maximum = TimeUnit.MILLISECONDS.toNanos(maximumMillis.toLong())
    private var generation = 0L
    private var sequence = 0L
    private data class State(var failures: Int = 0, var until: Long = 0, var outcome: Long = 0, var probe: Long? = null)
    private val states = mutableMapOf<K, State>()
    data class Permit<K>(val key: K, val generation: Long, val sequence: Long)

    init { require(initialMillis > 0 && maximumMillis >= initialMillis) }

    @Synchronized fun acquire(key: K): Permit<K>? {
        val state = states.getOrPut(key) { State() }
        if (state.failures > 0 && (now() < state.until || state.probe != null)) return null
        val permit = Permit(key, generation, ++sequence)
        if (state.failures > 0) state.probe = permit.sequence
        return permit
    }

    @Synchronized fun success(permit: Permit<K>) = finish(permit, failed = false)
    @Synchronized fun failure(permit: Permit<K>) = finish(permit, failed = true)
    private fun finish(permit: Permit<K>, failed: Boolean) {
        if (permit.generation != generation) return
        val state = states[permit.key] ?: return
        if (state.probe == permit.sequence) state.probe = null
        if (permit.sequence < state.outcome) return
        state.outcome = permit.sequence
        if (!failed) { state.failures = 0; state.until = 0; return }
        state.failures = (state.failures + 1).coerceAtMost(30)
        var delay = initial
        repeat(state.failures - 1) { delay = if (delay >= maximum / 2) maximum else delay * 2 }
        state.until = now() + minOf(delay, maximum)
    }

    @Synchronized fun cancel(permit: Permit<K>) {
        if (permit.generation == generation && states[permit.key]?.probe == permit.sequence) states[permit.key]?.probe = null
    }

    @Synchronized fun reset() { generation++; states.clear() }
}
