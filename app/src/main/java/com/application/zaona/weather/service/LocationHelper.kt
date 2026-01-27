package com.application.zaona.weather.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import kotlinx.coroutines.withTimeout

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location {
        if (!hasPermission()) {
            throw SecurityException("请先授予位置权限")
        }

        if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && 
            !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            throw Exception("请打开手机定位服务(GPS)")
        }

        return withTimeout(30000L) { // 30 seconds timeout
            suspendCancellableCoroutine { continuation ->
                // 1. Try last known location first (fastest)
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) 
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                if (lastKnownLocation != null) {
                    // Check if it's fresh enough (e.g. within 10 minutes) if needed
                    // For now, just return it as v1 did a simple check
                    continuation.resume(lastKnownLocation)
                    return@suspendCancellableCoroutine
                }

                // 2. Request single update
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        // Remove listener once we get a location
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        // Don't fail immediately if one provider is disabled, wait for others or timeout
                    }
                }

                try {
                    var requested = false
                    // Prefer Network provider for speed and indoor usage
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestSingleUpdate(
                            LocationManager.NETWORK_PROVIDER,
                            locationListener,
                            Looper.getMainLooper()
                        )
                        requested = true
                    } 
                    
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestSingleUpdate(
                            LocationManager.GPS_PROVIDER,
                            locationListener,
                            Looper.getMainLooper()
                        )
                        requested = true
                    }
                    
                    if (!requested) {
                         if (continuation.isActive) {
                            continuation.resumeWithException(Exception("No location provider enabled"))
                         }
                    }
                    
                    // Cancel listener if coroutine is cancelled
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(locationListener)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }
}
