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
import androidx.compose.ui.graphics.PathEffect
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
    var useTimezoneSpaFallback by remember { mutableStateOf(true) }

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
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (hasLocationPermission) {
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
        if (hasLocationPermission) {
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

    if (location != null) {
        val declination = SensorHelper.getDeclination(location!!.latitude, location!!.longitude, location!!.altitude, currentTimeMillis)
        compassAzimuth = if (useTrueNorth) (magneticAzimuth + declination + 360f) % 360f else magneticAzimuth
    } else {
        compassAzimuth = magneticAzimuth
    }

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
    } else {
        isNight = SunPositionCalculator.isNightFallback()
        if (useTimezoneSpaFallback) {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateTimezoneSpaFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere).toFloat()
        } else {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere, isDstActive).toFloat()
        }
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
            .background(Color.White) // White background
    ) {
        // Human shadow background silhouette
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                // Lower body / legs
                moveTo(size.width * 0.35f, size.height)
                lineTo(size.width * 0.65f, size.height)
                lineTo(size.width * 0.55f, size.height * 0.5f)
                lineTo(size.width * 0.45f, size.height * 0.5f)
                close()

                // Torso and Shoulders
                moveTo(size.width * 0.45f, size.height * 0.5f)
                lineTo(size.width * 0.55f, size.height * 0.5f)
                lineTo(size.width * 0.75f, size.height * 0.3f) // Right shoulder
                lineTo(size.width * 0.55f, size.height * 0.28f) // Right neck base
                lineTo(size.width * 0.45f, size.height * 0.28f) // Left neck base
                lineTo(size.width * 0.25f, size.height * 0.3f) // Left shoulder
                close()

                // Head
                addOval(androidx.compose.ui.geometry.Rect(
                    size.width * 0.35f, size.height * 0.1f,
                    size.width * 0.65f, size.height * 0.28f
                ))
            }
            drawPath(path, Color.Black.copy(alpha = 0.15f))
        }

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
            Text(debugText, color = Color.Black.copy(alpha=0.5f), fontSize = 10.sp)
        }

        Box(modifier = Modifier.fillMaxSize()) {

            // Central Dial fixed in absolute center
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isNight) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Warning: Sun is below horizon.", color = Color.Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Solar calculations disabled.", color = Color.Black, fontSize = 16.sp)
                    }
                } else {
                    Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {

                            // Central triangle (bigger and sharper)
                            val path = Path().apply {
                                moveTo(size.width / 2, size.height * 0.1f)
                                lineTo(size.width * 0.85f, size.height * 0.9f)
                                lineTo(size.width * 0.15f, size.height * 0.9f)
                                close()
                            }
                            drawPath(path, Color.Gray.copy(alpha = 0.5f))

                            // Half Sun at bottom base
                            drawArc(
                                color = Color(0xFFFFD700), // Gold/Yellow
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = true,
                                topLeft = Offset(size.width / 2 - 32f, size.height * 0.9f - 32f),
                                size = androidx.compose.ui.geometry.Size(64f, 64f)
                            )

                            // Outer unified dial for indicators
                            val dialCenter = Offset(size.width / 2, size.height * 0.5f)
                            val outerRadius = size.width * 0.45f
                            drawCircle(Color.Black, radius = outerRadius, center = dialCenter, style = Stroke(width = 4f))

                            // Draw Magnetic North Indicator
                            rotate(relativeMagneticNorth, dialCenter) {
                                drawLine(
                                    color = Color.Red,
                                    start = dialCenter,
                                    end = Offset(dialCenter.x, dialCenter.y - outerRadius),
                                    strokeWidth = 10f
                                )
                                drawLine(
                                    color = Color.Blue,
                                    start = dialCenter,
                                    end = Offset(dialCenter.x, dialCenter.y + outerRadius),
                                    strokeWidth = 10f
                                )
                            }

                            // Draw Solar North Indicator
                            rotate(solarNorthRelativeAzimuth, dialCenter) {
                                drawLine(
                                    color = Color(0xFFFFD700), // Gold/Yellow
                                    start = dialCenter,
                                    end = Offset(dialCenter.x, dialCenter.y - outerRadius),
                                    strokeWidth = 10f
                                )
                            }

                            // Rotating watch dial (inner)
                            val clockRadius = size.width * 0.25f
                            drawCircle(Color.White, radius = clockRadius, center = dialCenter)

                            val localCal = Calendar.getInstance()

                            val localH = localCal.get(Calendar.HOUR)
                            val localM = localCal.get(Calendar.MINUTE)

                            val stdCal = Calendar.getInstance()
                            if (isDstActive) {
                                stdCal.add(Calendar.HOUR_OF_DAY, -1)
                            }
                            val stdH = stdCal.get(Calendar.HOUR)
                            val stdM = stdCal.get(Calendar.MINUTE)

                            // If using the classic watch bisect fallback with DST, we rotate to the standard hour.
                            // If using Timezone SPA, or GNSS, we just point the local hour hand at the sun.
                            val useStdHandForRotation = isDstActive && (!useGNSS || location == null) && !useTimezoneSpaFallback

                            val h = if (useStdHandForRotation) stdH else localH
                            val m = if (useStdHandForRotation) stdM else localM

                            // Calculate normal hour angle relative to top (12 o'clock)
                            val normalHourAngle = h * 30f + m * 0.5f

                            // We want the primary hour hand to point straight down (towards the sun at bottom).
                            val dialRotation = 180f - normalHourAngle

                            rotate(dialRotation, dialCenter) {
                                val textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                val offset12 = textMeasurer.measure("12", textStyle)
                                val offset3 = textMeasurer.measure("3", textStyle)
                                val offset6 = textMeasurer.measure("6", textStyle)
                                val offset9 = textMeasurer.measure("9", textStyle)

                                // Draw numbers
                                drawText(textMeasurer, "12", dialCenter + Offset(-offset12.size.width/2f, -clockRadius + 8f), style = textStyle)
                                drawText(textMeasurer, "6", dialCenter + Offset(-offset6.size.width/2f, clockRadius - offset6.size.height - 8f), style = textStyle)
                                drawText(textMeasurer, "3", dialCenter + Offset(clockRadius - offset3.size.width - 8f, -offset3.size.height/2f), style = textStyle)
                                drawText(textMeasurer, "9", dialCenter + Offset(-clockRadius + 8f, -offset9.size.height/2f), style = textStyle)

                                // Draw hands inside the rotated context.
                                val minuteAngleInside = Math.toRadians(-90.0 + localM * 6).toFloat()

                                if (isDstActive && (!useGNSS || location == null) && !useTimezoneSpaFallback) {
                                    // If fallback calculation with DST is active, show the standard hour hand (dotted) and the local hour hand (solid)
                                    val stdHourAngleInside = Math.toRadians(-90.0 + (stdH * 30 + stdM * 0.5)).toFloat()
                                    val localHourAngleInside = Math.toRadians(-90.0 + (localH * 30 + localM * 0.5)).toFloat()

                                    // Draw local hour hand (solid)
                                    drawLine(
                                        color = Color.Black,
                                        start = dialCenter,
                                        end = Offset(
                                            dialCenter.x + clockRadius * 0.6f * cos(localHourAngleInside),
                                            dialCenter.y + clockRadius * 0.6f * sin(localHourAngleInside)
                                        ),
                                        strokeWidth = 10f
                                    )

                                    // Draw standard hour hand (dotted)
                                    drawLine(
                                        color = Color.Black,
                                        start = dialCenter,
                                        end = Offset(
                                            dialCenter.x + clockRadius * 0.6f * cos(stdHourAngleInside),
                                            dialCenter.y + clockRadius * 0.6f * sin(stdHourAngleInside)
                                        ),
                                        strokeWidth = 10f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                } else {
                                    // Draw primary hour hand
                                    val hourAngleInside = Math.toRadians(-90.0 + (localH * 30 + localM * 0.5)).toFloat()
                                    drawLine(
                                        color = Color.Black,
                                        start = dialCenter,
                                        end = Offset(
                                            dialCenter.x + clockRadius * 0.6f * cos(hourAngleInside),
                                            dialCenter.y + clockRadius * 0.6f * sin(hourAngleInside)
                                        ),
                                        strokeWidth = 10f
                                    )
                                }

                                drawLine(
                                    color = Color.Black,
                                    start = dialCenter,
                                    end = Offset(
                                        dialCenter.x + clockRadius * 0.8f * cos(minuteAngleInside),
                                        dialCenter.y + clockRadius * 0.8f * sin(minuteAngleInside)
                                    ),
                                    strokeWidth = 6f
                                )
                            }
                        }
                    }
                }
            }

            // Foreground Layout for Titles and Controls
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Compass Corrector", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                if (!isNight) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(String.format("Correction Angle: %.1f°", diff), color = Color.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Align the triangle base with your shadow", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Controls at bottom
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // GNSS Checkbox
                    val gnssEnabled = hasLocationPermission
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useGNSS && gnssEnabled,
                            onCheckedChange = { useGNSS = it },
                            enabled = gnssEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = Color.Black,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("Use GNSS for Solar North", color = if (gnssEnabled) Color.Black else Color.Gray)
                    }

                    // True North Checkbox
                    val trueNorthEnabled = hasLocationPermission && location != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useTrueNorth && trueNorthEnabled,
                            onCheckedChange = { useTrueNorth = it },
                            enabled = trueNorthEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = Color.Black,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("Adjust Magnetic to True North", color = if (trueNorthEnabled) Color.Black else Color.Gray)
                    }

                    if (location == null) {
                        Text("Warning: Magnetic North not adjusted to True North.", color = Color.Red, fontSize = 12.sp)
                    }
                    if (!hasLocationPermission) {
                        Text("GNSS disabled/unavailable. Using fallback calculations.", color = Color.Red, fontSize = 12.sp)
                    }

                    // Fallback Checkboxes
                    val isFallbackActive = !useGNSS || !hasLocationPermission || location == null

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useTimezoneSpaFallback && isFallbackActive,
                            onCheckedChange = { useTimezoneSpaFallback = it },
                            enabled = isFallbackActive,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = Color.Black,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("Use Timezone-Estimated SPA Fallback", color = if (isFallbackActive) Color.Black else Color.Gray, fontSize = 14.sp)
                    }

                    val isDstCheckboxEnabled = isFallbackActive && !useTimezoneSpaFallback
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isDstActive && isDstCheckboxEnabled,
                            onCheckedChange = { isDstActive = it },
                            enabled = isDstCheckboxEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = Color.Black,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("DST Active (Daylight Saving Time)", color = if (isDstCheckboxEnabled) Color.Black else Color.Gray, fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hemisphere: ", color = if (isFallbackActive) Color.Black else Color.Gray, fontSize = 14.sp)
                        Button(
                            onClick = { isNorthernHemisphere = true },
                            enabled = isFallbackActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNorthernHemisphere) Color.Blue else Color.Gray,
                                disabledContainerColor = Color.LightGray
                            ),
                            modifier = Modifier.height(36.dp)
                        ) { Text("N") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { isNorthernHemisphere = false },
                            enabled = isFallbackActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isNorthernHemisphere) Color.Blue else Color.Gray,
                                disabledContainerColor = Color.LightGray
                            ),
                            modifier = Modifier.height(36.dp)
                        ) { Text("S") }
                    }
                }
            }
        }
    }
}
