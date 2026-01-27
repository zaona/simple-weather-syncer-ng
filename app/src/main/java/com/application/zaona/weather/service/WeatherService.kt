package com.application.zaona.weather.service

import android.content.Context
import android.content.SharedPreferences
import com.application.zaona.weather.model.CityLocation
import com.application.zaona.weather.model.GeoResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WeatherService {
    private const val DEFAULT_API_HOST = "ma7aaq4xg5.re.qweatherapi.com"
    // TODO: Should be loaded from secure storage or user input
    private const val DEFAULT_API_KEY = "8e247f6669f14acf848fb2021c9d0f59" 
    
    private const val ANDROID_PACKAGE_NAME = "com.application.zaona.weather"
    private const val ANDROID_CERT_SHA1 = "36:02:8A:67:4E:C2:7A:F6:08:2D:C0:F9:34:B8:93:8A:5A:A6:A2:7E" 
    
    private const val PREFS_NAME = "weather_prefs"
    private const val KEY_RECENT_SEARCHES = "weather_recent_searches"
    private const val MAX_RECENT_SEARCHES = 10

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    suspend fun searchLocation(cityName: String): List<CityLocation> {
        if (cityName.trim().isEmpty()) {
            throw Exception("请输入城市名称")
        }

        val url = "https://$DEFAULT_API_HOST/geo/v2/city/lookup?location=$cityName&key=$DEFAULT_API_KEY"
        return executeGeoRequest(url)
    }

    suspend fun getCityByCoordinates(longitude: Double, latitude: Double): CityLocation? {
        // Format: longitude,latitude (e.g. 116.41,39.92)
        // Keep 2 decimal places as in v1
        val locationParam = String.format("%.2f,%.2f", longitude, latitude)
        val url = "https://$DEFAULT_API_HOST/geo/v2/city/lookup?location=$locationParam&key=$DEFAULT_API_KEY"
        
        val locations = executeGeoRequest(url)
        return locations.firstOrNull()
    }

    private suspend fun executeGeoRequest(url: String): List<CityLocation> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("X-Android-Package-Name", ANDROID_PACKAGE_NAME)
                .header("X-Android-Cert", ANDROID_CERT_SHA1)
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

    suspend fun fetchDailyWeather(locationId: String, days: String, cityName: String): String {
        val url = "https://$DEFAULT_API_HOST/v7/weather/$days?location=$locationId&key=$DEFAULT_API_KEY"
        
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("X-Android-Package-Name", ANDROID_PACKAGE_NAME)
                .header("X-Android-Cert", ANDROID_CERT_SHA1)
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
}

