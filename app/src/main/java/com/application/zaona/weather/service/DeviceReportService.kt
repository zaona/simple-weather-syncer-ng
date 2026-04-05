package com.application.zaona.weather.service

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.application.zaona.weather.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object DeviceReportService {
    private const val TAG = "DeviceReportService"
    private val backendBaseUrl = BuildConfig.WEATHER_BACKEND_BASE_URL.trimEnd('/')
    private val clientType = BuildConfig.WEATHER_CLIENT_TYPE
    private val apiKey = BuildConfig.WEATHER_API_KEY
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private lateinit var deviceId: String

    fun init(context: Context) {
        deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (deviceId.isNullOrEmpty()) {
            deviceId = "unknown_android_device"
        }
    }

    suspend fun reportDeviceName(deviceName: String): Result<Unit> {
        if (!::deviceId.isInitialized) {
            return Result.failure(IllegalStateException("Service not initialized. Call init(context) first."))
        }
        if (deviceName.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("deviceName is empty"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val payload = JsonObject().apply {
                    addProperty("deviceId", deviceId)
                    addProperty("deviceName", deviceName.trim())
                }

                val request = Request.Builder()
                    .url(toBackendUrl("/api/device/report"))
                    .header("X-Client-Type", clientType)
                    .header("X-API-Key", apiKey)
                    .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code ${response.code}: $body")
                    }

                    val json = gson.fromJson(body, JsonObject::class.java)
                    val code = json?.get("code")?.asString
                    if (code != "200") {
                        val message = json?.get("message")?.asString ?: "unknown error"
                        throw IOException("API Error: $code, message=$message")
                    }
                }

                Log.d(TAG, "Device reported successfully via backend: $deviceName")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting device", e)
                Result.failure(e)
            }
        }
    }

    private fun toBackendUrl(path: String): String {
        val builder = backendBaseUrl.toHttpUrl().newBuilder()
        builder.encodedPath("/${path.trimStart('/')}")
        return builder.build().toString()
    }
}
