package org.pedrofelix.course.coroutines

import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class SuspendCoroutineAndCancellationTests {

    companion object {
        private val logger = LoggerFactory.getLogger(SuspendCoroutineAndCancellationTests::class.java)
        private val scheduler = Executors.newSingleThreadScheduledExecutor()
    }

    @Test
    fun `suspendCoroutine does not handle coroutine cancellation`() {

        suspend fun mydelay(ms: Long) = suspendCoroutine<Unit> { continuation ->
            scheduler.schedule({
                logger.info("Scheduler callback called.")
                continuation.resume(Unit)
            }, ms, TimeUnit.MILLISECONDS)
        }

        runBlocking {
            val job = launch {
                try {
                    mydelay(1000)
                } catch (ex: Exception) {
                    logger.info("Exception caught '{}' - '{}'.", ex.javaClass.name, ex.message)
                }
            }
            delay(500)
            job.cancel()
        }
        logger.info("After runBlocking.")
    }
    /*
     * Takeaways:
     * - suspendCoroutine only resumes when the continuation is called, even if the coroutine is cancelled before that.
     * - The coroutine resumes normally after the continuation is called, even if in the cancelled state.
     */

    @Test
    fun `suspendCancellableCoroutine does handle coroutine cancellation`() {

        suspend fun mydelay(ms: Long) = suspendCancellableCoroutine<Unit> { continuation ->
            scheduler.schedule({
                logger.info("Scheduler callback called.")
                continuation.resume(Unit)
            }, ms, TimeUnit.MILLISECONDS)
        }

        runBlocking {
            val job = launch {
                try {
                    mydelay(1000)
                } catch (ex: Exception) {
                    logger.info("Exception caught '{}' - '{}'.", ex.javaClass.name, ex.message)
                }
            }
            delay(500)
            job.cancel()
        }
        logger.info("After runBlocking.")

        logger.info("Waiting a bit more to see if anything else happens")
        Thread.sleep(2000)
    }
    /*
     * Takeaways:
     * - suspendCancellableCoroutine resumes immediately when the coroutine is cancelled, with a `JobCancellationException`.
     * - Note how the scheduled callback still runs after the given time, even if the coroutine already ended.
     */

    @Test
    fun `suspendCancellableCoroutine and callback cancellation`() {

        suspend fun mydelay(ms: Long) = suspendCancellableCoroutine<Unit> { continuation ->
            val future = scheduler.schedule({
                logger.info("Scheduler callback called.")
                continuation.resume(Unit)
            }, ms, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation {
                future.cancel(false)
            }
        }

        runBlocking {
            val job = launch {
                try {
                    mydelay(1000)
                } catch (ex: Exception) {
                    logger.info("Exception caught '{}' - '{}'.", ex.javaClass.name, ex.message)
                }
            }
            delay(500)
            job.cancel()
        }
        logger.info("After runBlocking.")

        logger.info("Waiting a bit more to see if anything else happens")
        Thread.sleep(2000)
    }
    /*
     * Takeaways:
     * - By using continuation.invokeOnCancellation we can cancel the scheduled callback.
     *   However, this is not fully guaranteed, since the `future.cancel` can happen in concurrency with
     *   the coroutine cancellation.
     */

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `suspendCancellableCoroutine and resource management`() {

        fun produceSomething(handler: Consumer<Long>) {
            scheduler.schedule({ handler.accept(42) }, 1000, TimeUnit.MILLISECONDS)
        }

        suspend fun getInt() = suspendCancellableCoroutine<Long> { continuation ->
            produceSomething { value ->
                continuation.resume(value) {
                    logger.info("Disposing {}", value)
                }
            }
        }

        runBlocking {
            val job = launch {
                try {
                    val i = getInt()
                    logger.info("Got {}", i)
                } catch (ex: Exception) {
                    logger.info("Exception caught '{}' - '{}'.", ex.javaClass.name, ex.message)
                }
            }
            delay(500)
            job.cancel()
        }
        logger.info("After runBlocking.")

        logger.info("Waiting a bit more to see if anything else happens")
        Thread.sleep(2000)
    }

    @OptIn(InternalCoroutinesApi::class)
    @Test
    fun `suspendCoroutine and coroutine cancellation`() {

        suspend fun mydelay(ms: Long) = suspendCoroutine<Unit> { continuation ->
            val resumed = AtomicBoolean(false)
            val disposableHandle = continuation.context[Job]
                ?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
                if(resumed.getAndSet(true) == false){
                    continuation.resumeWithException(CancellationException("Cancelled"))
                }
            }
            scheduler.schedule({
                logger.info("Scheduler callback called.")
                disposableHandle?.dispose()
                if(resumed.getAndSet(true) == false) {
                    continuation.resume(Unit)
                }
                continuation.resume(Unit)
            }, ms, TimeUnit.MILLISECONDS)

        }

        runBlocking {
            val job = launch {
                try {
                    mydelay(1000)
                } catch (ex: Exception) {
                    logger.info("Exception caught '{}' - '{}'.", ex.javaClass.name, ex.message)
                }
            }
            delay(500)
            job.cancel()
        }
        logger.info("After runBlocking.")
    }
    /*
     * Takeaways:
     * - suspendCoroutine only resumes when the continuation is called, even if the coroutine is cancelled before that.
     * - The coroutine resumes normally after the continuation is called, even if in the cancelled state.
     */
}