package com.dailygallery.app.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The real OkHttp client against a local server. */
class OkHttpGetTest {
    private val server = MockWebServer().apply { start() }
    private val http = OkHttpGet()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun returnsTheBodyAndNamesTheApp() {
        server.enqueue(MockResponse().setBody("""{"ok": true}"""))
        assertEquals("""{"ok": true}""", runBlocking { http.get(server.url("/artworks/1").toString()) })
        val request = server.takeRequest()
        assertEquals(OkHttpGet.USER_AGENT, request.getHeader("User-Agent"))
        assertEquals(OkHttpGet.USER_AGENT, request.getHeader("AIC-User-Agent"))
    }

    @Test
    fun aMissingPageIsNotFoundAndOtherErrorsComeFromTheServer() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows(FetchException.NotFound::class.java) { runBlocking { http.get(server.url("/a").toString()) } }
        assertThrows(FetchException.Server::class.java) { runBlocking { http.get(server.url("/b").toString()) } }
    }

    @Test
    fun noConnectionIsOffline() {
        val url = server.url("/").toString()
        server.shutdown()
        assertThrows(FetchException.Offline::class.java) { runBlocking { http.get(url) } }
    }
}
