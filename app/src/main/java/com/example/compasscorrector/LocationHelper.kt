package com.example.compasscorrector

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var isUpdating = false
    private var lastLocation: Location? = null

    private var callback: ((Location) -> Unit)? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            callback?.invoke(location)
        }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocationResult: (Location) -> Unit) {
        if (isUpdating) return
        callback = onLocationResult

        // Fused Location fallback logic removed.
        // We will just passively listen to whatever GPS/Network LocationManager pumps out to this listener
        // But for direct routing, we'll actually rely more on GnssDiagnosticHelper's state which already fetches GPS & Network separately!
        // To keep this generic wrapper intact for components that just want "a location", we'll request both.

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
             locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
        }
        isUpdating = true
    }

    fun stopLocationUpdates() {
        if (!isUpdating) return
        locationManager.removeUpdates(locationListener)
        callback = null
        isUpdating = false
    }
}
