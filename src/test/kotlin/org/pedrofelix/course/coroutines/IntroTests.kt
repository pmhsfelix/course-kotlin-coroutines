package org.pedrofelix.course.coroutines

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger(IntroTests::class.java)

class IntroTests {

    @Test
    fun `coroutines are suspendable sequential computations`() = runBlocking {
        val startTime = System.currentTimeMillis()
        // launch (create and start) a coroutine
        val coroutine0 = launch {
            repeat(5) {
                log.trace("on repetition {} of first coroutine", it)
                delay(200)
            }
        }

        // launch (create and start) another coroutine
        val coroutine1 = launch {
            repeat(5) {
                log.trace("on repetition {} of second coroutine", it)
                delay(200)
            }
        }

        coroutine0.join()
        coroutine1.join()
        val endTime = System.currentTimeMillis()
        assertTrue(
            "The total time to run is less than the sum of the time for each coroutine",
            endTime - startTime < 2 * 5 * 200,
        )
    }
    /*  Takeaways
        - The trace messages show that the two coroutines run concurrently.
          - I.e. the second starts before the first ends.
        - Note how coroutines are created similarly to threads, i.e., by passing a lambda to a function
            - However in the coroutine case the functions have the special `suspend` modifier.
        - Note also how the main computation synchronizes with the completion of the coroutines using `join`,
          which is similar to what is done with threads.
        - Finally, observe, via the trace messages, how all the coroutines in this example run in the *same* thread.
          (this doesn't have to be this way always).
     */

    @Test
    fun `multiple coroutines can run in the same thread`() = runBlocking {
        val mainThread = Thread.currentThread()

        val coroutine0 = launch {
            repeat(5) {
                log.trace("on repetition {} of first coroutine", it)
                assertEquals("The coroutine runs in the main thread", mainThread, Thread.currentThread())
                delay(200)
            }
        }

        val coroutine1 = launch {
            repeat(5) {
                log.trace("on repetition {} of second coroutine", it)
                assertEquals("The coroutine runs in the main thread", mainThread, Thread.currentThread())
                delay(200)
            }
        }

        coroutine0.join()
        coroutine1.join()
    }
    /*  Takeaways
        - Multiple coroutines can run in the same thread.
        - I.e. Creating a new coroutine doesn't create a new thread (JVM thread or OS thread).
          - As a consequence, coroutines have a lower resource cost (e.g. memory).
     */

    /*
        - JVM threads map one-to-one to OS threads.
        - The OS schedules the execution of N threads on M CPUs, where N can be much greater that M.
            - This is possible because threads are time multiplexed on CPUs
              CPU0: |- thread0 -------||- thread2 ---||- thread1 ---||
              CPU1: |- thread1 ---||- thread3 -----------||- thread0 -----||
            - Each CPU runs "segments" of each thread
              - Each thread doesn't need to be run completely in the same CPU.
            - A *context switch* (the '||' in the diagram above) happens when a CPU stops running one thread
              and starts running a different one
              - The context of the previously running thread is saved (e.g. stack-pointer).
              - The context of the next running thread is loaded back.
            - Context-switches happen when:
              - a thread calls an OS function which cannot be immediately completed and that doesn't require
                the CPU to be completed (e.g. read bytes from a socket).
              - a time period elapsed, meaning that it is time for the running thread to give its way to another thread.
                This means context switch is *preemptive*, i.e., can happen any anytime and is out of control of the
                running thread.

        - How is it possible that multiple coroutines run on the same thread? By using a time multiplexing mechanism
        similar to the one described above for threads
            main-thread: |- coroutine0 --|...|- coroutine1 ---|..|- coroutine0 ---|...|- coroutine1 -
            - When coroutine0 reaches the first suspend point, which happens on the `delay` call, the coroutine is
              "suspended" and the same thread is now used to start running coroutine1.
            - Coroutine switch can only happen at suspension points, which are introduced explicitly by the compiler
              when generating the coroutine code. This differs from OS thread's context switch points, which can happen
              everywhere (more precisely on the boundary of any machine instruction).
     */

