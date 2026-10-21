package io.github.kust3r3nk0.tgwsandroid.core

import java.util.concurrent.TimeUnit

/** Monotonic, per-route retry budget. No payload replay, sleeping workers or retry loop. */
internal class RouteBackoff<K>(
    initialMillis: Int = 1_000,
    maximumMillis: Int = 30_000,
    private val adaptive:()->Boolean={true},
    private val now: () -> Long = System::nanoTime,
) {
    private val initial = TimeUnit.MILLISECONDS.toNanos(initialMillis.toLong())
    private val maximum = TimeUnit.MILLISECONDS.toNanos(maximumMillis.toLong())
    private var generation = 0L
    private var sequence = 0L
    private data class State(var failures: Int = 0, var until: Long = 0, var outcome: Long = 0, var probe: Long? = null,
        var pressureUntil: Long? = null, var pressureProbe: Boolean = false, var lastFailure:Long=0)
    private val states = mutableMapOf<K, State>()
    class Permit<K>(val key: K, val generation: Long, val sequence: Long) {
        internal val finished = java.util.concurrent.atomic.AtomicBoolean()
        internal val canRecover = java.util.concurrent.atomic.AtomicBoolean()
    }

    init { require(initialMillis > 0 && maximumMillis >= initialMillis) }

    @Synchronized fun acquire(key: K): Permit<K>? {
        val state = states.getOrPut(key) { State() }
        if (state.pressureUntil?.let { now() - it < 0 } == true) return null
        if ((state.failures > 0 && now() - state.until < 0) || state.probe != null) return null
        val permit = Permit(key, generation, ++sequence)
        if (state.failures > 0 || state.pressureProbe) state.probe = permit.sequence
        return permit
    }

    @Synchronized fun success(permit: Permit<K>) = finish(permit, failed = false)
    @Synchronized fun failure(permit: Permit<K>) = finish(permit, failed = true)
    /** Opening a socket releases a probe but does not prove MTProto progress. */
    @Synchronized fun connected(permit: Permit<K>) = finish(permit, failed = false, healthy = false)
    private fun finish(permit: Permit<K>, failed: Boolean, healthy:Boolean=true) {
        if (!permit.finished.compareAndSet(false, true)) return
        if (permit.generation != generation) return
        val state = states[permit.key] ?: return
        if (state.probe == permit.sequence) {
            state.probe = null
            if (!failed && state.pressureUntil?.let { now() - it >= 0 } == true) state.pressureProbe = false
        }
        if (permit.sequence < state.outcome) return
        state.outcome = permit.sequence
        if (!failed) { if(healthy){state.failures = 0; state.until = 0}else permit.canRecover.set(true); return }
        state.failures = if(adaptive())(state.failures + 1).coerceAtMost(30)else 1
        state.lastFailure=permit.sequence
        var delay = initial
        repeat(state.failures - 1) { delay = if (delay >= maximum / 2) maximum else delay * 2 }
        state.until = now() + minOf(delay, maximum)
    }

    /** Runtime failures get a newer outcome than any older in-flight setup. */
    @Synchronized fun reject(permit:Permit<K>) {
        if(permit.generation!=generation || !permit.canRecover.compareAndSet(true,false))return
        finish(Permit(permit.key,generation,++sequence),failed=true)
    }

    @Synchronized fun recovered(permit:Permit<K>) {
        if(!permit.canRecover.compareAndSet(true,false))return
        if(permit.generation!=generation)return
        val state=states[permit.key]?:return
        if(permit.sequence<state.lastFailure)return
        state.failures=0;state.until=0
    }

    @Synchronized fun cancel(permit: Permit<K>) {
        if (!permit.finished.compareAndSet(false, true)) return
        if (permit.generation == generation && states[permit.key]?.probe == permit.sequence) states[permit.key]?.probe = null
    }

    /** Endpoint overload is shared by DCs; an in-flight success cannot erase Retry-After. */
    @Synchronized fun defer(key: K, millis: Int) {
        val state = states.getOrPut(key) { State() }
        state.pressureProbe = true
        val delay = TimeUnit.MILLISECONDS.toNanos(millis.coerceAtLeast(1).toLong()).coerceAtMost(maximum)
        val deadline = now() + delay
        if (state.pressureUntil == null || deadline - state.pressureUntil!! > 0) state.pressureUntil = deadline
    }

    @Synchronized fun remainingMillis(key: K): Long {
        val state = states[key] ?: return 0
        val left = maxOf(if (state.failures > 0) state.until - now() else 0,
            state.pressureUntil?.minus(now()) ?: 0)
        return if (left <= 0) 0 else TimeUnit.NANOSECONDS.toMillis(left).coerceAtLeast(1)
    }

    @Synchronized fun reset() { generation++; states.clear() }
}
