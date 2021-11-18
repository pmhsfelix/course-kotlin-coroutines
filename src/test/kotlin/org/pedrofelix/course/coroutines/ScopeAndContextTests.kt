package org.pedrofelix.course.coroutines

import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger(ScopeAndContextTests::class.java)

class ScopeAndContextTests {

    @Test
    fun first() {
        logger.trace("Starting the test")
        runBlocking {
            logger.trace("Inside the runBlocking")
            launch {
                delay(1000)
                logger.trace("Inside nested coroutine")
            }
            launch {
                delay(1000)
                logger.trace("Inside nested coroutine")
            }
            logger.trace("After launching coroutines")
        }
        logger.trace("After runBlocking")
    }
    /*
     * Takeaways:
     * - All three coroutines run in the `main` thread.
     * - The `trace` after the `launch`s doesn't have any delay, i.e., doesn't wait for the coroutines to end.
     * - The `trace` after the `runBlocking` waits for all coroutines to end.
     */

    @Test
    fun `using coroutineScope`() {
        logger.trace("Starting the test")
        runBlocking {
            logger.trace("Inside the runBlocking")
            coroutineScope {
                launch {
                    delay(1000)
                    logger.trace("Inside nested coroutine")
                }
                launch {
                    delay(1000)
                    logger.trace("Inside nested coroutine")
                }
                logger.trace("After launching coroutines")
            }
            logger.trace("After the coroutineScope")
        }
        logger.trace("After runBlocking")
    }
    /*
     * Takeaways:
     * - All three coroutines still run in the `main` thread.
     * - The `coroutineScope` doesn't create a new coroutine.
     * - The `trace` after the `launch`s doesn't have any delay, i.e., doesn't wait for the coroutines to end.
     * - However the `trace` after the `coroutineScope` waits for all coroutines in that inner scope to end.
     */

    @Test
    fun `using withContext`() {
        logger.trace("Starting the test")
        runBlocking {
            logger.trace("Inside the runBlocking")
            withContext(coroutineContext) {
                launch {
                    delay(1000)
                    logger.trace("Inside nested coroutine")
                }
                launch {
                    delay(1000)
                    logger.trace("Inside nested coroutine")
                }
                logger.trace("After launching coroutines")
            }
            logger.trace("After the withContext")
        }
        logger.trace("After runBlocking")
    }
    /*
     * Takeaways:
     * - All three coroutines still run in the `main` thread.
     * - The `coroutineScope` doesn't create a new coroutine.
     * - The `trace` after the `launch`s doesn't have any delay, i.e., doesn't wait for the coroutines to end.
     * - However the `trace` after the `withContext` waits for all coroutines in that inner scope to end.
     *   I.e. Here `withContext` behaves similarly to `coroutineScope`. However `withContext` allows for changing
     *   the context, namely the dispatcher.
     */

    @Test
    fun `using withContext and a dispatcher`() {
        logger.trace("Starting the test")
        runBlocking {
            logger.trace("Inside the runBlocking")
            launch {
                delay(1000)
                logger.trace("Inside first nested coroutine")
            }
            withContext(Dispatchers.IO) {
                launch {
                    delay(1000)
                    logger.trace("Inside second nested coroutine")
                }
            }

            logger.trace("After the coroutineScope")
        }
        logger.trace("After runBlocking")
    }
    /*
     * Takeaways:
     * - The second nested coroutine now does not run on the `main` thread. Instead, it runs on threads of the
     *   scheduler provided as argument of `withContext`.
     * - There are still only three coroutines, even with the use of `withContext`
     */

    @Test
    fun `changing the dispatcher on a coroutine`() = runBlocking {
        logger.trace("Inside the coroutine, before the withContext")
        withContext(Dispatchers.IO) {
            logger.trace("Inside the withContext")
        }
        logger.trace("Inside the coroutine, after the withContext")
    }

