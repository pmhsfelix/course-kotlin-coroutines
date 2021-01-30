package org.pedrofelix.course.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

fun startThread(block: Runnable) = Thread(block).apply {
    isDaemon = false
    start()
}

fun join(vararg ths: Thread) {
    for (th in ths) {
        th.join()
    }
}

suspend fun join(vararg jobs: Job) {
    for (th in jobs) {
        th.join()
    }
}

fun joinBlocking(vararg jobs: Job) = runBlocking {
    join(*jobs)
}
