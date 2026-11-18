package com.novalisten.app.enrichment

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Conservative implementation budget shared by API calls and photo bytes, not a new product setting. */
class RequestLimiter(private val monotonic: () -> Long = { System.nanoTime() / 1_000_000 }, private val wall: () -> Long = System::currentTimeMillis) {
    private val slots = Semaphore(2)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val next = ConcurrentHashMap<String, Long>()
    private val blocked = ConcurrentHashMap<String, Long>()
    fun cooldown(group: String, until: Long) { blocked.merge(group, until) { first, second -> maxOf(first, second) } }
    suspend fun <T> execute(group: String, interval: Long, block: suspend () -> T): T = locks.getOrPut(group) { Mutex() }.withLock {
        if ((blocked[group] ?: 0) > wall()) throw RemoteFailure("Fournisseur temporairement suspendu après une limite ou une panne. Réessayez plus tard.")
        val wait = (next[group] ?: 0) - monotonic()
        if (wait > 0) delay(wait)
        slots.withPermit {
            try { block() } finally { next[group] = monotonic() + interval }
        }
    }
}
