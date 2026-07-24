package com.example.compasscorrector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var sensorHelper: SensorHelper
    private lateinit var locationHelper: LocationHelper

    private var hasLocationPermission by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorHelper = SensorHelper(this)
        locationHelper = LocationHelper(this)

        checkLocationPermission()

        setContent {
            MaterialTheme {
                CompassApp(sensorHelper, locationHelper, hasLocationPermission)
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        sensorHelper.start()
        // We handle location resuming in Compose using lifecycle effects or state
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stop()
        locationHelper.stopLocationUpdates()
    }
}

@Composable
fun CompassApp(sensorHelper: SensorHelper, locationHelper: LocationHelper, hasLocationPermission: Boolean) {
    var magneticAzimuth by remember { mutableStateOf(0f) }
    var location by remember { mutableStateOf<android.location.Location?>(null) }

    var useGNSS by remember { mutableStateOf(false) }
    var useTrueNorth by remember { mutableStateOf(false) }
    var isNorthernHemisphere by remember { mutableStateOf(true) } // For manual fallback

    // Auto-detect DST
    val defaultDst = java.util.TimeZone.getDefault().inDaylightTime(java.util.Date())
    var isDstActive by remember { mutableStateOf(defaultDst) }

    // Update magnetic azimuth
    DisposableEffect(Unit) {
        sensorHelper.onAzimuthChanged = { azimuth ->
            magneticAzimuth = azimuth
        }
        onDispose {
            sensorHelper.onAzimuthChanged = null
        }
    }

    // Lifecycle observer to handle pause/resume for location updates
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasLocationPermission, useGNSS) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (hasLocationPermission && useGNSS) {
                    locationHelper.startLocationUpdates { loc ->
                        location = loc
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                locationHelper.stopLocationUpdates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial setup
        if (hasLocationPermission && useGNSS) {
            locationHelper.startLocationUpdates { loc ->
                location = loc
            }
        } else {
            locationHelper.stopLocationUpdates()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationHelper.stopLocationUpdates()
        }
    }

    val currentTimeMillis = System.currentTimeMillis()

    // Calculations
    val isNight: Boolean
    val solarNorthRelativeAzimuth: Float
    val compassAzimuth: Float

    if (useGNSS && location != null) {
        val lat = location!!.latitude
        val lon = location!!.longitude
        isNight = SunPositionCalculator.isNight(lat, lon, currentTimeMillis)

        // Exact solar absolute azimuth
        val solarAbsoluteAzimuth = SunPositionCalculator.calculateSolarAzimuth(lat, lon, currentTimeMillis).toFloat()

        // The user points the bottom of the device at the sun.
        // So absolute device heading = solarAbsoluteAzimuth - 180
        // We want the relative angle of North (0) compared to device top:
        solarNorthRelativeAzimuth = (180f - solarAbsoluteAzimuth + 360f) % 360f

        val declination = SensorHelper.getDeclination(lat, lon, location!!.altitude, currentTimeMillis)
        compassAzimuth = if (useTrueNorth) (magneticAzimuth + declination + 360f) % 360f else magneticAzimuth

    } else {
        isNight = SunPositionCalculator.isNightFallback()
        solarNorthRelativeAzimuth = SunPositionCalculator.calculateFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere, isDstActive).toFloat()
        compassAzimuth = magneticAzimuth
    }

    // Difference between magnetic compass north and solar calculated north
    // Correction = SolarNorth - CompassNorth
    var correction = solarNorthRelativeAzimuth - (360f - compassAzimuth) // Wait, compassAzimuth is the device heading relative to North.
    // If device points North, compassAzimuth = 0. Arrow points straight up.
    // So relative angle to North is 360 - compassAzimuth.
    val relativeMagneticNorth = (360f - compassAzimuth) % 360f

    var diff = solarNorthRelativeAzimuth - relativeMagneticNorth
    if (diff < -180f) diff += 360f
    if (diff > 180f) diff -= 360f

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray) // Dark background
    ) {
        // Debug Text at very top left
        Column(modifier = Modifier.padding(4.dp).align(Alignment.TopStart)) {
            val debugText = """
                Loc: ${if (location != null) "%.4f, %.4f".format(location!!.latitude, location!!.longitude) else "null"}
                Magnetic Az: %.1f
                True North: $useTrueNorth
                Solar Az (Rel): %.1f
                DST: $isDstActive
                Hemi: ${if(isNorthernHemisphere) "N" else "S"}
            """.trimIndent().format(magneticAzimuth, solarNorthRelativeAzimuth)
            Text(debugText, color = Color.White.copy(alpha=0.5f), fontSize = 10.sp)
        }

        // Human shadow background silhouette
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.4f, size.height)
                lineTo(size.width * 0.6f, size.height)
                lineTo(size.width * 0.7f, size.height * 0.5f)
                lineTo(size.width * 0.55f, size.height * 0.3f)
                // Head
                addOval(androidx.compose.ui.geometry.Rect(
                    size.width * 0.4f, size.height * 0.15f,
                    size.width * 0.6f, size.height * 0.3f
                ))
                lineTo(size.width * 0.45f, size.height * 0.3f)
                lineTo(size.width * 0.3f, size.height * 0.5f)
                close()
            }
            drawPath(path, Color(0xFF222222))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Align the triangle base with your shadow", color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

            if (isNight) {
                Text("Warning: It is night time. Sun features disabled.", color = Color.Red, fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
            } else {

                Text(String.format("Correction Angle: %.1f°", diff), color = Color.Yellow, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Magnetic Compass
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(Color.White, style = Stroke(width = 4f))
                            rotate(relativeMagneticNorth) {
                                drawLine(Color.Red, start = center, end = Offset(center.x, 0f), strokeWidth = 8f)
                                drawLine(Color.Blue, start = center, end = Offset(center.x, size.height), strokeWidth = 8f)
                            }
                        }
                    }

                    // Center: Triangle with upside down clock & half sun
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(size.width / 2, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(path, Color.Gray.copy(alpha = 0.5f))

                            // Half Sun at bottom base
                            drawArc(
                                color = Color.Yellow,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true,
                                topLeft = Offset(size.width / 2 - 24f, size.height - 24f),
                                size = androidx.compose.ui.geometry.Size(48f, 48f)
                            )

                            // Upside down clock
                            val clockRadius = size.width * 0.35f
                            val center = Offset(size.width / 2, size.height * 0.6f)
                            drawCircle(Color.White, radius = clockRadius, center = center)

                            // Quarter numbers for upside-down clock
                            val textStyle = TextStyle(color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val offset12 = textMeasurer.measure("12", textStyle)
                            val offset3 = textMeasurer.measure("3", textStyle)
                            val offset6 = textMeasurer.measure("6", textStyle)
                            val offset9 = textMeasurer.measure("9", textStyle)

                            // 12 is at the bottom
                            drawText(textMeasurer, "12", center + Offset(-offset12.size.width/2f, clockRadius - offset12.size.height - 4f), style = textStyle)
                            // 6 is at the top
                            drawText(textMeasurer, "6", center + Offset(-offset6.size.width/2f, -clockRadius + 4f), style = textStyle)
                            // 3 is at the left (looking upside down)
                            drawText(textMeasurer, "3", center + Offset(-clockRadius + 4f, -offset3.size.height/2f), style = textStyle)
                            // 9 is at the right (looking upside down)
                            drawText(textMeasurer, "9", center + Offset(clockRadius - offset9.size.width - 4f, -offset9.size.height/2f), style = textStyle)

                            val cal = Calendar.getInstance()
                            val h = cal.get(Calendar.HOUR)
                            val m = cal.get(Calendar.MINUTE)

                            // Upside down: 12 is at the bottom (+90 deg mathematically if 0 is right, but Compose 0 is 3 o'clock)
                            // Normally 12 is -90 deg. Upside down 12 is +90 deg.
                            // Hour hand angle: 90 + (h * 30 + m * 0.5)
                            val hourAngle = Math.toRadians(90.0 + (h * 30 + m * 0.5)).toFloat()
                            val minuteAngle = Math.toRadians(90.0 + m * 6).toFloat()

                            drawLine(
                                color = Color.Black,
                                start = center,
                                end = Offset(
                                    center.x + clockRadius * 0.6f * cos(hourAngle),
                                    center.y + clockRadius * 0.6f * sin(hourAngle)
                                ),
                                strokeWidth = 8f
                            )
                            drawLine(
                                color = Color.Black,
                                start = center,
                                end = Offset(
                                    center.x + clockRadius * 0.8f * cos(minuteAngle),
                                    center.y + clockRadius * 0.8f * sin(minuteAngle)
                                ),
                                strokeWidth = 4f
                            )
                        }
                    }

                    // Right: Solar calculated North arrow
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                         Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(Color.White, style = Stroke(width = 4f))
                            rotate(solarNorthRelativeAzimuth) {
                                drawLine(Color.Yellow, start = center, end = Offset(center.x, 0f), strokeWidth = 8f)
                            }
                        }
                    }
                }
            }

            // Controls
            Spacer(modifier = Modifier.height(32.dp))

            if (hasLocationPermission) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useGNSS,
                        onCheckedChange = { useGNSS = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Blue,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White
                        )
                    )
                    Text("Use GNSS for Solar North", color = Color.White)
                }

                // Only enable True North if GNSS is selected AND we actually have a location
                val trueNorthEnabled = useGNSS && location != null

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useTrueNorth,
                        onCheckedChange = { useTrueNorth = it },
                        enabled = trueNorthEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Blue,
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White,
                            disabledUncheckedColor = Color.Gray,
                            disabledCheckedColor = Color.Gray
                        )
                    )
                    Text(
                        "Adjust Magnetic to True North",
                        color = if (trueNorthEnabled) Color.White else Color.Gray
                    )
                }

                if (!useGNSS || location == null) {
                    Text("Warning: Magnetic North is not adjusted to True North.", color = Color.Red)
                }
            } else {
                Text("GNSS disabled/unavailable. Using fallback calculations.", color = Color.Yellow)
            }

            if (!useGNSS || !hasLocationPermission || location == null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isDstActive,
                        onCheckedChange = { isDstActive = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color.Blue, uncheckedColor = Color.White, checkmarkColor = Color.White)
                    )
                    Text("DST Active (Daylight Saving Time)", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Hemisphere:", color = Color.White)
                // Hemisphere selection (Simple two buttons as a globe)
                Row {
                    Button(
                        onClick = { isNorthernHemisphere = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isNorthernHemisphere) Color.Blue else Color.Gray)
                    ) { Text("Northern") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { isNorthernHemisphere = false },
                        colors = ButtonDefaults.buttonColors(containerColor = if (!isNorthernHemisphere) Color.Blue else Color.Gray)
                    ) { Text("Southern") }
                }
            }
        }
    }
}
