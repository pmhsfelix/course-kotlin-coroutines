package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger(JobTests::class.java)

class JobTests {

    @Test
    fun `job tree`() = runBlocking {
        // Some suspendable countdown latches to synchronize the test
        val syncWithChildCoroutineStart = KCountDownLatch(2)
        val syncWithParentAllowingTermination = KCountDownLatch(1)

        // vars to hold the child jobs
        var job1: Job? = null
        var job2: Job? = null

        var firstCoroutineContext: CoroutineContext? = null

        // launch parent coroutine
        val job0 = launch(start = CoroutineStart.LAZY) {
            firstCoroutineContext = coroutineContext

            // wait a bit before starting child coroutines
            delay(20)

            // launch child coroutines and capture their jobs
            job1 = launch {
                syncWithChildCoroutineStart.countDown()
                syncWithParentAllowingTermination.await()
                delay(20)
                logger.info("Ending child j1")
            }

            job2 = launch {
                syncWithChildCoroutineStart.countDown()
                syncWithParentAllowingTermination.await()
                delay(20)
                logger.info("Ending child j2")
            }
        }

        // launch a coroutine to periodically show the job states, including the children
        launch { monitorJob(job0) }

        delay(20)
        job0.start()

        syncWithChildCoroutineStart.await()
        assertEquals(
            "the job that represents the first launched coroutine has two child jobs",
            2,
            job0.children.count(),
        )
        assertTrue("job0 contains job1 as a child", job0.children.contains(job1))
        assertTrue("job0 contains job2 as a child", job0.children.contains(job2))

        assertEquals("coroutineContext contains the job returned by launch", job0, firstCoroutineContext?.get(Job))
        syncWithParentAllowingTermination.countDown()

        job0.join()
        assertEquals("the parent job doesn't have any child job after completion", 0, job0.children.count())
    }
    /*
     * Takeaways:
     * - Creating a coroutine creates a Job, which is returned by the coroutine builder and also available in the
     * coroutineContext
     * - As new children coroutines are created, their jobs are added as childs of the parent coroutine
     * (i.e. the coroutine providing the context from which the CoroutineScope was created).
     * - Children coroutines are removed from the parent hen they complete.
     */

    @Test
    fun `using the Job from a scope`() = runBlocking {
        val scope = CoroutineScope(Job())
        // because the job returned by Job() implements CompletableJob()
        val job0 = scope.coroutineContext[Job] as CompletableJob

        val job1 = scope.launch {
            delay(10)
        }

        val job2 = scope.launch {
            delay(20)
        }

        // without this complete, the join would never complete
        // (the job doesn't complete even if the children complete)
        job0.complete()
        assertEquals(JobState.ACTIVE_OR_COMPLETING, job1.getState())
        assertEquals(JobState.ACTIVE_OR_COMPLETING, job2.getState())

        job0.join()
        assertEquals(JobState.COMPLETED, job1.getState())
        assertEquals(JobState.COMPLETED, job2.getState())
    }
    /*
     * Takeaways:
     * - Using an explicit CoroutineScope, created with an explicit Job to launch child coroutines
     * - Explicitly terminating the parent job and synchronizing with its termination
     */

    private suspend fun monitorJob(job: Job) {
        do {
            delay(10)
            show(job)
        } while (!job.isCompleted)
    }

    private fun show(job: Job, indent: Int = 0) {
        logger.info("{}state:{}", spacesFor(indent), job.getState())
        job.children.forEach {
            show(it, indent + 2)
        }
    }

    private fun spacesFor(indent: Int) = " ".repeat(indent)
}
