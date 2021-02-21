// This file was automatically generated from threads-and-coroutines.md by Knit tool. Do not edit.
package org.pedrofelix.course.coroutines.generated.exampleThreadsAndCoroutines02

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
    val thread0 = Thread {
        sleepForABit(Duration.ofMillis(500))
    }.apply { start() }
    val thread1 = Thread {
        sleepForABit(Duration.ofMillis(1000))
    }.apply { start() }
    log.info("waiting for the threads to end")
    join(thread0, thread1)
    log.info("ending")
}
