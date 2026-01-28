package com.application.zaona.weather.service

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    
    data class AppUpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val updateDescription: String,
        val downloadUrl: String,
        val forceUpdate: Boolean = true
    )
    
    data class UpdateCheckResult(
        val checkFailed: Boolean,
        val hasUpdate: Boolean,
        val updateInfo: AppUpdateInfo? = null,
        val errorMessage: String? = null
    )
    
    private const val UPDATE_CONFIG_URL = "https://gitee.com/zaona/simple-weather-update/raw/master/update.json"

    private fun removeAbiOffset(versionCode: Int): Int {
        return when {
            versionCode >= 4000 -> versionCode - 4000
            versionCode >= 3000 -> versionCode - 3000
            versionCode >= 2000 -> versionCode - 2000
            versionCode >= 1000 -> versionCode - 1000
            else -> versionCode
        }
    }
    
    suspend fun checkForUpdateManually(context: Context): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val pi = pm.getPackageInfo(context.packageName, 0)
                val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                    (pi.longVersionCode and 0xFFFFFFFF).toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pi.versionCode
                }
                val baseCode = removeAbiOffset(currentCode)
                
                val request = Request.Builder()
                    .url(UPDATE_CONFIG_URL)
                    .header("Accept", "application/json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateCheckResult(
                            checkFailed = true,
                            hasUpdate = false,
                            errorMessage = "服务器返回错误: ${response.code}"
                        )
                    }
                    
                    val body = response.body?.string() ?: return@withContext UpdateCheckResult(
                        checkFailed = true,
                        hasUpdate = false,
                        errorMessage = "空响应"
                    )
                    
                    val info = gson.fromJson(body, AppUpdateInfo::class.java)
                    return@withContext if (info.versionCode > baseCode) {
                        UpdateCheckResult(
                            checkFailed = false,
                            hasUpdate = true,
                            updateInfo = info
                        )
                    } else {
                        UpdateCheckResult(
                            checkFailed = false,
                            hasUpdate = false
                        )
                    }
                }
            } catch (e: Exception) {
                UpdateCheckResult(
                    checkFailed = true,
                    hasUpdate = false,
                    errorMessage = e.message ?: "检查更新失败"
                )
            }
        }
    }
}
