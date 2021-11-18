
# Threads and coroutines

This documents presents Kotlin's `suspend` functions and coroutines by comparing them to plain regular functions and threads.

## Regular functions and threads

Let's start with a simple _regular_ function that just waits for some time to elapse, using a ` Thread.sleep`.

```kotlin
fun sleepForABit(duration: Duration) {
    log.info("before sleep")
    Thread.sleep(duration.toMillis())
    log.info("after sleep")
}
```

In this example we are using `Thread.sleep` as a placeholder for any operation that takes a significant time to complete and that does not require CPU usage during it full execution.
Other examples could be waiting for some bytes to arrive at a socket (i.e. I/O operation), or waiting for some other
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

- After this time elapses, the thread resumes execution, and the function `sleepForABit` returns to the `main`
function.

- Then, `sleepForABit` is called again, this time with a 1000 ms duration argument.

- After these 1000 ms elapse, the `sleepForABit` function returns, and the program ends,
after issuing some more log messages.

<!--- KNIT example-threads-and-coroutines-01.kt -->

Instead of synchronously and serially calling the `sleepForABit` function, we can create two threads
and call the `sleepForABit` function on these threads.

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

Each time the `Thread.sleep` function is called, the running thread (i.e. `Thread-0` or `Thread-1`), is put into a non-ready state, meaning that it will not occupy a CPU.
However, other thread owned resources, such as its stack memory, will still be in use while the time elapses.
The same would happen if the called function was an I/O operation and not a simple `Thread.sleep`.
A so called _blocking operation_ doesn't occupy a CPU, however it still occupies a thread and all its related resources, namely memory.
This introduces a limit to the number of threads we may have in this state.

<!--- KNIT example-threads-and-coroutines-02.kt -->

## `suspend` functions and coroutines

Let's now consider an equivalent example where:

- The "sleeping" function, now called `delayForABit` is a `suspend` function.
- Instead of `Thread.sleep` we call `delay`, which is also a `suspend` function present at `kotlinx.coroutines``

```kotlin
suspend fun delayForABit(duration: Duration) {
    log.info("before sleep")
    delay(duration.toMillis())
    log.info("after sleep")
}
```

In addition, instead of using the `delayForABit` function to create two threads, we use it to create two _coroutines_.
Notice how the new `main` function is similar to the previous one:

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

These log messages show something very interesting:

- To start, all log messages are issued in the `main` thread. That means no extra threads are used to run the code, even when multiple coroutines are created and started.
- Looking into the `before sleep` messages, we notice that the first message is followed by the second one, well before the first `after sleep` appears. This means that the call to `delay` on one coroutine doesn't block the hosting thread, allowed that thread to call `delay` on the second thread.

That is, statements from different coroutines run in the context of the same thread, one at a time, similarly to how multiple threads run in the same CPU, also one at a time.
This is possible because a thread can leave a coroutine on a suspension point and still be available to run statements from another coroutine.
When the suspension point is ready to be resumed, because some time elapsed, or an I/O operation completed, the previous thread can re-enter the coroutine and continue its execution from the suspension point onwards.



<!--- KNIT example-threads-and-coroutines-03.kt -->


