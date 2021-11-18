package org.pedrofelix.course.coroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ExceptionTests::class.java)

class ExceptionTests {

    @Test
    fun `uncaught exception`(): Unit = runBlocking {

        val job = GlobalScope.launch {
            throw RuntimeException("exception from launch")
        }
        try {
            job.join()
        } catch (e: Exception) {
            logger.info("Exception caught on launch: {}", e.message)
        }

        val deferred = GlobalScope.async {
            throw RuntimeException("exception from async")
        }
        try {
            deferred.await()
        } catch (e: Exception) {
            logger.info("Exception caught on async: {}", e.message)
        }

    }

}