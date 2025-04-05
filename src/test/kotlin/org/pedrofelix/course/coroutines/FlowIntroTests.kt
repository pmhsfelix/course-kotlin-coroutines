package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val logger = LoggerFactory.getLogger(FlowIntroTests::class.java)

class FlowIntroTests {
    @Test
    fun begin() {
        // represents a computation that can be called, may suspend during execution and returns an Int
        val aSuspendFunctionReturningAnInt: suspend () -> Int =
            suspend {
                delay(100)
                42
            }

        // represents a computation that can be called, may suspend during execution and returns a List of Int
        val aSuspendFunctionReturningAListOfInts: suspend () -> List<Int> =
            suspend {
                delay(100)
                listOf(1, 2)
            }

        // represents a computation that can be called and that will emit Ints during its execution.
        val aFlowProducingInts: Flow<Int> =
            flow {
                for (i in 1..2) {
                    delay(100)
                    emit(i)
                }
            }

        runBlocking {
            // invoke will return an Int, eventually after some suspensions
            val theInt: Int = aSuspendFunctionReturningAnInt.invoke()
            assertEquals(42, theInt)

            // invoke will return a List of Int, eventually after some suspensions
            val theIntList: List<Int> = aSuspendFunctionReturningAListOfInts.invoke()
            assertTrue { theIntList.isNotEmpty() }

            // collect will call the passed in block for each value produced by the flow
            aFlowProducingInts.collect {
                logger.trace("A Int produced by the flow {}", it)
            }
        }
    }

    @Test
    fun `defining and consuming a flow`() {
        // A flow, defined outside of any coroutine
        // The flow defines a suspendable computation that produces/emits multiples values
        // Notice how the suspensions may occur between the production of each value
        val theFlow =
            flow {
                for (i in 0..7) {
                    delay(100)
                    logger.trace("Flow emitting {}", i)
                    emit(i)
                }
            }

        // A suspendable function that uses the flow
        suspend fun sum(flow: Flow<Int>): Int {
            var acc = 0
            // could use a fold
            flow.collect {
                logger.trace("Consuming {}", it)
                acc += it
            }
            return acc
        }

        runBlocking {
            println(sum(theFlow))
        }
    }
    /*
       Takeaways:
       - A flow can be defined anywhere, namely outside of a coroutine.
       - No computation is run when the flow is executed. Defining a flow defines the computation but doesn't run it.
       - Any suspend function can run the computation by "collecting" the items produced by the flow.
       - The flow computation will run in the coroutine where it was "collected".
     */

    @Test
    fun `partial collect - collecting only the first element`() {
        val theFlow =
            flow {
                try {
                    for (i in 0..7) {
                        delay(100)
                        logger.trace("Flow emitting {}", i)
                        emit(i)
                    }
                } catch (e: CancellationException) {
                    logger.trace("flow aborted")
                }
            }

        runBlocking {
            // println(sum(theFlow))
            logger.info("Obtaining the first element of the flow: {}", theFlow.first())
            logger.info("Obtaining the first element of the flow: {}", theFlow.first())
        }
    }
    /*
       Takeaways:
       - Notice how on the two `theFlow.first()` call, the flow computation always starts at the first value.
         I.e. the first computation state is not reused on the second call.
       - Notice how the `for` inside the flow is _broken_ (early end), because the `emit` throws `CancellationException`

     */
}
