package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val logger = LoggerFactory.getLogger(ExceptionTests::class.java)

@OptIn(DelicateCoroutinesApi::class)
class ExceptionTests {
    @Test
    fun `synchronizing with coroutines that end with exceptions`(): Unit =
        runBlocking {
            // Creating and synchronizing with a coroutine that ends with an exception,
            // using launch and join
            val job =
                GlobalScope.launch {
                    delay(100)
                    throw RuntimeException("exception from launch")
                }
            try {
                job.join()
            } catch (e: Exception) {
                logger.info("Exception caught on launch: {}", e.message)
                fail("join on a coroutine that was created via join doesn't rethrow the exception")
            }
            // Takeaway: the exception is NOT observed by the join

            // Creating and synchronizing with a coroutine that ends with an exception,
            // using async and await
            val deferred =
                GlobalScope.async {
                    delay(100)
                    throw RuntimeException("exception from async")
                }
            val enteredTheCatch: Boolean
            try {
                deferred.await()
            } catch (e: Exception) {
                enteredTheCatch = true
                logger.info("Exception caught on async: {}", e.message)
            }
            assertTrue(enteredTheCatch, "Exception is thrown when synchronizing via await")
            // Takeaway: the exception IS observed by the await
        }

    @Test
    fun `handling uncaught exceptions`(): Unit =
        runBlocking {
            val handledCounter = AtomicInteger(0)
            val uncaughtHandler =
                CoroutineExceptionHandler { _, _ ->
                    handledCounter.incrementAndGet()
                }

            // Creating and synchronizing with a coroutine that ends with an exception,
            // using launch and join
            val job =
                GlobalScope.launch(uncaughtHandler) {
                    delay(100)
                    throw RuntimeException("exception from launch")
                }
            job.join()

            // Creating and synchronizing with a coroutine that ends with an exception,
            // using async and await
            val deferred =
                GlobalScope.async(uncaughtHandler) {
                    delay(100)
                    throw RuntimeException("exception from async")
                }
            try {
                deferred.await()
            } catch (e: Exception) {
                logger.info("Exception caught on async: {}", e.message)
            }

            assertEquals(
                1,
                handledCounter.get(),
                "The uncaughtHandler was called only once",
            )
        }
    // Takeaway: unobserved exceptions are handled the context's CoroutineExceptionHandler

    @Test
    fun `exceptions can be caught`(): Unit =
        runBlocking {
            val handledCounter = AtomicInteger(0)
            val uncaughtHandler =
                CoroutineExceptionHandler { _, _ ->
                    handledCounter.incrementAndGet()
                }
            val job =
                GlobalScope.launch(uncaughtHandler) {
                    try {
                        delay(100)
                        throw RuntimeException("exception from launch")
                    } catch (e: Exception) {
                        // ending without an exception
                    }
                }
            job.join()

            assertEquals(0, handledCounter.get(), "The uncaughtHandler was never called")
        }

    @Test
    fun `the uncaughtHandler is used at the top level only`() =
        runBlocking {
            val outerCounter = AtomicInteger(0)
            val outerHandler =
                CoroutineExceptionHandler { _, _ ->
                    outerCounter.incrementAndGet()
                }
            val innerCounter = AtomicInteger(0)
            val innerHandler =
                CoroutineExceptionHandler { _, _ ->
                    innerCounter.incrementAndGet()
                }
            val job =
                GlobalScope.launch(outerHandler) {
                    launch(innerHandler) {
                        throw Exception("Exception thrown in the inner coroutine")
                    }
                    delay(100)
                }
            job.join()
            // Takeaways:
            // - The exception is NOT observed by the join and therefore the exception handler IS called.
            // - Howevet, the exception handler is only called at the top level.

            assertEquals(1, outerCounter.get())
            assertEquals(0, innerCounter.get())
        }

    @Test
    fun `Uncaught exception handler is not used when the exception is observed`() =
        runBlocking {
            val outerCounter = AtomicInteger(0)
            val outerHandler =
                CoroutineExceptionHandler { _, _ ->
                    outerCounter.incrementAndGet()
                }
            val innerCounter = AtomicInteger(0)
            val innerHandler =
                CoroutineExceptionHandler { _, _ ->
                    innerCounter.incrementAndGet()
                }
            val deferred =
                GlobalScope.async(outerHandler) {
                    launch(innerHandler) {
                        throw Exception("Exception thrown in the inner coroutine")
                    }
                    delay(100)
                }
            try {
                deferred.await()
                fail("exception is thrown by the await")
            } catch (e: Exception) {
                // do nothing here
            }
            // Takeaway: the exception is observed by the await and therefore the exception handler is not called

            assertEquals(0, outerCounter.get())
            assertEquals(0, innerCounter.get())
        }
}
