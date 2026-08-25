package com.example.compasscorrector.ui

import android.content.Context
import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

        Text("Location Methods", style = MaterialTheme.typography.titleMedium, color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))

        if (!hasLocationPermission) {
            Text("Location Permission Required for GNSS Diagnostics.", color = Color.Red, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Location Table
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Provider", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor)
                Text("Lat", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.End)
                Text("Lon", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            val netLoc = gnssState.networkLocation
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Network", modifier = Modifier.weight(1f), color = foregroundColor)
                Text(if (netLoc != null) String.format("%.4f", netLoc.latitude) else "-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
                Text(if (netLoc != null) String.format("%.4f", netLoc.longitude) else "-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
            }

            val gpsLoc = gnssState.gpsLocation
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("GPS", modifier = Modifier.weight(1f), color = foregroundColor)
                Text(if (gpsLoc != null) String.format("%.4f", gpsLoc.latitude) else "-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
                Text(if (gpsLoc != null) String.format("%.4f", gpsLoc.longitude) else "-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            var distanceString = "Distance: Waiting for locations..."
            if (netLoc != null && gpsLoc != null) {
                val dist = netLoc.distanceTo(gpsLoc)
                distanceString = "Distance: ${String.format("%.1f", dist)} meters"
            }
            Text(distanceString, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = foregroundColor, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("GNSS Constellations", style = MaterialTheme.typography.titleMedium, color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))

        // Satellites Table
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("Constellation", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = foregroundColor)
                Text("In View", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.Center)
                Text("In Fix", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.Center)
            }
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

            var expandedConstellation by remember { mutableStateOf<String?>(null) }

            gnssState.constellations.values.filter { it.inViewCount > 0 }.forEach { data ->
                val constelId = data.name
                Column(modifier = Modifier.fillMaxWidth().clickable {
                    expandedConstellation = if (expandedConstellation == constelId) null else constelId
                }.padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("${data.flag} ${data.name}", modifier = Modifier.weight(1.5f), color = foregroundColor)
                        Text("${data.inViewCount}", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.Center)
                        Text("${data.inFixCount}", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.Center)
                    }

                    if (expandedConstellation == constelId) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
                            // In Fix Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("In Fix (C/N0)", fontWeight = FontWeight.Bold, color = foregroundColor, fontSize = 12.sp)
                                data.satellites.filter { it.usedInFix }.forEach { sat ->
                                    Text("SV${sat.svid}: ${String.format("%.1f", sat.cn0DbHz)}", color = Color.Green, fontSize = 12.sp)
                                }
                            }
                            // In View Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Out of Fix (C/N0)", fontWeight = FontWeight.Bold, color = foregroundColor, fontSize = 12.sp)
                                data.satellites.filter { !it.usedInFix }.forEach { sat ->
                                    Text("SV${sat.svid}: ${String.format("%.1f", sat.cn0DbHz)}", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("TOTAL", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = foregroundColor)
                Text("${gnssState.totalInView}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.Center)
                val fixColor = if (gnssState.totalInFix < 4) Color.Red else foregroundColor
                Text("${gnssState.totalInFix}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = fixColor, textAlign = TextAlign.Center)
            }
            if (gnssState.totalInFix < 4 && gnssState.totalInView > 0) {
                Text("Warning: Very low total satellites in fix. Location unreliable.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Signal Strengths", style = MaterialTheme.typography.titleSmall, color = foregroundColor)
        Text("Avg C/N0 (In Fix): ${String.format("%.1f", gnssState.avgCn0InFix)} dB-Hz", color = foregroundColor)
        Text("Avg C/N0 (Out of Fix): ${String.format("%.1f", gnssState.avgCn0OutFix)} dB-Hz", color = foregroundColor)

        if (gnssState.avgCn0OutFix > gnssState.avgCn0InFix && gnssState.avgCn0OutFix > 0f) {
             Text("Note: Out-of-fix signals are stronger on average.", color = Color.Yellow, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Orientation Diagnostics", style = MaterialTheme.typography.titleMedium, color = foregroundColor)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("Live Magnetic Azimuth: ${String.format("%.1f°", magneticAzimuth)}", color = foregroundColor, modifier = Modifier.padding(end = 16.dp))

            // Visual Compass
            Canvas(modifier = Modifier.size(60.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width / 2f
                drawCircle(color = foregroundColor, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                val rotationRad = Math.toRadians(-magneticAzimuth.toDouble() - 90.0)

                // Draw N marker at top of device (always up)
                // Draw rotating compass needle
                val endX = cx + (r * 0.8f) * kotlin.math.cos(rotationRad).toFloat()
                val endY = cy + (r * 0.8f) * kotlin.math.sin(rotationRad).toFloat()

                drawLine(color = Color.Red, start = Offset(cx, cy), end = Offset(endX, endY), strokeWidth = 4f)

                // Draw South tail
                val southRad = rotationRad + Math.PI
                val sEndX = cx + (r * 0.8f) * kotlin.math.cos(southRad).toFloat()
                val sEndY = cy + (r * 0.8f) * kotlin.math.sin(southRad).toFloat()
                drawLine(color = Color.Blue, start = Offset(cx, cy), end = Offset(sEndX, sEndY), strokeWidth = 4f)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
        Text("Compass Sensor Accuracy: $accuracyText", color = accuracyColor, fontWeight = FontWeight.Bold)
        Text("Magnetic Field Strength: ${String.format("%.1f µT", magneticFieldStrength)}", color = foregroundColor)

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "To calibrate the compass and improve accuracy, move your device in a figure-8 motion.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
