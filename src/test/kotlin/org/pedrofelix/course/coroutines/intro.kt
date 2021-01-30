package org.pedrofelix.course.coroutines

import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(IntroExamples::class.java)

class IntroExamples {

    /**
     * A regular function that just waits for some time to elapse
     * using a regular [Thread.sleep]
     */
    fun sleepForABit(ms: Long) {
        log.info("Before sleep")
        Thread.sleep(ms)
        log.info("After sleep")
    }

    /**
     * What can we do with a regular function?
     * - Call it from another regular function
     * - Create one or more threads to execute it
     * - Schedule it execution on an executor
     */
    @Test
    fun call_regular_function_from_another_regular_function() {
        sleepForABit(500)
    }

    @Test
    fun use_a_regular_function_to_create_two_threads() {
        log.info("starting")
        // using the Thread constructor
        val th0 = Thread {
            sleepForABit(500)
        }.apply { start() }

        // using an helper method to automatically start the Thread
        val th1 = startThread {
            sleepForABit(1000)
        }
        log.info("Waiting for threads to end")
        join(th0, th1)
    }

    /**
     * A suspend function that just waits for some time to elapse
     * using a [delay] and not [Thread.sleep]
     */
    suspend fun delayForABit(ms: Long) {
        log.info("Before sleep")
        delay(ms)
        log.info("After sleep")
    }

    /**
     * What can we do with a suspend function?
     * - Call it from another suspend function
     * - Create one or more coroutines to execute it
     */

    // @Test - Running this function as a test will fail. More on this later.
    suspend fun call_suspend_function_from_another_suspend_function() {
        delayForABit(500)
    }

    /**
     * - The meaning of [GlobalScope] and [Dispatchers.Unconfined]
     * will be discussed later.
     * - Take a look on the log messages, namely observe that all log messages are
     * from the same thread.
     * - A coroutine does not monopolize a thread during it execution.
     * Namely, a single thread can be used to run multiple coroutines, using time multiplexing
     * This is similar to how a single CPU can be used to run multiple threads,
     * using a similar method.
     */
    @Test
    fun use_a_suspend_function_to_create_two_coroutines() {
        log.info("starting")
        // using the launch coroutine builder
        val co0 = GlobalScope.launch(Dispatchers.Unconfined) {
            sleepForABit(500)
        }

        val co1 = GlobalScope.launch(Dispatchers.Unconfined) {
            sleepForABit(1000)
        }
        log.info("Waiting for coroutines to end")
        joinBlocking(co0, co1)
    }

}