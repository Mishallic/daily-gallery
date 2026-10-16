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

/**
 * Runs [block], catching a failed read or an unreadable answer as a failure. Cancellation still
 * cancels, and anything else (a bug) still throws.
 */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: FetchException) {
    Result.failure(failure)
} catch (failure: SerializationException) {
    Result.failure(failure)
} catch (failure: IllegalArgumentException) {
    Result.failure(failure)
}

/** [attempt], when only success matters. */
internal suspend fun <T> optional(block: suspend () -> T): T? = attempt(block).getOrNull()
