package org.pedrofelix.course.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Ignore
import kotlin.test.Test

private val logger = LoggerFactory.getLogger(ScopeAndContextTests::class.java)

@Ignore
class BlockingIOTests {
    fun getConn(
        delay: Long,
        port: Int,
    ): HttpsURLConnection {
        val uri = URI("https://httpbin.org:$port/delay/$delay")
        return uri.toURL().openConnection() as HttpsURLConnection
    }

    fun getStatusCode(conn: HttpsURLConnection): Int {
        conn.connect()
        logger.trace("After connect")
        val statusCode = conn.responseCode
        return statusCode
    }

    @InternalCoroutinesApi
    @Test
    fun first(): Unit =
        runBlocking(Dispatchers.IO) {
            withTimeout(1000) {
                runInterruptible {
                    val conn = getConn(5, 444)
                    logger.trace("HttpsURLConnection obtained")
                    val status = getStatusCode(conn)
                    logger.trace("Status code: {}", status)
                }
            }
        }

    @Test
    fun `cancelling a Thread sleep`(): Unit =
        runBlocking(Dispatchers.IO) {
            withTimeout(2000) {
                runInterruptible {
                    try {
                        logger.info("Before sleep")
                        Thread.sleep(10000)
                    } catch (e: InterruptedException) {
                        logger.info("InterruptedException")
                    }
                }
            }
        }

    @Test
    fun `trying to cancel a socket connect`(): Unit =
        runBlocking(Dispatchers.IO) {
            withTimeout(2000) {
                val socket = Socket()
                runInterruptible {
                    socket.connect(InetSocketAddress("httpbin.org", 444))
                }
            }
        }

    @Test
    fun `trying to cancel a socket read`(): Unit =
        runBlocking(Dispatchers.IO) {
            withTimeout(2000) {
                val socket = Socket()
                runInterruptible {
                    socket.connect(InetSocketAddress("httpbin.org", 443))
                    val buf = ByteArray(8)
                    socket.getInputStream().read(buf)
                    Unit
                }
            }
        }

    @Test
    fun `trying to cancel a socket read using interrupts`() {
        val socket = Socket()
        val th =
            Thread {
                socket.connect(InetSocketAddress("httpbin.org", 443))
                val buf = ByteArray(8)
                socket.getInputStream().read(buf)
            }.apply { start() }
        Thread.sleep(1000)
        logger.trace("interrupting")
        th.interrupt()
        th.join(2000)
        logger.trace("closing socket")
        socket.close()
        logger.trace("End")
    }

    @Test
    fun `TODO`() =
        runBlocking {
            withTimeout(2000) {
                val socket = Socket()
                withContext(Dispatchers.IO) {
                    try {
                        async {
                            socket.connect(InetSocketAddress("httpbin.org", 443))
                            val buf = ByteArray(8)
                            socket.getInputStream().read(buf)
                            logger.trace("done")
                            42
                        }.await()
                    } catch (e: CancellationException) {
                        logger.trace("cancelled")
                        socket.close()
                    }
                }
                Unit
            }
        }

    @Test
    fun `TODO2`() =
        runBlocking {
            withTimeout(2000) {
                val conn = getConn(5, 443)
                withContext(Dispatchers.IO) {
                    try {
                        async {
                            logger.trace("HttpsURLConnection obtained")
                            val status = getStatusCode(conn)
                            logger.trace("Status code: {}", status)
                            42
                        }.await()
                    } catch (e: CancellationException) {
                        logger.trace("cancelled")
                        conn.disconnect()
                    }
                }
                Unit
            }
        }

    fun blockingFunction() {
        Thread.sleep(1000)
    }

    suspend fun suspendBlockingFunction() {
        Thread.sleep(1000)
    }

    suspend fun suspendBlockingFunctionWithIODispatcher() {
        withContext(Dispatchers.IO) {
            Thread.sleep(1000)
        }
    }

    @Test
    fun `running a suspend blocking function on the Main dispatcher`() {
        runBlocking {
            launch {
                logger.trace("Starting first coroutine")
                suspendBlockingFunction()
            }
            launch {
                logger.trace("Starting second coroutine")
                suspendBlockingFunction()
            }
        }
    }

    @Test
    fun `running a suspend blocking function on the IO dispatcher`() {
        runBlocking {
            launch {
                logger.trace("Starting first coroutine")
                suspendBlockingFunctionWithIODispatcher()
            }
            launch {
                logger.trace("Starting second coroutine")
                suspendBlockingFunctionWithIODispatcher()
            }
        }
    }
}
