package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(FlowIntroTests::class.java)

class FlowIntroTests {

    @Test
    fun begin() {

        // represents a computation that can be called, may suspend during execution and returns an Int
        val aSuspendFunctionReturningAnInt: suspend () -> Int = suspend {
            delay(100)
            42
        }

        // represents a computation that can be called, may suspend during execution and returns a List of Int
        val aSuspendFunctionReturningAListOfInts: suspend () -> List<Int> = suspend {
            delay(100)
            listOf(1, 2)
        }

        // represents a computation that can be called and that will emit Ints during its execution.
        val aFlowProducingInts: Flow<Int> = flow {
            for (i in 1..2) {
                delay(100)
                emit(i)
            }
        }

        runBlocking {

            // invoke will return an Int, eventually after some suspensions
            val theInt: Int = aSuspendFunctionReturningAnInt.invoke()

            // invoke will return a List of Int, eventually after some suspensions
            val theIntList: List<Int> = aSuspendFunctionReturningAListOfInts.invoke()

            // collect will call the passed in block for each value produced by the flow
            aFlowProducingInts.collect {
                logger.trace("A Int produced by the flow: {}", it)
            }
        }
    }

    @Test
    fun `defining and consuming a flow`() {
        // A flow, defined outside of any coroutine
        // The flow defines a suspendable computation that produces/emits multiples values
        // Notice how the suspensions may occur between the production of each value
        val theFlow = flow {
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
     * Takeaways:
     * - A flow can be defined anywhere, namely outside of a coroutine.
     * - No computation is run when the flow is defined. Defining a flow defines the computation but doesn't run it.
     * - Any suspend function can run the computation by "collecting" the items produced by the flow.
     * - The flow computation will run in the coroutine where it was "collected".
     */

    @Test
    fun `partial collect - collecting only the first element`() {

        val theFlow = flow {
            try {
                for (i in 0..7) {
                    delay(100)
                    logger.trace("Flow emitting {}", i)
                    emit(i)
                }
            } catch (e: CancellationException) {
                logger.trace("flow aborted with {}", e.message)
                throw e
            }
        }

        runBlocking {
            // println(sum(theFlow))
            logger.info("Obtaining the first element of the flow: {}", theFlow.first())
            logger.info("Obtaining the first element of the flow: {}", theFlow.first())
        }
    }
    /*
     * Takeaways:
     * - Notice how on the two `theFlow.first()` call, the flow computation always starts at the first value.
     *   I.e. the first computation state is not reused on the second call.
     * - Notice how the `for` inside the flow is _broken_ (early end), because the `emit` throws `CancellationException`
     * - HOWEVER that exception is not thrown outside of the `first` call, i.e, outside the collect.
     *   This is due to exception transparency.
     */

    @Test
    fun `transform operator`() = runBlocking {

        val initialFlow = (0..5).asFlow()
        val transformedFlow = initialFlow.transform {
            it
            emit(it)
            emit(it)
        }

        var acc = 0
        transformedFlow.collect {
            acc += it
        }
        assertEquals(30, acc)
    }

    @Test
    fun `changing context`() = runBlocking {

        val theFlow = flow {
            repeat(5) {
                logger.info("about to sleep")
                Thread.sleep(500)
                emit(it)
            }
        }.flowOn(Dispatchers.IO)

        logger.info("before collect")
        theFlow.collect {
            delay(1000)
            logger.info("collection '{}'", it)
        }
    }

    @Test
    fun `exception transparency`() = runBlocking {
        val theFlow = flow {
            try {
                repeat(5) {
                    logger.info("emitting {}", it)
                    emit(it)
                    delay(100)
                }
            } catch (ex: Exception) {
                logger.info("caught {}", ex.message)
                throw ex
            }
        }

        logger.info("before collect")
        try {
            theFlow.collect {
                logger.info("collection '{}'", it)
                throw Exception("For testing purposes")
            }
            fail()
        } catch (ex: Exception) {
            assertEquals("For testing purposes", ex.message)
        }
    }

    @Test
    fun `changing context and exceptions`() = runBlocking {
        val theFlow = flow {
            try {
                repeat(5) {
                    logger.info("emitting {}", it)
                    emit(it)
                    delay(100)
                }
            } catch (ex: Exception) {
                logger.info("caught {}", ex.message)
                throw ex
            }
        }.flowOn(Dispatchers.IO)

        logger.info("before collect")
        try {
            theFlow.collect {
                logger.info("collection '{}'", it)
                throw Exception("For testing purposes")
            }
            fail()
        } catch (ex: Exception) {
            assertEquals("For testing purposes", ex.message)
        }
    }

    @Test
    fun `using delay`() = runBlocking {
        var attempt = 0
        val theFlow = flow {
            logger.info("Flow called")
            if(attempt == 0) {
                attempt += 1
                throw Exception("Exception on emission")
            }
            emit(42)
        }.retry()

        logger.info("Start collecting")
        theFlow.collect {
            logger.info("Collecting '{}'", it)
        }

    }
}