package org.pedrofelix.course.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ServerUsingThreads {

    fun run() {
        val serverSocket = ServerSocket()
        serverSocket.bind(InetSocketAddress("0.0.0.0", 8080))
        var clientId = 0
        while (true) {
            val socket = serverSocket.accept()
            logger.info("Socket accepted")
            Thread {
                runClient(socket, ++clientId)
            }.start()
        }
    }

    private fun runClient(socket: Socket, clientId: Int) {
        try {
            logger.info("{}: Starting client", clientId)
            val inputStream = socket.getInputStream()
            val outputStream = socket.getOutputStream()
            val buffer = ByteArray(2)
            while (true) {
                logger.info("{}: reading", clientId)
                val readLen = inputStream.read(buffer)
                logger.info("{}: read {} bytes", clientId, readLen)
                if (readLen == -1) {
                    break
                }
                logger.info("{}: writing", clientId)
                outputStream.write(buffer, 0, readLen)
            }
        } catch (ex: Exception) {
            logger.error("{}: exception {}", clientId, ex.message)
        } finally {
            logger.info("{}: ending", clientId)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServerUsingThreads::class.java)
    }
}

val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

class ServerUsingCoroutines {

    suspend fun run() {
        val serverSocket = AsynchronousServerSocketChannel.open()
        serverSocket.bind(InetSocketAddress("0.0.0.0", 8080))
        var clientId = 0
        val latch = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.info("shutdown started")
                serverSocket.close()
                latch.await()
                logger.info("shutdown completed")
            },
        )
        val handler = CoroutineExceptionHandler { _, ex ->
            logger.info("Coroutine ended with exception: {}", ex.javaClass.simpleName)
        }
        try {
            coroutineScope {
                try {
                    while (true) {
                        val socket = serverSocket.acceptAsync()
                        logger.info("Socket accepted")
                        launch(dispatcher + handler) {
                            runClient(socket, ++clientId)
                        }
                    }
                } catch (ex: AsynchronousCloseException) {
                    logger.info("socket closed, ending server")
                    cancel()
                }
            }
        } catch (ex: CancellationException) {
            logger.info("Expected cancellation")
        }
        latch.countDown()
        logger.info("run ending")
    }

    private suspend fun runClient(channel: AsynchronousSocketChannel, clientId: Int) {
        try {
            logger.info("{}: Starting client", clientId)
            val buffer = ByteBuffer.allocate(2)
            while (true) {
                logger.info("{}: reading", clientId)
                val readLen = channel.readAsync(buffer)
                logger.info("{}: read {} bytes", clientId, readLen)
                if (readLen == -1) {
                    break
                }
                buffer.flip()
                logger.info("{}: writing", clientId)
                channel.writeAllAsync(buffer)
                buffer.clear()
            }
        } catch (ex: AsynchronousCloseException) {
            logger.info("{}: socket closed, ending client", clientId)
        } finally {
            logger.info("{}: ending", clientId)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServerUsingCoroutines::class.java)
    }
}

/*fun main() {
    val server = ServerUsingThreads()
    server.run()
}*/

fun main(): Unit = runBlocking {
    val server = ServerUsingCoroutines()
    server.run()
}

suspend fun AsynchronousServerSocketChannel.acceptAsync() =
    suspendCoroutine<AsynchronousSocketChannel> { continuation ->

        this.accept(
            Unit,
            object : CompletionHandler<AsynchronousSocketChannel, Unit> {
                override fun completed(channel: AsynchronousSocketChannel, attachment: Unit) {
                    continuation.resume(channel)
                }

                override fun failed(exception: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }

private val logger = LoggerFactory.getLogger("Utils")

suspend fun AsynchronousByteChannel.readAsync(buffer: ByteBuffer) =
    suspendCoroutine<Int> { continuation ->
        @OptIn(InternalCoroutinesApi::class)
        continuation.context[Job]?.invokeOnCompletion(onCancelling = true) {
            logger.info("Closing AsynchronousByteChannel")
            this.close()
        }
        this.read(
            buffer,
            Unit,
            object : CompletionHandler<Int, Unit> {
                override fun completed(result: Int, attachment: Unit) {
                    continuation.resume(result)
                }

                override fun failed(exc: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exc)
                }
            },
        )
    }

suspend fun AsynchronousByteChannel.writeAllAsync(buffer: ByteBuffer) {
    do {
        this.writeAsync(buffer)
    } while (buffer.remaining() != 0)
}

suspend fun AsynchronousByteChannel.writeAsync(buffer: ByteBuffer) =
    suspendCoroutine { continuation ->
        this.write(
            buffer,
            Unit,
            object : CompletionHandler<Int, Unit> {

                override fun completed(result: Int, attachment: Unit) {
                    continuation.resume(result)
                }

                override fun failed(exc: Throwable, attachment: Unit) {
                    continuation.resumeWithException(exc)
                }
            },
        )
    }
