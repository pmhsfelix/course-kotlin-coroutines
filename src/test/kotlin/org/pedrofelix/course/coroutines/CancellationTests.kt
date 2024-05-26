package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger(CancellationTests::class.java)

class CancellationTests {

    @Test
    fun `cancelling coroutines`() {
        try {
            runBlocking(Dispatchers.IO) {
                val deferred: Deferred<Int> = async {
                    Thread.sleep(1000)
                    42
                }
                cancel()
                try {
                    logger.trace("Calling await")
                    deferred.await()
                } catch (e: CancellationException) {
                    logger.trace("Inner exception catch: {}", e.message)
                }
            }
        } catch (e: CancellationException) {
            logger.trace("Outer exception catch: {}", e.message)
        }
    }
    /*
     * Takeaways:
     * - The inner coroutine is considered cancelled even if it doesn't throw any exception and does
     *   return a value (the 42).
     * - More, it is considered cancelled immediately after the `cancel`, even before it returns.
     *   That is visible in the fact that the `deferred.await` throws before the ~1000 ms elapse.
     *   This means tha the await throws in the `Cancelling` state.
     * - However the runBlocking only throws the exception after the inner coroutine is the `Cancelled` state.
     */

    @Test
    fun `cancelling coroutines with blocking operations on it`() = runBlocking(Dispatchers.IO) {
        try {
            coroutineScope {
                val c1 = launch {
                    logger.trace("Before sleep of first coroutine")
                    Thread.sleep(1000)
                    logger.trace("After sleep of first coroutine")
                }
                val c2 = launch {
                    logger.trace("Before sleep of second coroutine")
                    Thread.sleep(2000)
                    logger.trace("After sleep of second coroutine")
                }
                delay(1500)
                logger.trace("cancelling scope")
                cancel()
                logger.trace(
                    "c1.isCancelled={}, c1.isCompleted={}, c2.isCancelled={}, c2.isCompleted={}",
                    c1.isCancelled,
                    c1.isCompleted,
                    c2.isCancelled,
                    c2.isCompleted,
                )
            }
        } catch (e: CancellationException) {
            logger.trace("Handling cancellation exception")
        }
        logger.trace("After coroutineScope")
    }
    /*
     * Takeaways:
     * - Cancellation of a coroutine doesn't automatically cancel a blocking operation
     *   that may be running on that coroutine, such as a `Thread.sleep`.
     * - Looking at the trace after the `cancel`
     *   - The first coroutine was not cancelled because it completed before the `cancel`
     *   - The second coroutine is cancelled, however it's still not completed when the cancel returns,
     *     namely because the coroutine is blocked on the `Thread.sleep`.
     * - However, the `coroutineScope` will only return (from suspension) when the second coroutine is
     * completed (i.e. after the 2000 ms elapse)
     */

    // Adds runInterruptible
    @Test
    fun `cancelling coroutines using runInterruptible`() = runBlocking(Dispatchers.IO) {
        try {
            coroutineScope {
                val c1 = launch {
                    logger.trace("Before sleep of first coroutine")
                    Thread.sleep(1000)
                    logger.trace("After sleep of first coroutine")
                }
                val c2 = launch {
                    runInterruptible {
                        logger.trace("Before sleep of second coroutine")
                        Thread.sleep(2000)
                        logger.trace("After sleep of second coroutine")
                    }
                }
                delay(1500)
                logger.trace("cancelling scope")
                cancel()
                logger.trace(
                    "c1.isCancelled={}, c1.isCompleted={}, c2.isCancelled={}, c2.isCompleted={}",
                    c1.isCancelled,
                    c1.isCompleted,
                    c2.isCancelled,
                    c2.isCompleted,
                )
            }
        } catch (e: CancellationException) {
            logger.trace("Handling cancellation exception")
        }
        logger.trace("After coroutineScope")
    }
    /*
     * Takeaways:
     * - Now the test does take ~1500 ms because the `Thread.sleep` is interrupted when
     *   the coroutine where it is executing is cancelled. Note that this does work because
     *   `Thread.sleep` is cancellable via interruptions by throwing an `InterruptedException`.
     */

    @Test
    fun `child cancellation - a child throws Exception`() = runBlocking(CoroutineExceptionHandler(::exceptionHandler)) {
        var job0: Job? = null
        var job1: Job? = null
        var job2: Job? = null

        supervisorScope {
            job0 = launch {
                job1 = launch {
                    delay(10)
                    throw Exception("Bum!!")
                }

                job2 = launch {
                    delay(20)
                }
            }
        }

        assertEquals(JobState.CANCELLED, job0?.getState())
        assertEquals(JobState.CANCELLED, job1?.getState())
        assertEquals(JobState.CANCELLED, job2?.getState())
    }
    /*
     * Takeaways:
     * - an exception (different from CancellationException) on child1 cancel both the parent and the sibling
     */

    @Test
    fun `child cancellation - a child throws CancellationException`() =
        runBlocking(CoroutineExceptionHandler(::exceptionHandler)) {
            var job0: Job? = null
            var job1: Job? = null
            var job2: Job? = null

            supervisorScope {
                job0 = launch {
                    job1 = launch {
                        delay(10)
                        throw CancellationException("Giving up")
                    }

                    job2 = launch {
                        delay(20)
                    }
                }
            }

            assertEquals(JobState.COMPLETED, job0?.getState())
            assertEquals(JobState.CANCELLED, job1?.getState())
            assertEquals(JobState.COMPLETED, job2?.getState())
        }
    /*
     * Takeaways:
     * - a CancellationException on child1 does NOT cancel the parent or the sibling
     */

    @Test
    fun `child cancellation using async - a child throws CancellationException`() =
        runBlocking(CoroutineExceptionHandler(::exceptionHandler)) {
            var job0: Job? = null
            var job1: Deferred<Unit>? = null
            var job2: Deferred<Int>? = null

            supervisorScope {
                job0 = launch {
                    job1 = async {
                        delay(10)
                        throw CancellationException("Giving up")
                    }

                    job2 = async {
                        delay(20)
                        42
                    }
                }
            }

            assertEquals(JobState.COMPLETED, job0?.getState())
            assertEquals(JobState.CANCELLED, job1?.getState())
            assertEquals(JobState.COMPLETED, job2?.getState())
            assertEquals(42, job2?.await())
        }
    /*
     * Takeaways:
     * - a CancellationException on child1 does NOT cancel the parent or the sibling,
     * even when using async. Namely, the value from the second child is still available.
     */

    @Test
    fun `using supervisorScope`() = runBlocking {
        val job0 = launch(CoroutineExceptionHandler(::exceptionHandler)) {
            supervisorScope {
                launch {
                    delay(10)
                    throw Exception("Bum!!")
                }

                launch {
                    delay(20)
                    logger.info("Child 2 completed normally")
                }
            }
        }

        job0.join()
        Assert.assertEquals(JobState.COMPLETED, job0.getState())
    }
    /*
     * Takeaways:
     * - Using supervisorScope prevents the parent from being cancelled even if one of the childs is cancelled
     */

    private fun exceptionHandler(context: CoroutineContext, ex: Throwable) {
        logger.info("Exception handler: Exception '{}' on '{}'", ex, context)
    }
}
