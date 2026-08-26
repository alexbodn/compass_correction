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

data class SatelliteInfo(
    val svid: Int,
    val cn0DbHz: Float,
    val usedInFix: Boolean
)

data class ConstellationData(
    val name: String,
    val flag: String,
    val inViewCount: Int = 0,
    val inFixCount: Int = 0,
    val satellites: List<SatelliteInfo> = emptyList()
)

data class GnssDiagnosticState(
    val networkLocation: Location? = null,
    val gpsLocation: Location? = null,
    val totalInView: Int = 0,
    val totalInFix: Int = 0,
    val constellations: Map<Int, ConstellationData> = mapOf(
        GnssStatus.CONSTELLATION_GPS to ConstellationData("GPS", "🇺🇸"),
        GnssStatus.CONSTELLATION_GALILEO to ConstellationData("Galileo", "🇪🇺"),
        GnssStatus.CONSTELLATION_GLONASS to ConstellationData("GLONASS", "🇷🇺"),
        GnssStatus.CONSTELLATION_BEIDOU to ConstellationData("BeiDou", "🇨🇳"),
        GnssStatus.CONSTELLATION_QZSS to ConstellationData("QZSS", "🇯🇵"),
        GnssStatus.CONSTELLATION_IRNSS to ConstellationData("IRNSS", "🇮🇳"),
        GnssStatus.CONSTELLATION_SBAS to ConstellationData("SBAS", "🛰️"),
        GnssStatus.CONSTELLATION_UNKNOWN to ConstellationData("Other", "❓")
    ),
    val avgCn0InFix: Float = 0f,
    val avgCn0OutFix: Float = 0f
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
            val count = status.satelliteCount

            var totalInView = 0
            var totalInFix = 0
            var sumCn0InFix = 0f
            var sumCn0OutFix = 0f

            val tempMap = mutableMapOf<Int, MutableList<SatelliteInfo>>()

            for (i in 0 until count) {
                val used = status.usedInFix(i)
                val type = status.getConstellationType(i)
                val cn0 = status.getCn0DbHz(i)
                val svid = status.getSvid(i)

                totalInView++
                if (used) {
                    totalInFix++
                    sumCn0InFix += cn0
                } else {
                    sumCn0OutFix += cn0
                }

                val key = if (type in listOf(
                    GnssStatus.CONSTELLATION_GPS,
                    GnssStatus.CONSTELLATION_GALILEO,
                    GnssStatus.CONSTELLATION_GLONASS,
                    GnssStatus.CONSTELLATION_BEIDOU,
                    GnssStatus.CONSTELLATION_QZSS,
                    GnssStatus.CONSTELLATION_IRNSS,
                    GnssStatus.CONSTELLATION_SBAS
                )) type else GnssStatus.CONSTELLATION_UNKNOWN
                tempMap.getOrPut(key) { mutableListOf() }.add(SatelliteInfo(svid, cn0, used))
            }

            val newConstellations = _state.value.constellations.mapValues { (key, data) ->
                val sats = tempMap[key] ?: emptyList()
                data.copy(
                    inViewCount = sats.size,
                    inFixCount = sats.count { it.usedInFix },
                    satellites = sats
                )
            }

            _state.value = _state.value.copy(
                totalInView = totalInView,
                totalInFix = totalInFix,
                constellations = newConstellations,
                avgCn0InFix = if (totalInFix > 0) sumCn0InFix / totalInFix else 0f,
                avgCn0OutFix = if ((totalInView - totalInFix) > 0) sumCn0OutFix / (totalInView - totalInFix) else 0f
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
