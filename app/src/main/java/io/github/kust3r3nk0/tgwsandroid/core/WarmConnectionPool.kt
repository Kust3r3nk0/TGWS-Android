package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class WssRoute(val address: String, val domain: String)

/** Only unused WSS connections enter this pool. Leases are single-use and never returned. */
internal class WarmConnectionPool<K>(
    private val capacity: Int,
    maxAgeMillis: Int,
    private val connectMillis: Int,
    private val watchdog: ScheduledThreadPoolExecutor,
    private val opener: (K, (Closeable) -> Unit) -> RemoteTransport?,
    private val now: () -> Long = System::nanoTime,
) : Closeable {
    private val lock = Any()
    private val maxAge = TimeUnit.MILLISECONDS.toNanos(maxAgeMillis.toLong())
    private var generation = 0L
    private var closed = false
    private val idle = linkedMapOf<K, Entry>()
    private val pending = mutableMapOf<K, Job>()
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(maxOf(1, capacity)),
        { task -> Thread(task, "tgws-preconnect").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())

    init { require(capacity in 0..8 && maxAgeMillis > 0 && connectMillis > 0) }

    private class Owned : Closeable {
        private val resources = mutableSetOf<Closeable>()
        val closed = AtomicBoolean()
        private val deadlineActive = AtomicBoolean(true)
        fun expire() { if (deadlineActive.compareAndSet(true, false)) close() }
        fun retain() = deadlineActive.compareAndSet(true, false) && !closed.get()
        fun own(value: Closeable) {
            val release = synchronized(resources) { if (closed.get()) true else { resources += value; false } }
            if (release) dispose(value)
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val values = synchronized(resources) { resources.toList().also { resources.clear() } }
            values.asReversed().forEach(::dispose)
        }
        companion object {
            private fun dispose(value: Closeable) {
                try { if (value is RemoteTransport) value.abort() else value.close() } catch (_: Exception) { }
            }
        }
    }
    private class Entry(val channel: RemoteTransport, val owned: Owned, val created: Long) : RemoteTransport {
        override val kind get() = channel.kind
        override fun send(data: ByteArray) = channel.send(data)
        override fun receive() = channel.receive()
        override fun verifyIdle() = channel.verifyIdle()
        override fun abort() = owned.close()
        override fun close() = abort()
    }
    private class Job(val owned: Owned = Owned(), var future: Future<*>? = null)

    fun take(key: K): RemoteTransport? {
        val entry = synchronized(lock) { if (closed) null else idle.remove(key) } ?: return null
        if (now() - entry.created < maxAge && !entry.owned.closed.get()) return entry
        entry.abort()
        return null
    }

    fun refill(key: K) {
        synchronized(lock) {
            if (closed || capacity == 0 || idle.containsKey(key) || pending.containsKey(key) || idle.size + pending.size >= capacity) return
            val epoch = generation
            val job = Job()
            pending[key] = job
            try {
                job.future = workers.submit {
                    var entry: Entry? = null
                    var deadline: java.util.concurrent.ScheduledFuture<*>? = null
                    try {
                        if (!job.owned.closed.get()) {
                            deadline = watchdog.schedule({ job.owned.expire() }, connectMillis.toLong(), TimeUnit.MILLISECONDS)
                            val channel = opener(key, job.owned::own)
                            if (channel != null) {
                                job.owned.own(channel)
                                if (job.owned.retain()) entry = Entry(channel, job.owned, now())
                            }
                        }
                    } catch (_: Exception) { /* Opener records safe route outcomes, never raw exception text. */ }
                    finally {
                        deadline?.cancel(false)
                        val adopted = synchronized(lock) {
                            if (pending[key] === job) pending.remove(key)
                            if (!closed && generation == epoch && !job.owned.closed.get() && entry != null) {
                                idle[key] = entry!!
                                true
                            } else false
                        }
                        if (!adopted) job.owned.close()
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                pending.remove(key)
                job.owned.close()
            }
        }
    }

    /** Rotation closes stale idle entries without opening new connections in the absence of demand. */
    fun rotate() {
        val expired = synchronized(lock) {
            val stale = idle.filterValues { it.owned.closed.get() || now() - it.created >= maxAge }.keys.toList()
            stale.mapNotNull(idle::remove)
        }
        expired.forEach(Entry::abort)
    }

    fun invalidate() {
        val resources = synchronized(lock) {
            generation++
            val values = idle.values.map { it.owned } + pending.values.map { it.owned }
            idle.clear()
            pending.values.forEach { it.future?.cancel(true) }
            pending.clear()
            workers.purge()
            values
        }
        resources.forEach(Owned::close)
    }

    internal fun size(): Pair<Int, Int> = synchronized(lock) { idle.size to pending.size }

    override fun close() {
        synchronized(lock) { closed = true }
        invalidate()
        workers.shutdownNow()
    }
}
