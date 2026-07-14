package com.nxteam.nxstore.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

object Http {
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response {
        val builder = Request.Builder().url(url).header("User-Agent", DESKTOP_UA)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.newCall(builder.build()).execute()
    }

    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        get(url, headers).use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
            return resp.body?.string() ?: throw java.io.IOException("Empty body for $url")
        }
    }
}
