package org.pedrofelix.course.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test

class ContextTests {
    /**
     *  An example [CoroutineContext.Element] using a companion object as its key.
     */
    class SomeContextElement(val value: String) : AbstractCoroutineContextElement(SomeContextElement) {
        companion object : CoroutineContext.Key<SomeContextElement>
    }

    /**
     * Another example [CoroutineContext.Element].
     */
    class AnotherContextElement(val value: String) : AbstractCoroutineContextElement(AnotherContextElement) {
        companion object : CoroutineContext.Key<AnotherContextElement>
    }

    @Test
    fun `setting and accessing coroutine context`() =
        runBlocking<Unit> {
            // defining the context of a new coroutine by adding two context elements
            launch(
                SomeContextElement("hello") +
                    AnotherContextElement("world") +
                    Dispatchers.Default,
            ) {
                someSuspendFunction()
            }
        }

    private suspend fun someSuspendFunction() {
        // The context and its elements are available everywhere
        val someContext =
            coroutineContext[SomeContextElement]
                ?: throw IllegalStateException("missing required context")
        val anotherContext =
            coroutineContext[AnotherContextElement]
                ?: throw IllegalStateException("missing required context")
        logger.info("someContext: {}", someContext.value)
        logger.info("anotherContext: {}", anotherContext.value)
        logger.info("dispatcher: {}", coroutineContext[ContinuationInterceptor])
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContextTests::class.java)
    }
}
