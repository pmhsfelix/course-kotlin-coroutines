package org.pedrofelix.course.coroutines

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(HttpClientTests::class.java)

class HttpClientTests {

    @Test
    fun `ktor test`() = runBlocking {
        val client = HttpClient(Android)
        val response: HttpResponse = client.get("https://httpbin.org/delay/2")
        logger.trace("status = {}", response.status)
    }
}
