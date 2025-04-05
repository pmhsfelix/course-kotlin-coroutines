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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
     *   This means that the await throws in the `Cancelling` state.
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
                    c1.isCancelled, c1.isCompleted, c2.isCancelled, c2.isCompleted
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
                    c1.isCancelled, c1.isCompleted, c2.isCancelled, c2.isCompleted
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
        assertEquals(JobState.COMPLETED, job0.getState())
    }
    /*
     * Takeaways:
     * - Using supervisorScope prevents the parent from being cancelled even if one of the childs is cancelled
     */

    @Test
    fun `using suspendCoroutine`() {
        runBlocking {
            logger.info("Start")
            val executor = Executors.newSingleThreadScheduledExecutor()
            suspend fun mydelay(ms: Long) = suspendCoroutine<Unit> { continuation ->
                executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
            }

            val job = launch {
                mydelay(1000)
                logger.info("After mydelay")
            }
            delay(100)
            job.cancel()
            logger.info("job isCancelled={}", job.isCancelled)
        }
        logger.info("runBlocking ended")
    }
    /*
     * Takeaways:
     * - suspendCoroutine does NOT handle cancellations
     *   - The log message after the mydelay call still happens.
     */

    @Test
    fun `using suspendCancellableCoroutine`() {
        runBlocking {
            logger.info("Start")
            val executor = Executors.newSingleThreadScheduledExecutor()
            suspend fun mydelay(ms: Long) = suspendCancellableCoroutine<Unit> { continuation ->
                executor.schedule({
                    logger.info("schedule callback called, continuing coroutine")
                    continuation.resume(Unit)
                }, ms, TimeUnit.MILLISECONDS)
            }

            val job = launch {
                mydelay(1000)
                logger.info("After mydelay")
            }
            delay(100)
            job.cancel()
            logger.info("job isCancelled={}", job.isCancelled)
        }
        logger.info("runBlocking ended")
        // Let's wait just a bit more to see if anything happens
        Thread.sleep(1000)
    }
    /*
     * Takeaways:
     * - suspendCancellableCoroutine DOES handle cancellations
     *   - info message after mydelay is never called
     *   - parent coroutine (the one created by runBlocking) ends after 100ms and not after 1000ms
     * - HOWEVER, the scheduled callback still runs after 1000ms
     *   - We can have a call to continuation.resume AFTER the coroutine already continued.
     *     This second call is allowed by the model and simply ignored.
     */

    @Test
    fun `using suspendCancellableCoroutine and invokeOnCancellation`() {
        runBlocking {
            logger.info("Start")
            val executor = Executors.newSingleThreadScheduledExecutor()
            suspend fun mydelay(ms: Long) = suspendCancellableCoroutine<Unit> { continuation ->
                val future = executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
                continuation.invokeOnCancellation {
                    // This cancellation may be done too late, i.e. the scheduled callback can still run.
                    // This means we have a race between cancellation and normal completion, where BOTH can happen
                    future.cancel(true)
                }
            }

            val job = launch {
                mydelay(1000)
                logger.info("After mydelay")
            }
            delay(100)
            job.cancel()
            logger.info("job isCancelled={}", job.isCancelled)
        }
        logger.info("runBlocking ended")
    }
    /*
     * Takeaways:
     * - the `continuation.invokeOnCancellation` gives us an opportunity to try to cancel the ongoing operation
     *   and avoid the continuation.resume after cancellation.
     * - The race between cancellation and success completion can still occur.
     */

    @Test
    fun `using withTimeout`() {
        runBlocking {
            logger.info("Start")
            val executor = Executors.newSingleThreadScheduledExecutor()
            suspend fun mydelay(ms: Long) = suspendCancellableCoroutine<Unit> { continuation ->
                val future = executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
                continuation.invokeOnCancellation {
                    // This cancellation may be done too late, i.e. the scheduled callback can still run.
                    // This means we have a race between cancellation and normal completion, where BOTH can happen
                    future.cancel(true)
                }
            }

            val job = launch {
                withTimeout(100) {
                    mydelay(1000)
                    logger.info("After mydelay")
                }
            }
            job.join()
            logger.info("job isCancelled={}", job.isCancelled)
        }
        logger.info("runBlocking ended")
        // Let's wait just a bit more to see if anything happens
        Thread.sleep(2000)
    }
    /*
     * Takeaways:
     * - The `withTimout` cancels the coroutine if the block doesn't end in the defined time
     */

    @Test
    fun `using withTimeout and uncancellable delay`() {
        runBlocking {
            logger.info("Start")
            val executor = Executors.newSingleThreadScheduledExecutor()
            suspend fun mydelay(ms: Long) = suspendCoroutine<Unit> { continuation ->
                executor.schedule({ continuation.resume(Unit) }, ms, TimeUnit.MILLISECONDS)
            }

            val job = launch {
                try {
                    withTimeout(100) {
                        mydelay(1000)
                        logger.info("After mydelay")
                    }
                    logger.info("After withTimeout")
                } catch (ex: Exception) {
                    logger.info("Got exception {}", ex.message)
                }
            }
            delay(300)
            logger.info("job isCancelled={}", job.isCancelled)
        }
        logger.info("runBlocking ended")
        // Let's wait just a bit more to see if anything happens
        Thread.sleep(2000)
    }
    /*
     * Takeaways:
     * - The log message after mydelay is still issued (mydelay is not cancellable)
     * - HOWEVER, the log message after withTimeout is NOT issued because withTimout throws exception
     *   ONLY after the internal block ends
     */

    private fun exceptionHandler(context: CoroutineContext, ex: Throwable) {
        logger.info("Exception handler: Exception '{}' on '{}'", ex, context)
    }

}