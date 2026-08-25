package com.example.compasscorrector.ui

import android.content.Context
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compasscorrector.GnssDiagnosticHelper
import com.example.compasscorrector.GnssDiagnosticState

@Composable
fun DiagnosticsScreen(
    foregroundColor: Color,
    magneticAzimuth: Float,
    magneticAccuracy: Int,
    magneticFieldStrength: Float,
    hasLocationPermission: Boolean
) {
    val context = LocalContext.current
    val helper = remember { GnssDiagnosticHelper(context) }
    val gnssState by helper.state.collectAsState()

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            helper.startDiagnostics()
        }
        onDispose {
            helper.stopDiagnostics()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge, color = foregroundColor)
        Spacer(modifier = Modifier.height(16.dp))

        Text("GNSS Diagnostics", style = MaterialTheme.typography.titleMedium, color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))

        if (!hasLocationPermission) {
            Text("Location Permission Required for GNSS Diagnostics.", color = Color.Red, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Location Discrepancy
        val netLoc = gnssState.networkLocation
        val gpsLoc = gnssState.gpsLocation

        var distanceString = "Waiting for locations..."
        if (netLoc != null && gpsLoc != null) {
            val dist = netLoc.distanceTo(gpsLoc)
            distanceString = "Distance between Network and GPS: ${String.format("%.1f", dist)} meters"
        }
        Text(distanceString, color = foregroundColor)

        if (netLoc != null) {
            Text("Network Location: ${String.format("%.4f, %.4f", netLoc.latitude, netLoc.longitude)}", color = Color.Gray, fontSize = 12.sp)
        }
        if (gpsLoc != null) {
            Text("GPS Location: ${String.format("%.4f, %.4f", gpsLoc.latitude, gpsLoc.longitude)}", color = Color.Gray, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Satellite Constellations
        Text("Satellites Visible: ${gnssState.totalSatellites}", color = foregroundColor)
        Text("Satellites Used in Fix: ${gnssState.usedSatellites}", color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text("GPS (US): ${gnssState.gpsSatellites}", color = foregroundColor)
        Text("Galileo (EU): ${gnssState.galileoSatellites}", color = foregroundColor)
        Text("GLONASS (RU): ${gnssState.glonassSatellites}", color = foregroundColor)
        Text("BeiDou (CN): ${gnssState.beidouSatellites}", color = foregroundColor)

        Spacer(modifier = Modifier.height(16.dp))

        // Signal Strengths
        Text("Signal Strength Anomalies", style = MaterialTheme.typography.titleSmall, color = foregroundColor)
        Text("Max C/N0: ${String.format("%.1f", gnssState.maxCn0DbHz)} dB-Hz", color = foregroundColor)
        Text("Avg C/N0: ${String.format("%.1f", gnssState.avgCn0DbHz)} dB-Hz", color = foregroundColor)
        if (gnssState.totalSatellites > 0 && gnssState.avgCn0DbHz > 45f) {
            Text("Warning: Unusually high average signal strength. Possible spoofing.", color = Color.Red)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Orientation Diagnostics", style = MaterialTheme.typography.titleMedium, color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Live Magnetic Azimuth: ${String.format("%.1f°", magneticAzimuth)}", color = foregroundColor)

        Spacer(modifier = Modifier.height(8.dp))

        val accuracyText = when (magneticAccuracy) {
            android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable (Calibrate immediately!)"
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low (Calibration needed)"
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            else -> "Unknown ($magneticAccuracy)"
        }
        val accuracyColor = when (magneticAccuracy) {
            android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE,
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color.Red
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color.Yellow
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color.Green
            else -> foregroundColor
        }
        Text("Compass Sensor Accuracy: $accuracyText", color = accuracyColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Magnetic Field Strength: ${String.format("%.1f µT", magneticFieldStrength)}", color = foregroundColor)

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "To calibrate the compass and improve accuracy, move your device in a figure-8 motion.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
