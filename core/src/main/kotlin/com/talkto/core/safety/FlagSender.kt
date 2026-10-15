package com.talkto.core.safety

import com.talkto.core.avatar.await
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Posts a flagged answer ([FlagReport.json]) to the authors' report address, so a child can report what Claude
 * said without leaving the app. Any address that takes a JSON POST works, for example a Google Apps Script
 * web app that appends a row to a sheet (it answers with a redirect, which OkHttp follows).
 */
class FlagSender(private val url: String, private val http: OkHttpClient = defaultClient()) {

    /** True when the address took the report. Never throws: a report that did not go out is tried again later. */
    suspend fun send(json: String): Boolean = try {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON))
            .build()
        http.newCall(request).await().use { it.isSuccessful }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Only https addresses; anything else means "no report address in this build". */
        fun accepts(url: String?): Boolean = url != null && url.startsWith("https://") && url.length > "https://".length

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
