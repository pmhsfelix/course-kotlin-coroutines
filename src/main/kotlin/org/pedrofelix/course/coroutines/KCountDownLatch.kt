package org.pedrofelix.course.coroutines

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class KCountDownLatch(initialCounter: Int) {
    private var counter = initialCounter
    private val lock = Mutex()
    private val waiters = mutableListOf<Continuation<Unit>>()

    suspend fun await() {
        lock.lock()
        if (counter == 0) {
            lock.unlock()
            return
        }
        suspendCoroutine { cont ->
            waiters.add(cont)
            lock.unlock()
        }
    }

    suspend fun countDown() {
        var waitersToResume: List<Continuation<Unit>>? = null
        lock.withLock {
            counter -= 1
            if (counter == 0 && waiters.isNotEmpty()) {
                waitersToResume = waiters.toList()
                waiters.clear()
            }
        }
        waitersToResume?.let {
            it.forEach { waiter ->
                waiter.resume(Unit)
            }
        }
    }
}
