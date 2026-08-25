package com.example.compasscorrector

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GnssDiagnosticState(
    val networkLocation: Location? = null,
    val gpsLocation: Location? = null,
    val totalSatellites: Int = 0,
    val usedSatellites: Int = 0,
    val gpsSatellites: Int = 0,
    val galileoSatellites: Int = 0,
    val glonassSatellites: Int = 0,
    val beidouSatellites: Int = 0,
    val maxCn0DbHz: Float = 0f,
    val avgCn0DbHz: Float = 0f
)

class GnssDiagnosticHelper(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(GnssDiagnosticState())
    val state: StateFlow<GnssDiagnosticState> = _state

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val currentState = _state.value
            if (location.provider == LocationManager.NETWORK_PROVIDER) {
                _state.value = currentState.copy(networkLocation = location)
            } else if (location.provider == LocationManager.GPS_PROVIDER) {
                _state.value = currentState.copy(gpsLocation = location)
            }
        }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            var gps = 0
            var galileo = 0
            var glonass = 0
            var beidou = 0
            var totalCn0 = 0f
            var maxCn0 = 0f

            val count = status.satelliteCount
            for (i in 0 until count) {
                if (status.usedInFix(i)) used++
                val type = status.getConstellationType(i)
                when (type) {
                    GnssStatus.CONSTELLATION_GPS -> gps++
                    GnssStatus.CONSTELLATION_GALILEO -> galileo++
                    GnssStatus.CONSTELLATION_GLONASS -> glonass++
                    GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                }
                val cn0 = status.getCn0DbHz(i)
                totalCn0 += cn0
                if (cn0 > maxCn0) maxCn0 = cn0
            }

            _state.value = _state.value.copy(
                totalSatellites = count,
                usedSatellites = used,
                gpsSatellites = gps,
                galileoSatellites = galileo,
                glonassSatellites = glonass,
                beidouSatellites = beidou,
                maxCn0DbHz = maxCn0,
                avgCn0DbHz = if (count > 0) totalCn0 / count else 0f
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiagnostics() {
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
        locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
    }

    fun stopDiagnostics() {
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
    }
}
