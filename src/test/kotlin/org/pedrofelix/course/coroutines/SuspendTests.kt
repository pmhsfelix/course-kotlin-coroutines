package org.pedrofelix.course.coroutines

import org.slf4j.LoggerFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.test.Test

private val logger = LoggerFactory.getLogger(SuspendTests::class.java)

class SuspendTests {
    @Test
    fun `understanding suspend functions`() {
        var currentContinuation: Continuation<Unit>? = null
        var done = false

        /*
         A suspend function with a loop. On each loop iteration, the suspend function
         suspend it execution, effectively returning to the caller. During that process
         a reference to the continuation point after the resume point is provided and
         this example code stores it on a variable.
         */
        suspend fun aSuspendFunction(s: String): Int {
            logger.trace("Suspend function called with s={}", s)
            repeat(3) {
                logger.trace("On iteration {}, before suspend", it)

                // suspendCoroutineUninterceptedOrReturn is an intrinsic function
                // (i.e. a function implemented directly by the compiler)
                // that calls the provided block with a reference to the next statement.
                // This reference is called the continuation and in this case points to the
                // logger.trace(...) statement.
                suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                    // here we just store the provided continuation on an external variable.
                    currentContinuation = continuation
                    // this tell the suspendCoroutineUninterceptedOrReturn that we want to suspend
                    COROUTINE_SUSPENDED
                }

                // the continuation will "point" to this statement
                logger.trace("On iteration {}, after suspend", it)
            }
            return 42
        }

        // A suspend function cannot be called directly from a non suspend function, because suspend
        // functions are compiled according to the Continuation Passing Style (CPS), while non suspend
        // functions are compiled in the Direct Style.
        // - In CPS, a function receives an extra argument with a reference to the code that must be run when the
        //   function completes.
        // - This enables the function to have suspend points, where it returns to the immediate caller even
        //   without having completed (e.g. in the middle of a loop)
        // - When it is finally completed, it calls the passed in continuation.
        // Here we just cast the suspend function to a non-suspend function taking into consideration that extra
        // parameter. Since in the function produces an Int, that means that the passed in continuation receives
        // an Int.
        @Suppress("UNCHECKED_CAST")
        val nonSuspendFunctionReference = ::aSuspendFunction as (String, Continuation<Int>) -> Unit

        // This is the continuation we are going to pass to the suspend function and that will be called by it
        // when it completes
        val continuation =
            object : Continuation<Int> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Int>) {
                    logger.trace("Terminal continuation called with result={}", result)
                    done = true
                }
            }

        // Let's start by calling the suspend function
        nonSuspendFunctionReference.invoke("start", continuation)
        // The function returns not because it was completed, but because the first suspend point was reached
        logger.trace("Suspend function returned")
        while (!done) {
            // And we have a continuation that we can call to resume the suspend function.
            assert(currentContinuation != null)
            logger.trace("Calling continuation")
            currentContinuation?.resumeWith(Result.success(Unit))
            // The continuation call returned because a new suspend point was reached
            // or because it completed, called the initially provided continuation and this one also
            // returned.
            logger.trace("Continuation returned")
        }

        // It's a good idea to run this test function and observe the trace messages
    }
}
