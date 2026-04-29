package com.application.zaona.weather.service

import android.content.Context
import com.application.zaona.weather.BuildConfig
import com.application.zaona.weather.model.CityLocation
import com.application.zaona.weather.model.GeoResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WeatherService {
    private val backendBaseUrl = BuildConfig.WEATHER_BACKEND_BASE_URL.trimEnd('/')
    private val clientType = BuildConfig.WEATHER_CLIENT_TYPE
    private val apiKey = BuildConfig.WEATHER_API_KEY
    private const val PREFS_NAME = "weather_prefs"
    private const val KEY_RECENT_SEARCHES = "weather_recent_searches"
    private const val MAX_RECENT_SEARCHES = 10
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    suspend fun searchLocation(context: Context, cityName: String): List<CityLocation> {
        if (cityName.trim().isEmpty()) {
            throw Exception("请输入城市名称")
        }

        val url = toBackendUrl(
            path = "/api/geo/lookup",
            queryParams = mapOf("location" to cityName.trim())
        )
        return executeGeoRequest(url)
    }

    suspend fun getCityByCoordinates(context: Context, longitude: Double, latitude: Double): CityLocation? {
        val locationParam = String.format("%.2f,%.2f", longitude, latitude)
        val url = toBackendUrl(
            path = "/api/geo/lookup",
            queryParams = mapOf("location" to locationParam)
        )
        
        val locations = executeGeoRequest(url)
        return locations.firstOrNull()
    }

    private suspend fun executeGeoRequest(url: String): List<CityLocation> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .applyAuthHeaders()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val geoResponse = gson.fromJson(responseBody, GeoResponse::class.java)

                if (geoResponse.code == "200" && !geoResponse.location.isNullOrEmpty()) {
                    geoResponse.location
                } else if (geoResponse.code == "404") {
                    emptyList()
                } else {
                    throw IOException("API Error: ${geoResponse.code}")
                }
            }
        }
    }

    suspend fun fetchWeatherData(
        context: Context,
        locationId: String,
        days: String,
        cityName: String,
        syncHourly: Boolean,
        syncAlerts: Boolean = false,
    ): String {
        val payload = JsonObject().apply {
            addProperty("locationId", locationId)
            addProperty("source", clientType)
            add("modules", JsonObject().apply {
                addProperty("daily", days)
                if (syncHourly) {
                    addProperty("hourly", "168h")
                }
                if (syncAlerts) {
                    addProperty("alerts", true)
                }
            })
        }

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(toBackendUrl("/api/weather/sync"))
                .applyAuthHeaders()
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val jsonObject = gson.fromJson(responseBody, com.google.gson.JsonObject::class.java)
                
                val code = jsonObject.get("code")?.asString
                if (code != "200") {
                    throw IOException("API Error: $code")
                }
                
                jsonObject.addProperty("location", cityName)
                jsonObject.toString()
            }
        }
    }

    fun loadRecentSearches(context: Context): List<CityLocation> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CityLocation>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToRecentSearches(context: Context, location: CityLocation) {
        val currentList = loadRecentSearches(context).toMutableList()
        
        // Remove if exists
        currentList.removeAll { it.id == location.id }
        
        // Add to top
        currentList.add(0, location)
        
        // Limit size
        if (currentList.size > MAX_RECENT_SEARCHES) {
            currentList.subList(MAX_RECENT_SEARCHES, currentList.size).clear()
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT_SEARCHES, gson.toJson(currentList)).apply()
    }

    private fun toBackendUrl(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): String {
        val builder = backendBaseUrl.toHttpUrl().newBuilder()
        builder.encodedPath("/${path.trimStart('/')}")
        queryParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun Request.Builder.applyAuthHeaders(): Request.Builder {
        return header("X-Client-Type", clientType)
            .header("X-API-Key", apiKey)
    }
}

