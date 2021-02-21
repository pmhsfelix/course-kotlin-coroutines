<!--- INCLUDE .*
import kotlinx.coroutines.*
import org.junit.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import org.pedrofelix.course.coroutines.*

private val log = LoggerFactory.getLogger("examples")

-->

# Threads and coroutines

## Regular functions and threads

Let's start with a simple _regular_ function that just waits for some time to elapse, using a regular Thread.sleep`.

```kotlin
fun sleepForABit(duration: Duration) {
    log.info("before sleep")
    Thread.sleep(duration.toMillis())
    log.info("after sleep")
}
```

We are using `Thread.sleep` to illustrate an operation take can take a significant time and that does not require
CPU usage during it full execution.
Other examples could be waiting for some bytes to arrive on a socket (i.e. I/O operation), or waiting for some other
thread or program to perform something (i.e. thread coordination).

<!--- INCLUDE .*-->

We can call `sleepForABit` from another function

```kotlin
fun main() {
    log.info("starting")
    sleepForABit(Duration.ofMillis(500))
    sleepForABit(Duration.ofMillis(1000))
    log.info("ending")
}
```

The resulting output will be something like this.

```
5 [main] INFO examples - starting
10 [main] INFO examples - before sleep
515 [main] INFO examples - after sleep
516 [main] INFO examples - before sleep
1518 [main] INFO examples - after sleep
1518 [main] INFO examples - ending
```

These log messages show that the `sleepForABit` function runs twice,
always in the `main` thread and in a serial way:

- First, the `sleepForABit` is called in the `main` thread, with a 500 ms duration argument.
As a result, the `main` thread will be put in a non-ready state for approximately 500s.

- After this time elapses, the thread resumes execution, and the function `sleepForABit` returns to the `main`
function.

- Then, `sleepForABit` is called again, this time with a 1000 ms duration argument.

- After these 1000 ms elapse, the `sleepForABit` function returns and the program ends,
after issuing some more log messages.

<!--- KNIT example-threads-and-coroutines-01.kt -->

Instead of synchronously and serially calling the `sleepForABit` function, we can create two threads
and call the `sleepForABit` on these threads.


```kotlin
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
```

The resulting log messages are:

```
6 [main] INFO examples - starting
8 [main] INFO examples - waiting for the threads to end
11 [Thread-0] INFO examples - before sleep
11 [Thread-1] INFO examples - before sleep
513 [Thread-0] INFO examples - after sleep
1015 [Thread-1] INFO examples - after sleep
1015 [main] INFO examples - ending
```

In this case, the log messages clearly show the functions being run in the two created threads:
`Thread-0` and `Thread-1`.  
Also, the execution is parallel and not serial, as can be seen in the total time that the program takes to run -  
1000 ms, i.e, the greatest of the two sleeping durations.

It is important to emphasize that the created threads will be occupied while the `Thread.sleep` are in execution,
even if no CPU is assigned to any of those threads.

<!--- KNIT example-threads-and-coroutines-02.kt -->

## `suspend` functions and coroutines

Let's now consider an equivalent example where:

- The "sleeping" function, now called `delayForABit` is a `suspend` function.
- Instead of `Thread.sleep` we call `delay`, which is also a `suspend` function present by `kotlinx.coroutines``

```kotlin
suspend fun delayForABit(duration: Duration) {
    log.info("before sleep")
    delay(duration.toMillis())
    log.info("after sleep")
}
```

In addition, instead of using the `delayForABit` function to create two threads,  
we use them to create two _coroutines_.
Notice how this `main` function is similar to the previous one:

- Instead of calling the `Thread` constructor to create the threads,
we use the `launch` coroutine builder to create and start the coroutines.

- We still use a `join` function to wait for both coroutines to end.


```kotlin
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
```

Running this new `main` function produces something like

```
74 [main] INFO examples - starting
84 [main] INFO examples - waiting for the coroutines to end
93 [main] INFO examples - before sleep
96 [main] INFO examples - before sleep
597 [main] INFO examples - after sleep
1099 [main] INFO examples - after sleep
1099 [main] INFO examples - ending
```

<!--- KNIT example-threads-and-coroutines-03.kt -->


