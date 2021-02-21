// This file was automatically generated from threads-and-coroutines.md by Knit tool. Do not edit.
package org.pedrofelix.course.coroutines.generated.exampleThreadsAndCoroutines01

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

fun main() {
    log.info("starting")
    sleepForABit(Duration.ofMillis(500))
    sleepForABit(Duration.ofMillis(1000))
    log.info("ending")
}
