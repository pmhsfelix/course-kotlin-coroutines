package org.pedrofelix.course.coroutines

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory


class ChannelTests {

    companion object {
        private val logger = LoggerFactory.getLogger(ChannelTests::class.java)
    }

    @Test
    fun first() = runBlocking {

        val poisonPill = ""
        val channel = Channel<String>(10)

        launch {
            for(message in channel) {
                if(message === poisonPill) {
                    logger.info("Received poison pill, ending")
                    return@launch
                }
                logger.info("Received '{}'", message)
            }
        }

        repeat(10) {
            channel.send("message-$it")
            delay(500)
        }
        channel.send(poisonPill)
    }
}