    @Test
    fun `is this the same as using tasks on a thread pool`() {
        val executor = Executors.newSingleThreadExecutor()
        val startTime = System.currentTimeMillis()

        // submit a task to the executor
        val task0 = executor.submit {
            repeat(5) {
                log.trace("on repetition {} of first coroutine", it)
                Thread.sleep(200)
            }
        }

        // launch (create and start) another coroutine
        val task1 = executor.submit {
            repeat(5) {
                log.trace("on repetition {} of second coroutine", it)
                Thread.sleep(200)
            }
        }

        task0.get()
        task1.get()
        val endTime = System.currentTimeMillis()
        assertTrue(
            "The total time to run is more than the sum of the time for each coroutine",
            endTime - startTime > 2 * 5 * 200,
        )
    }

    @Test
    fun `what happens if the coroutine blocks the thread where it is running`() = runBlocking {
        val mainThread = Thread.currentThread()
        val startTime = System.currentTimeMillis()
        val coroutine0 = launch {
            repeat(5) {
                log.trace("on repetition {} of first coroutine", it)
                assertEquals("The coroutine runs in the main thread", mainThread, Thread.currentThread())
                Thread.sleep(200)
            }
        }

        val coroutine1 = launch {
            repeat(5) {
                log.trace("on repetition {} of second coroutine", it)
                assertEquals("The coroutine runs in the main thread", mainThread, Thread.currentThread())
                Thread.sleep(200)
            }
        }
        coroutine0.join()
        coroutine1.join()

        val endTime = System.currentTimeMillis()
        assertTrue(
            "The total time to run is now greater or equal than the sum of the time for each coroutine",
            endTime - startTime > 2 * 5 * 200,
        )
    }
    /*  Takeaways

        - In this case, we are using a non-suspend `Thread.sleep` function, which blocks the hosting thread instead
         of allowing it to start running a different coroutine. Since there is no suspend point on the coroutine code,
         the second coroutine will only start after the first one ends. This is observable on the fact that the overall
         execution time is the sum of the execution time for each coroutine.
         It is also observable on the trace messages.
     */

    @Test
    fun `It is possible to have more than one thread being used to schedule coroutines`() = runBlocking {
        val mainThread = Thread.currentThread()
        val startTime = System.currentTimeMillis()
        val coroutine0 = launch(Dispatchers.Default) {
            repeat(5) {
                log.trace("on repetition {} of first coroutine", it)
                assertNotEquals("The coroutine does NOT run in the main thread", mainThread, Thread.currentThread())
                Thread.sleep(200)
            }
        }

        val coroutine1 = launch(Dispatchers.Default) {
            repeat(5) {
                log.trace("on repetition {} of second coroutine", it)
                assertNotEquals("The coroutine runs in the main thread", mainThread, Thread.currentThread())
                Thread.sleep(200)
            }
        }
        coroutine0.join()
        coroutine1.join()

        val endTime = System.currentTimeMillis()
        assertTrue(
            "The total time to run is now again smalller than the sum of the time for each coroutine",
            endTime - startTime < 2 * 5 * 200,
        )
    }
    /*  Takeaways

        - This example uses the `Dispatchers.Default` coroutine dispatcher to run the two launched coroutines.
          Since this dispatcher has more than one thread available to use, now both coroutines can again run
          concurrently, even if they block the execution.
        - However now each coroutine will require an exclusive thread for it, meaning that now a coroutine
          "costs" one thread, i.e., it monopolizes one thread during its total execution time.
     */

    @Test
    fun `since coroutines are more lightweight than threads, can we have lots of them`() = runBlocking {
        val nOfCoroutines = 100_000
        val mainThread = Thread.currentThread()

        val counter = AtomicInteger(0)
        val coroutines = (0 until nOfCoroutines).map {
            launch {
                assertTrue("Coroutine running on the main thread", mainThread == Thread.currentThread())
                delay(100)
                counter.incrementAndGet()
            }
        }.toList()

        coroutines.forEach { coroutine -> coroutine.join() }
        assertEquals(nOfCoroutines, counter.get())
        log.trace("yes, we can")
    }
}
