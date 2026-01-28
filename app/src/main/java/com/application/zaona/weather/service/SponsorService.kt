package com.application.zaona.weather.service

import com.application.zaona.weather.model.SponsorData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SponsorService {
    private const val SPONSOR_URL = "https://gitee.com/zaona/simple-weather-update/raw/master/sponsor.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchSponsors(onProgress: (Float) -> Unit = {}): SponsorData = withContext(Dispatchers.IO) {
        onProgress(0.1f)
        val request = Request.Builder()
            .url(SPONSOR_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Network error: ${response.code}")
            
            onProgress(0.5f)
            
            val body = response.body?.string() ?: throw Exception("Empty response")
            val data = gson.fromJson(body, SponsorData::class.java)
            
            onProgress(1.0f)
            data
        }
    }
}
