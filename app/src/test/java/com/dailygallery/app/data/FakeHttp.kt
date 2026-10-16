package com.dailygallery.app.data

import java.util.Collections

/** A saved response from src/test/resources/fixtures. */
fun fixture(name: String): String =
    requireNotNull(FakeHttp::class.java.getResource("/fixtures/$name")) { "No fixture $name" }.readText()

/**
 * Answers each request from the first route whose URL prefix matches; anything else is a 404.
 * Routes return a saved response or throw, to stand in for a failing service.
 */
class FakeHttp(private vararg val routes: Pair<String, () -> String>) : HttpGet {
    val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override suspend fun get(url: String): String {
        requested += url
        val route = routes.firstOrNull { url.startsWith(it.first) } ?: throw FetchException.NotFound(url)
        return route.second()
    }
}
