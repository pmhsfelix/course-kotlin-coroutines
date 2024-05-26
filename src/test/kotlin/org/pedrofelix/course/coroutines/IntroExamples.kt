package org.pedrofelix.course.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory
import java.time.Duration

private val log = LoggerFactory.getLogger(IntroExamples::class.java)

class IntroExamples {

    /**
     * A regular function that just waits for some time to elapse
     * using a regular [Thread.sleep]
     */
    fun sleepForABit(duration: Duration) {
        log.info("Before sleep")
        Thread.sleep(duration.toMillis())
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
        sleepForABit(Duration.ofMillis(500))
    }

    @Test
    fun use_a_regular_function_to_create_two_threads() {
        log.info("starting")
        val thread0 = Thread {
            sleepForABit(Duration.ofMillis(500))
        }.apply { start() }

        val thread1 = Thread {
            sleepForABit(Duration.ofMillis(1000))
        }.apply { start() }

        log.info("Waiting for threads to end")
        join(thread0, thread1)
    }

    /**
     * A suspend function that just waits for some time to elapse
     * using a [delay] and not [Thread.sleep]
     */
    suspend fun delayForABit(duration: Duration) {
        log.info("Before sleep")
        delay(duration.toMillis())
        log.info("After sleep")
    }

    /**
     * What can we do with a suspend function?
     * - Call it from another suspend function
     * - Create one or more coroutines to execute it
     */

    // @Test - Running this function as a test will fail. More on this later.
    suspend fun call_suspend_function_from_another_suspend_function() {
        delayForABit(Duration.ofMillis(500))
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
        val coroutine0 = GlobalScope.launch {
            delayForABit(Duration.ofMillis(500))
        }

        val coroutine1 = GlobalScope.launch(Dispatchers.Unconfined) {
            delayForABit(Duration.ofMillis(100))
        }
        log.info("Waiting for coroutines to end")
        joinBlocking(coroutine0, coroutine1)
    }

    @Test
    fun use_a_suspend_function_to_create_two_coroutines_2() = runBlocking {
        log.info("starting")
        val coroutine0 = launch {
            delayForABit(Duration.ofMillis(500))
        }

        val coroutine1 = launch {
            delayForABit(Duration.ofMillis(100))
        }
        log.info("Waiting for coroutines to end")
        join(coroutine0, coroutine1)
    }
}
