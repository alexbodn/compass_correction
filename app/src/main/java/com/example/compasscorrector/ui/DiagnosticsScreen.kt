package com.example.compasscorrector.ui

import com.example.compasscorrector.SextantLockedData

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AccuracyGauge(score: Float, modifier: Modifier = Modifier) {
    // Score is 0.0 (Worst/Red) to 1.0 (Best/Green)
    Canvas(modifier = modifier) {
        val sweepAngle = 180f
        val startAngle = 180f
        val strokeWidth = 8f

        // Draw Red segment
        drawArc(
            color = Color.Red,
            startAngle = 180f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Draw Yellow segment
        drawArc(
            color = Color.Yellow,
            startAngle = 240f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
        // Draw Green segment
        drawArc(
            color = Color.Green,
            startAngle = 300f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw Needle
        val clampedScore = score.coerceIn(0f, 1f)
        val needleAngleRad = Math.toRadians(180.0 + (clampedScore * 180.0))
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.width / 2f

        val endX = cx + (r * 0.8f) * cos(needleAngleRad).toFloat()
        val endY = cy + (r * 0.8f) * sin(needleAngleRad).toFloat()

        drawLine(
            color = Color.White,
            start = Offset(cx, cy),
            end = Offset(endX, endY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawCircle(color = Color.White, radius = 4f, center = Offset(cx, cy))
    }
}

@Composable
fun DiagnosticsScreen(
    foregroundColor: Color,
    magneticAzimuth: Float,
    magneticAccuracy: Int,
    magneticFieldStrength: Float,
    hasLocationPermission: Boolean,
    sextantLockedData: SextantLockedData?,
    onNavigateToSextant: () -> Unit
) {
    val context = LocalContext.current
    val helper = remember { GnssDiagnosticHelper(context) }
    val gnssState by helper.state.collectAsState()

    var csvContentToSave by remember { mutableStateOf("") }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContentToSave.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            helper.startDiagnostics()
        }
        onDispose {
            helper.stopDiagnostics()
        }
    }

    var selectedTabIndex by remember { mutableStateOf(0) }

    val gpsLoc = gnssState.gpsLocation

    // Normalize Location Accuracy (0.0 to 1.0)
    // 0m = 1.0 (Green), 30m = 0.5 (Yellow), >=60m = 0.0 (Red)
    val locScore = if (gpsLoc != null) {
        val acc = gpsLoc.accuracy
        (1f - (acc / 60f)).coerceIn(0f, 1f)
    } else 0f

    val locAccuracyText = if (gpsLoc != null) {
        "Accuracy (${String.format("%.0f", gpsLoc.accuracy)}m)"
    } else "Accuracy"

    val locAccuracyColor = if (gpsLoc != null) {
        if (gpsLoc.accuracy <= 10f) Color.Green
        else if (gpsLoc.accuracy <= 30f) Color.Yellow
        else Color.Red
    } else Color.Gray

    val dirScore = when (magneticAccuracy) {
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 0.9f
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.5f
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.1f
        else -> 0f
    }

    val dirAccuracyText = "Accuracy"

    val dirAccuracyColor = when (magneticAccuracy) {
        android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE,
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Color.Red
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Color.Yellow
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Color.Green
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Tools to diagnose your phone location and direction reliability.\nThe capabilities are very well designed, but measurements depend on environmental conditions that could be suboptimal or misleading.\nWe'll help you diagnose your conditions and find working alternatives, if needed.",
            color = foregroundColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()
        )

        TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, contentColor = foregroundColor) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 }
            ) {
                Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Location", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                    AccuracyGauge(score = locScore, modifier = Modifier.size(20.dp, 10.dp).padding(end = 4.dp))
                    Text(locAccuracyText, color = locAccuracyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 }
            ) {
                Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Direction", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                    AccuracyGauge(score = dirScore, modifier = Modifier.size(20.dp, 10.dp).padding(end = 4.dp))
                    Text(dirAccuracyText, color = dirAccuracyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {

            if (selectedTabIndex == 0) {
                if (!hasLocationPermission) {
                    Text("Location Permission Required for GNSS Diagnostics.", color = Color.Red, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Location Table
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Location Method", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor)
                        Text("Coordinates (Lat, Lon)", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = foregroundColor, textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                    val shareCoordinate = { lat: Double, lon: Double ->
                        val geoUri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)

                        // Fallback to generic text sharing if no map app is installed
                        if (shareIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(shareIntent)
                        } else {
                            val fallbackIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, "$lat, $lon")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(fallbackIntent, "Share Location"))
                        }
                    }

                    val netLoc = gnssState.networkLocation
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("Network", fontWeight = FontWeight.Bold, color = foregroundColor)
                            if (netLoc != null) Text(" ±${String.format("%.0f", netLoc.accuracy)}m", color = Color.Gray, fontSize = 10.sp)
                        }
                        if (netLoc != null) {
                            Text(String.format("%.4f, %.4f", netLoc.latitude, netLoc.longitude), modifier = Modifier.weight(1f).clickable { shareCoordinate(netLoc.latitude, netLoc.longitude) }, color = Color(0xFF64B5F6), textAlign = TextAlign.End)
                        } else {
                            Text("-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
                        }
                    }


                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("GNSS", fontWeight = FontWeight.Bold, color = foregroundColor)
                            if (gpsLoc != null) Text(" ±${String.format("%.0f", gpsLoc.accuracy)}m", color = Color.Gray, fontSize = 10.sp)
                        }
                        if (gpsLoc != null) {
                            Text(String.format("%.4f, %.4f", gpsLoc.latitude, gpsLoc.longitude), modifier = Modifier.weight(1f).clickable { shareCoordinate(gpsLoc.latitude, gpsLoc.longitude) }, color = Color(0xFF64B5F6), textAlign = TextAlign.End)
                        } else {
                            Text("-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sextant", fontWeight = FontWeight.Bold, color = foregroundColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("[Measure]", color = Color(0xFF64B5F6), fontSize = 12.sp, modifier = Modifier.clickable { onNavigateToSextant() })
                        }
                        val sextantLat = sextantLockedData?.deducedLatitude
                        val sextantLon = sextantLockedData?.assumedOrDeducedLongitude
                        // Only show if it was actively deduced (both lat and lon exist implies full compass deduction was used)
                        val isFullyDeduced = sextantLat != null && sextantLon != null && sextantLockedData?.lockedShadowAzimuth != null

                        if (isFullyDeduced) {
                            Text(String.format("%.4f, %.4f", sextantLat!!, sextantLon!!), modifier = Modifier.weight(1f).clickable { shareCoordinate(sextantLat.toDouble(), sextantLon.toDouble()) }, color = Color(0xFF64B5F6), textAlign = TextAlign.End)
                        } else {
                            Text("-", modifier = Modifier.weight(1f), color = foregroundColor, textAlign = TextAlign.End)
                        }
                    }

                    HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                    var distanceString = "Waiting for measured locations..."
                    var distanceColor = foregroundColor
                    if (netLoc != null && gpsLoc != null) {
                        val dist = netLoc.distanceTo(gpsLoc)
                        distanceString = "${String.format("%.1f", dist)}m between measured locations"
                        distanceColor = if (dist < 15f) Color.Green else if (dist < 50f) Color.Yellow else Color.Red
                    }
                    Text(distanceString, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = distanceColor, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Satellites Table
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Constellation", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = foregroundColor)
                        Text("In Fix", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.Green, textAlign = TextAlign.Center)
                        Text("Not in Fix", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                    var expandedConstellation by remember { mutableStateOf<String?>(null) }

                    gnssState.constellations.values.filter { it.inViewCount > 0 }.forEach { data ->
                        val constelId = data.name
                        Column(modifier = Modifier.fillMaxWidth().clickable {
                            expandedConstellation = if (expandedConstellation == constelId) null else constelId
                        }.padding(vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("${data.flag} ${data.name}", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = foregroundColor)
                                val notInFixCount = data.inViewCount - data.inFixCount
                                Text("${data.inFixCount}", modifier = Modifier.weight(1f), color = if (data.inFixCount > 0) Color.Green else Color.Gray, textAlign = TextAlign.Center)
                                Text("$notInFixCount", modifier = Modifier.weight(1f), color = Color.Gray, textAlign = TextAlign.Center)
                            }

                            if (expandedConstellation == constelId) {
                                val sortedSats = data.satellites.sortedByDescending { it.cn0DbHz }
                                sortedSats.forEach { sat ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                        Text("SV${sat.svid}: ${String.format("%.1f", sat.cn0DbHz)}", modifier = Modifier.weight(1.5f).padding(start = 24.dp), color = if (sat.usedInFix) Color.Green else Color.Gray, fontSize = 12.sp)
                                        Text(if (sat.usedInFix) "✅" else "", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Green)
                                        Text(if (!sat.usedInFix) "✔️" else "", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                    var showAverages by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth().clickable { showAverages = !showAverages }.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("TOTAL", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = foregroundColor)
                            val fixColor = if (gnssState.totalInFix < 4) Color.Red else Color.Green
                            Text("${gnssState.totalInFix}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = fixColor, textAlign = TextAlign.Center)
                            val totalNotInFix = gnssState.totalInView - gnssState.totalInFix
                            Text("$totalNotInFix", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                        if (showAverages) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("Average C/N0 (dB-Hz)", modifier = Modifier.weight(1.5f), color = Color.Gray, fontSize = 12.sp)
                                Text(String.format("%.1f", gnssState.avgCn0InFix), modifier = Modifier.weight(1f), color = Color.Green, fontSize = 12.sp, textAlign = TextAlign.Center)
                                Text(String.format("%.1f", gnssState.avgCn0OutFix), modifier = Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                            if (gnssState.avgCn0OutFix > gnssState.avgCn0InFix && gnssState.avgCn0OutFix > 0f) {
                                Text("Warning: Not-in-fix signals are stronger on average. Possible spoofing.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    if (gnssState.totalInFix < 4 && gnssState.totalInView > 0) {
                        Text("Warning: Very low total satellites in fix. Location unreliable.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val csvBuilder = StringBuilder()
                        csvBuilder.append("Constellation,SVID,C/N0(dB-Hz),In_Fix,Azimuth(deg),Elevation(deg),Has_Almanac,Has_Ephemeris,Carrier_Freq(Hz)\n")
                        gnssState.constellations.values.filter { it.inViewCount > 0 }.forEach { data ->
                            data.satellites.sortedByDescending { it.cn0DbHz }.forEach { sat ->
                                csvBuilder.append("${data.name},${sat.svid},${String.format("%.1f", sat.cn0DbHz)},${sat.usedInFix},${String.format("%.1f", sat.azimuthDegrees)},${String.format("%.1f", sat.elevationDegrees)},${sat.hasAlmanac},${sat.hasEphemeris},${sat.carrierFrequencyHz}\n")
                            }
                        }
                        csvContentToSave = csvBuilder.toString()
                        createDocumentLauncher.launch("gnss_diagnostics.csv")
                    }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Download Satellite Data (CSV)")
                    }
                }
            } else {
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

                val detailedAccuracyText = when (magneticAccuracy) {
                    android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable (Calibrate immediately!)"
                    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low (Calibration needed)"
                    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                    else -> "Unknown ($magneticAccuracy)"
                }

                Text("Compass Sensor Accuracy: $detailedAccuracyText", color = dirAccuracyColor, fontWeight = FontWeight.Bold)
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
    }
}
