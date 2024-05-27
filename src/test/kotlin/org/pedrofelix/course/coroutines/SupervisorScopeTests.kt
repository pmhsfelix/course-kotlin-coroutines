package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test

class SupervisorScopeTests {

    @Test
    fun first() {
        val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
            logger.info("CoroutineExceptionHandler: {}, {}", context, throwable.message)
        }
        val scope = CoroutineScope(SupervisorJob() + exceptionHandler)
        launchChild(scope)
        Thread.sleep(2000)
        val latch = CountDownLatch(1)
        scope.coroutineContext[Job]?.invokeOnCompletion {
            latch.countDown()
        }
        logger.info("Cancelling scope")
        scope.cancel()
        latch.await()
        logger.info("Ending test")
    }

    private fun launchChild(scope: CoroutineScope) {
        val child = scope.launch {
            failAfterDelay()
        }
        child.invokeOnCompletion {
            if (it != null && it !is CancellationException) {
                logger.info("Child coroutine ended with {}, restarting it", it.message)
                // FIXME stack overflow
                launchChild(scope)
            }
            if (it is CancellationException) {
                logger.info("Child coroutine ended with {}, not restarting it", it.message)
            }
        }
    }

    private suspend fun failAfterDelay() {
        logger.info("child starting")
        delay(100)
        logger.info("child about to fail")
        throw Exception("Fail!")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SupervisorScopeTests::class.java)
    }
}
