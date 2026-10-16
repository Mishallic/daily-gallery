package com.dailygallery.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads URLs over OkHttp. The museums and Wikipedia ask apps to say who they are, so every request
 * names the app; the Art Institute reads its own header for that.
 */
class OkHttpGet(private val client: OkHttpClient = defaultClient()) : HttpGet {

    override suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(FetchException.Offline(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    when {
                        it.isSuccessful -> try {
                            continuation.resume(it.body!!.string())
                        } catch (e: IOException) {
                            continuation.resumeWithException(FetchException.Offline(e))
                        }
                        it.code == 404 -> continuation.resumeWithException(FetchException.NotFound(url))
                        else -> continuation.resumeWithException(FetchException.Server(it.code, url))
                    }
                }
            }
        })
    }

    companion object {
        const val USER_AGENT = "DailyGallery/1.0 (Android; https://github.com/Mishallic)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("AIC-User-Agent", USER_AGENT)
                        .build(),
                )
            }
            .build()
    }
}
