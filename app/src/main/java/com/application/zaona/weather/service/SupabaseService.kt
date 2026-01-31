package com.application.zaona.weather.service

import android.content.Context
import android.util.Log
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

const val SUPABASE_URL = "https://jqoiedegwagkjdqaxhze.supabase.co"
const val SUPABASE_KEY = "sb_publishable_4-KIUmkjcmU0A98w-hP_XA_GFNJo5KL"

@Serializable
data class UserDevice(
    val user_id: String? = null,
    val device_name: String,
    val created_at: String? = null,
    val updated_at: String? = null
)

object SupabaseService {
    private const val TAG = "SupabaseService"
    private val mutex = Mutex()

    lateinit var supabase: SupabaseClient
        private set

    fun init(context: Context) {
        if (::supabase.isInitialized) return

        val sharedPrefs = context.getSharedPreferences("supabase-auth", Context.MODE_PRIVATE)
        val settings = SharedPreferencesSettings(sharedPrefs)

        supabase = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Auth) {
                sessionManager = SettingsSessionManager(settings)
            }
            
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }

    suspend fun reportDeviceName(deviceName: String): Result<Unit> {
        if (!::supabase.isInitialized) {
            return Result.failure(IllegalStateException("SupabaseService not initialized. Call init(context) first."))
        }

        return withContext(Dispatchers.IO) {
            try {
                // 1. Ensure user is logged in (Thread-safe check)
                val userId = mutex.withLock {
                    if (supabase.auth.currentSessionOrNull() == null) {
                        Log.d(TAG, "No active session, signing in anonymously...")
                        supabase.auth.signInAnonymously()
                    }
                    
                    val user = supabase.auth.currentUserOrNull()
                    user?.id ?: throw Exception("User login failed")
                }

                Log.d(TAG, "User ID: $userId")

                // 2. Insert into Supabase (Upsert based on user_id)
                val device = UserDevice(
                    user_id = userId,
                    device_name = deviceName,
                    updated_at = Instant.now().toString()
                )
                
                supabase.from("user_devices").upsert(device) {
                    onConflict = "user_id"
                }
                
                Log.d(TAG, "Device reported successfully: $deviceName")
                return@withContext Result.success(Unit) // Success, exit
                
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting device", e)
                return@withContext Result.failure(e)
            }
        }
    }
}
