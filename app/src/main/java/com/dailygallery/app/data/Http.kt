package com.dailygallery.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads a URL as text. The app reads through OkHttp; tests answer from saved responses. */
fun interface HttpGet {
    suspend fun get(url: String): String
}

/** Why a request failed, in terms the screen can explain. */
sealed class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Offline(cause: Throwable? = null) : FetchException("No connection", cause)
    class NotFound(url: String) : FetchException("Not found: $url")
    class Server(code: Int, url: String) : FetchException("HTTP $code from $url")
}

/** The museums and Wikipedia add fields over time; unknown ones are ignored, nulls take the defaults. */
internal val sourceJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Runs [block], turning a failed read or an unreadable answer into null. Cancellation still cancels. */
internal suspend fun <T> optional(block: suspend () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: FetchException) {
    null
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