    @Test
    fun `using a explicit scope`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch {
            logger.trace("Inside first nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside first nested coroutine, after delay")
        }
        scope.launch {
            logger.trace("Inside second nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside second nested coroutine, after delay")
        }
        logger.trace("Ending test")
    }
    /*
     * Takeaways:
     * - The traces before the `delay` are executed in the `main` thread, due to the `Dispatchers.Unconfined` usage.
     * - However, the traces after the `delay` are not executed, because the test ends before that.
     * - The test ends before all coroutines are completed.
     */

    @Test
    fun `using a explicit scope with a spin-wait`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val c1 = scope.launch {
            logger.trace("Inside first nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside first nested coroutine, after delay")
        }
        val c2 = scope.launch {
            logger.trace("Inside second nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside second nested coroutine, after delay")
        }

        // Wait until the coroutines are completed
        // spin-wait only for demo purposes, do not use this in production
        while (!c1.isCompleted || !c2.isCompleted) {
            Thread.yield()
        }

        logger.trace("Ending test")
    }
    /*
     * Takeaways:
     * - Now the traces after the `delay` do run, because the test method waits for all coroutines to complete.
     * - Notice how those traces after the `delay` run on a `DefaultExecutor` thread
     * (probably the one associated to the scheduled executor used), due to the use of `Dispatchers.Unconfined`
     */

    @Test
    fun `using a explicit scope with a spin-wait and Dispatchers Default`() {
        val scope = CoroutineScope(Dispatchers.Default)
        val c1 = scope.launch {
            logger.trace("Inside first nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside first nested coroutine, after delay")
        }
        val c2 = scope.launch {
            logger.trace("Inside second nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside second nested coroutine, after delay")
        }
        while (!c1.isCompleted || !c2.isCompleted) {
            Thread.yield();
        }

        logger.trace("Ending test")
    }
    /*
     * Takeaways:
     * - Now the traces before and after the delay run on a `DefaultDispatcher` worker thread.
     */

    // Lets remove the spin-wait and replace it with a thread wait
    @Test
    fun `using a explicit scope without a spin-wait`() {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            logger.trace("Inside first nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside first nested coroutine, after delay")
        }
        scope.launch {
            logger.trace("Inside second nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside second nested coroutine, after delay")
        }

        // TODO can we assume a CoroutineScope always have a `Job`?
        val childrenJobs = scope.coroutineContext[Job]!!.children
        val latch = CountDownLatch(childrenJobs.count())
        childrenJobs.forEach {
            it.invokeOnCompletion {
                latch.countDown()
            }
        }
        latch.await()

        logger.trace("Ending test")
    }
    /*
     * Takeaways:
     * - Using the list of children and the `invokeOnCompletion` callback to synchronize
     */

    // Let's do a custom dispatcher that uses the current thread to dispatch,
    // based on a loop.
    class InPlaceDispatcher : CoroutineDispatcher() {

        // a marker that we put in the queue to signal that no more work item will be added
        companion object {
            private val POISON_PILL = Runnable { }
        }

        // the queue with the work items to execute.
        private val queue = LinkedBlockingQueue<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            // TODO not protected against a dispatch after a shutdown
            queue.offer(block)
        }

        fun shutdown() {
            queue.offer(POISON_PILL)
        }

        fun pump() {
            while (true) {
                val runnable: Runnable? = queue.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
                if (runnable == null || runnable === POISON_PILL) {
                    break
                }
                runnable.run()
            }
        }
    }

    @Test
    fun `using a explicit scope without a spin-wait and running everything on main`() {
        val dispatcher = InPlaceDispatcher()
        // a dispatcher is a context element, which is also an element
        val scope = CoroutineScope(dispatcher)
        scope.launch {
            logger.trace("Inside first nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside first nested coroutine, after delay")
        }
        scope.launch {
            logger.trace("Inside second nested coroutine, before delay")
            delay(1000)
            logger.trace("Inside second nested coroutine, after delay")
        }

        // TODO can we assume a CoroutineScope always have a `Job`?
        val childrenJobs = scope.coroutineContext[Job]!!.children
        val counter = AtomicInteger(childrenJobs.count())
        childrenJobs.forEach {
            it.invokeOnCompletion {
                if (counter.decrementAndGet() == 0) {
                    dispatcher.shutdown()
                }
            }
        }
        dispatcher.pump()

        logger.trace("Ending test")
    }
    /*
     * Takeaways:
     * - Creating a custom dispatcher that uses the current thread to execute the work items.
     */

}
