// This file was automatically generated from threads-and-coroutines.md by Knit tool. Do not edit.
package org.pedrofelix.course.coroutines.generated.exampleThreadsAndCoroutines03

import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import org.pedrofelix.course.coroutines.*

private val log = LoggerFactory.getLogger("examples")

fun sleepForABit(duration: Duration) {
    log.info("before sleep")
    Thread.sleep(duration.toMillis())
    log.info("after sleep")
}

suspend fun delayForABit(duration: Duration) {
    log.info("before sleep")
    delay(duration.toMillis())
    log.info("after sleep")
}

fun main() = runBlocking {
    log.info("starting")
    val coroutine0 = launch {
        delayForABit(Duration.ofMillis(500))
    }
    val coroutine1 = launch {
        delayForABit(Duration.ofMillis(1000))
    }
    log.info("waiting for the coroutines to end")
    join(coroutine0, coroutine1)
    log.info("ending")
}
