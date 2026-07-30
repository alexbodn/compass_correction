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

    val defaultDst = java.util.TimeZone.getDefault().inDaylightTime(java.util.Date())

    // Solar Settings
    var solarUseGNSS by remember { mutableStateOf(false) }
    var solarUseTrueNorth by remember { mutableStateOf(false) }
    var solarIsNorthernHemisphere by remember { mutableStateOf(true) }
    var solarUseTimezoneSpaFallback by remember { mutableStateOf(true) }
    var solarIsDstActive by remember { mutableStateOf(defaultDst) }
    var pointPeakAtSolar by remember { mutableStateOf(false) }

    // Lunar Settings
    var lunarUseGNSS by remember { mutableStateOf(false) }
    var lunarUseTrueNorth by remember { mutableStateOf(false) }
    var lunarIsNorthernHemisphere by remember { mutableStateOf(true) }
    var lunarUseTimezoneSpaFallback by remember { mutableStateOf(true) }
    var lunarIsDstActive by remember { mutableStateOf(defaultDst) }
    var pointPeakAtLunar by remember { mutableStateOf(false) }

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
    var selectedTabIndex by remember { mutableStateOf(0) }
    val useTrueNorth = if (selectedTabIndex == 0) solarUseTrueNorth else lunarUseTrueNorth
    val compassAzimuth: Float

    if (location != null) {
        val declination = SensorHelper.getDeclination(location!!.latitude, location!!.longitude, location!!.altitude, currentTimeMillis)
        compassAzimuth = if (useTrueNorth) (magneticAzimuth + declination + 360f) % 360f else magneticAzimuth
    } else {
        compassAzimuth = magneticAzimuth
    }

    var solarWarningMsg: String? = null
    var lunarWarningMsg: String? = null

    // Determine visibilities
    val isSunBelowHorizon = if (solarUseGNSS && location != null) {
        SunPositionCalculator.isNight(location!!.latitude, location!!.longitude, currentTimeMillis)
    } else {
        SunPositionCalculator.isNightFallback(currentTimeMillis, solarIsNorthernHemisphere)
    }

    val isMoonBelowHorizon = if (lunarUseGNSS && location != null) {
        MoonPositionCalculator.isMoonBelowHorizon(location!!.latitude, location!!.longitude, currentTimeMillis)
    } else {
        MoonPositionCalculator.isMoonBelowHorizonFallback(currentTimeMillis, lunarIsNorthernHemisphere)
    }

    // Assign recommendations based on opposite body's visibility
    if (isSunBelowHorizon) {
        val gnssString = if (solarUseGNSS && location != null) "is" else "may be"
        solarWarningMsg = "Warning: Sun $gnssString below horizon."
        if (!isMoonBelowHorizon) {
            solarWarningMsg += "\nSuggestion: Try the Lunar tab."
        }
    }
    if (isMoonBelowHorizon) {
        val gnssString = if (lunarUseGNSS && location != null) "is" else "may be"
        lunarWarningMsg = "Warning: Moon $gnssString below horizon."
        if (!isSunBelowHorizon) {
            lunarWarningMsg += "\nSuggestion: Try the Solar tab."
        }
    }

    var solarNorthRelativeAzimuth: Float
    if (solarUseGNSS && location != null) {
        val solarAbsoluteAzimuth = SunPositionCalculator.calculateSolarAzimuth(location!!.latitude, location!!.longitude, currentTimeMillis).toFloat()
        solarNorthRelativeAzimuth = (180f - solarAbsoluteAzimuth + 360f) % 360f
    } else {
        if (solarUseTimezoneSpaFallback) {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateTimezoneSpaFallbackNorthAzimuth(currentTimeMillis, solarIsNorthernHemisphere).toFloat()
        } else {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateFallbackNorthAzimuth(currentTimeMillis, solarIsNorthernHemisphere, solarIsDstActive).toFloat()
        }
    }
    if (pointPeakAtSolar) {
        solarNorthRelativeAzimuth = (solarNorthRelativeAzimuth + 180f) % 360f
    }

    // --- Moon Azimuth Calculation ---
    var lunarNorthRelativeAzimuth: Float
    if (lunarUseGNSS && location != null) {
        val lunarAbsoluteAzimuth = MoonPositionCalculator.calculateLunarAzimuth(location!!.latitude, location!!.longitude, currentTimeMillis).toFloat()
        lunarNorthRelativeAzimuth = (180f - lunarAbsoluteAzimuth + 360f) % 360f
    } else {
        if (lunarUseTimezoneSpaFallback) {
            lunarNorthRelativeAzimuth = MoonPositionCalculator.calculateTimezoneFallbackNorthAzimuth(currentTimeMillis, lunarIsNorthernHemisphere).toFloat()
        } else {
            // Analog watch bisect method does not apply to the moon.
            // When fallback is analog watch, we will just pass 0 or gray it out.
            lunarNorthRelativeAzimuth = 0f
        }
    }
    if (pointPeakAtLunar) {
        lunarNorthRelativeAzimuth = (lunarNorthRelativeAzimuth + 180f) % 360f
    }

    val relativeMagneticNorth = (360f - compassAzimuth) % 360f

    var solarDiff = solarNorthRelativeAzimuth - relativeMagneticNorth
    if (solarDiff < -180f) solarDiff += 360f
    if (solarDiff > 180f) solarDiff -= 360f

    var lunarDiff = lunarNorthRelativeAzimuth - relativeMagneticNorth
    if (lunarDiff < -180f) lunarDiff += 360f
    if (lunarDiff > 180f) lunarDiff -= 360f
    // If the analog watch fallback is active, we cannot calculate the moon properly.
    val isLunarAnalogFallbackActive = (!lunarUseGNSS || location == null) && !lunarUseTimezoneSpaFallback

    val textMeasurer = rememberTextMeasurer()
    val tabs = listOf("Solar", "Lunar")

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Compass Corrector", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Correction Angle line is always visible at the top
            Spacer(modifier = Modifier.height(8.dp))
            if (selectedTabIndex == 1 && isLunarAnalogFallbackActive) {
                Text("Correction Angle: N/A", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                val currentDiff = if (selectedTabIndex == 0) solarDiff else lunarDiff
                Text(String.format("Correction Angle: %.1f°", currentDiff), color = Color.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (selectedTabIndex == 0) {
                    CelestialToolTab(
                        isLunar = false,
                        relativeCelestialNorth = solarNorthRelativeAzimuth,
                        relativeMagneticNorth = relativeMagneticNorth,
                        currentTimeMillis = currentTimeMillis,
                        location = location,
                        magneticAzimuth = magneticAzimuth,
                        useGNSS = solarUseGNSS,
                        onUseGNSSChange = { solarUseGNSS = it },
                        useTrueNorth = solarUseTrueNorth,
                        onUseTrueNorthChange = { solarUseTrueNorth = it },
                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = solarUseTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { solarUseTimezoneSpaFallback = it },
                        isDstActive = solarIsDstActive,
                        onIsDstActiveChange = { solarIsDstActive = it },
                        isNorthernHemisphere = solarIsNorthernHemisphere,
                        onIsNorthernHemisphereChange = { solarIsNorthernHemisphere = it },
                        nightWarningMsg = solarWarningMsg,
                        textMeasurer = textMeasurer,
                        isLunarAnalogFallbackActive = false,
                        pointPeakAtBody = pointPeakAtSolar,
                        onPointPeakChange = { pointPeakAtSolar = it }
                    )
                } else if (selectedTabIndex == 1) {
                    CelestialToolTab(
                        isLunar = true,
                        relativeCelestialNorth = lunarNorthRelativeAzimuth,
                        relativeMagneticNorth = relativeMagneticNorth,
                        currentTimeMillis = currentTimeMillis,
                        location = location,
                        magneticAzimuth = magneticAzimuth,
                        useGNSS = lunarUseGNSS,
                        onUseGNSSChange = { lunarUseGNSS = it },
                        useTrueNorth = lunarUseTrueNorth,
                        onUseTrueNorthChange = { lunarUseTrueNorth = it },
                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = lunarUseTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { lunarUseTimezoneSpaFallback = it },
                        isDstActive = lunarIsDstActive,
                        onIsDstActiveChange = { lunarIsDstActive = it },
                        isNorthernHemisphere = lunarIsNorthernHemisphere,
                        onIsNorthernHemisphereChange = { lunarIsNorthernHemisphere = it },
                        nightWarningMsg = lunarWarningMsg,
                        textMeasurer = textMeasurer,
                        isLunarAnalogFallbackActive = isLunarAnalogFallbackActive,
                        pointPeakAtBody = pointPeakAtLunar,
                        onPointPeakChange = { pointPeakAtLunar = it }
                    )
                }
            } // End of outer tab Box
        } // End of inner padding Column
    } // End of Scaffold
}

@Composable
fun CelestialToolTab(
    isLunar: Boolean,
    relativeCelestialNorth: Float,
    relativeMagneticNorth: Float,
    currentTimeMillis: Long,
    location: android.location.Location?,
    magneticAzimuth: Float,
    useGNSS: Boolean,
    onUseGNSSChange: (Boolean) -> Unit,
    useTrueNorth: Boolean,
    onUseTrueNorthChange: (Boolean) -> Unit,
    hasLocationPermission: Boolean,
    useTimezoneSpaFallback: Boolean,
    onUseTimezoneSpaFallbackChange: (Boolean) -> Unit,
    isDstActive: Boolean,
    onIsDstActiveChange: (Boolean) -> Unit,
    isNorthernHemisphere: Boolean,
    onIsNorthernHemisphereChange: (Boolean) -> Unit,
    nightWarningMsg: String?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    isLunarAnalogFallbackActive: Boolean,
    pointPeakAtBody: Boolean,
    onPointPeakChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Human shadow background silhouette
        if (!pointPeakAtBody) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.35f, size.height)
                    lineTo(size.width * 0.65f, size.height)
                    lineTo(size.width * 0.55f, size.height * 0.5f)
                    lineTo(size.width * 0.45f, size.height * 0.5f)
                    close()

                    moveTo(size.width * 0.45f, size.height * 0.5f)
                    lineTo(size.width * 0.55f, size.height * 0.5f)
                    lineTo(size.width * 0.75f, size.height * 0.3f)
                    lineTo(size.width * 0.55f, size.height * 0.28f)
                    lineTo(size.width * 0.45f, size.height * 0.28f)
                    lineTo(size.width * 0.25f, size.height * 0.3f)
                    close()

                    addOval(androidx.compose.ui.geometry.Rect(
                        size.width * 0.35f, size.height * 0.1f,
                        size.width * 0.65f, size.height * 0.28f
                    ))
                }
                drawPath(path, Color.Black.copy(alpha = 0.15f))
            }
        }

        // Debug Text at very top left
        Column(modifier = Modifier.padding(4.dp).align(Alignment.TopStart)) {
            val debugText = """
                Loc: ${if (location != null) "%.4f, %.4f".format(location!!.latitude, location!!.longitude) else "null"}
                Magnetic Az: %.1f
                True North: $useTrueNorth
                Rel ${if (isLunar) "Lunar" else "Solar"} Az: %.1f
                DST: $isDstActive
                Hemi: ${if (isNorthernHemisphere) "N" else "S"}
            """.trimIndent().format(magneticAzimuth, relativeCelestialNorth)
            Text(debugText, color = Color.Black.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        // Central Dial fixed in absolute center
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {

                    val path = Path().apply {
                        moveTo(size.width / 2, size.height * 0.1f)
                        lineTo(size.width * 0.85f, size.height * 0.9f)
                        lineTo(size.width * 0.15f, size.height * 0.9f)
                        close()
                    }
                    drawPath(path, Color.Red)

                    val centerIconBase = if (pointPeakAtBody) {
                        Offset(size.width / 2, size.height * 0.1f)
                    } else {
                        Offset(size.width / 2, size.height * 0.9f)
                    }
                    val radius = 32f

                    if (!isLunar) {
                        // Full Sun Rays
                        val rayLength = 12f
                        val rayOffset = 6f
                        for (i in 0..11) {
                            val angle = (360f / 12) * i
                            val rad = Math.toRadians(angle.toDouble()).toFloat()
                            val startRay = Offset(
                                centerIconBase.x + (radius + rayOffset) * cos(rad),
                                centerIconBase.y + (radius + rayOffset) * sin(rad)
                            )
                            val endRay = Offset(
                                centerIconBase.x + (radius + rayOffset + rayLength) * cos(rad),
                                centerIconBase.y + (radius + rayOffset + rayLength) * sin(rad)
                            )
                            drawLine(
                                color = Color(0xFFFF9800),
                                start = startRay,
                                end = endRay,
                                strokeWidth = 6f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }

                        // Full Sun Body
                        drawCircle(
                            color = Color(0xFFFFD700),
                            radius = radius,
                            center = centerIconBase
                        )
                    } else {
                        // Moon Phase drawing
                        val phase = MoonPositionCalculator.calculateLunarPhase(currentTimeMillis)
                        val moonColor = Color(0xFFFFF59D) // Pale Yellow
                        val shadowColor = Color.Black // Assuming a dark background overlay or just "shadowed" moon

                        // Base full moon
                        drawCircle(color = moonColor, radius = radius, center = centerIconBase)

                        // Calculate the terminator (the shadow line on the moon)
                        // Phase 0.0 = New Moon, 0.5 = Quarter, 1.0 = Full Moon
                        if (phase < 0.98) {
                            // If it's mostly new moon
                            if (phase < 0.02) {
                                drawCircle(color = shadowColor, radius = radius, center = centerIconBase)
                            } else {
                                // Draw the shadow half
                                val shadowSide = if (phase < 0.5) 1f else -1f // Right side shadowed if waxing, left if waning (simplified)

                                // Determine sweep based on rotation requested by pointPeakAtBody toggle
                                val startAng = if (pointPeakAtBody) 90f else -90f
                                drawArc(
                                    color = shadowColor,
                                    startAngle = startAng,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(centerIconBase.x - radius, centerIconBase.y - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                                )

                                // Ellipse terminator
                                val widthScale = Math.abs(cos(Math.PI * phase)).toFloat()
                                val ovalWidth = radius * 2 * widthScale
                                val ovalLeft = centerIconBase.x - ovalWidth / 2
                                val isLitEllipse = (phase > 0.5)
                                val ellipseColor = if (isLitEllipse) moonColor else shadowColor

                                drawOval(
                                    color = ellipseColor,
                                    topLeft = Offset(ovalLeft, centerIconBase.y - radius),
                                    size = androidx.compose.ui.geometry.Size(ovalWidth, radius * 2)
                                )
                            }
                        }
                    }

                    val dialCenter = Offset(size.width / 2, size.height * 0.5f)
                    val outerRadius = size.width * 0.45f
                    drawCircle(Color.Black, radius = outerRadius, center = dialCenter, style = Stroke(width = 4f))

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

                    if (!isLunarAnalogFallbackActive) {
                        rotate(relativeCelestialNorth, dialCenter) {
                            drawLine(
                                color = if (isLunar) Color(0xFF9E9E9E) else Color(0xFFFFA500),
                                start = dialCenter,
                                end = Offset(dialCenter.x, dialCenter.y - outerRadius),
                                strokeWidth = 10f
                            )
                        }
                    }

                    val clockRadius = outerRadius * 0.7f
                    drawCircle(Color.White, radius = clockRadius, center = dialCenter)
                    drawCircle(Color.Black, radius = clockRadius, center = dialCenter, style = Stroke(width = 2f))

                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentTimeMillis
                    val localH = cal.get(Calendar.HOUR)
                    val localM = cal.get(Calendar.MINUTE)

                    val stdCal = Calendar.getInstance()
                    stdCal.timeInMillis = currentTimeMillis
                    if (isDstActive) {
                        stdCal.add(Calendar.HOUR_OF_DAY, -1)
                    }
                    val stdH = stdCal.get(Calendar.HOUR)
                    val stdM = stdCal.get(Calendar.MINUTE)

                    val useStdHandForRotation = isDstActive && (!useGNSS || location == null) && !useTimezoneSpaFallback

                    val h = if (useStdHandForRotation) stdH else localH
                    val m = if (useStdHandForRotation) stdM else localM

                    val normalHourAngle = h * 30f + m * 0.5f
                    val dialRotation = if (pointPeakAtBody) {
                        360f - normalHourAngle // Point 12 o'clock hour hand up at peak
                    } else {
                        180f - normalHourAngle // Point 12 o'clock hour hand down at base
                    }

                    rotate(dialRotation, dialCenter) {
                        val textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val offset12 = textMeasurer.measure("12", textStyle)
                        val offset3 = textMeasurer.measure("3", textStyle)
                        val offset6 = textMeasurer.measure("6", textStyle)
                        val offset9 = textMeasurer.measure("9", textStyle)

                        drawText(textMeasurer, "12", dialCenter + Offset(-offset12.size.width/2f, -clockRadius + 8f), style = textStyle)
                        drawText(textMeasurer, "6", dialCenter + Offset(-offset6.size.width/2f, clockRadius - offset6.size.height - 8f), style = textStyle)
                        drawText(textMeasurer, "3", dialCenter + Offset(clockRadius - offset3.size.width - 8f, -offset3.size.height/2f), style = textStyle)
                        drawText(textMeasurer, "9", dialCenter + Offset(-clockRadius + 8f, -offset9.size.height/2f), style = textStyle)

                        val minuteAngleInside = Math.toRadians(-90.0 + localM * 6).toFloat()

                        if (isDstActive && (!useGNSS || location == null) && !useTimezoneSpaFallback) {
                            val stdHourAngleInside = Math.toRadians(-90.0 + (stdH * 30 + stdM * 0.5)).toFloat()
                            val localHourAngleInside = Math.toRadians(-90.0 + (localH * 30 + localM * 0.5)).toFloat()

                            drawLine(
                                color = Color.Black,
                                start = dialCenter,
                                end = Offset(
                                    dialCenter.x + clockRadius * 0.6f * cos(localHourAngleInside),
                                    dialCenter.y + clockRadius * 0.6f * sin(localHourAngleInside)
                                ),
                                strokeWidth = 10f
                            )

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

        // Foreground Layout for Text/Controls
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (nightWarningMsg != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(nightWarningMsg, color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (isLunar && isLunarAnalogFallbackActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Lunar analog bisect unsupported. Please enable SPA Fallback.", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            val celestialName = if (isLunar) "the Moon" else "the Sun"

            // Dropdown Menu for Alignment Choice
            var expanded by remember { mutableStateOf(false) }

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(8.dp)
                ) {
                    Text("Point triangle peak at: ", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (pointPeakAtBody) celestialName else "your shadow",
                        color = Color.Blue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(" ▼", color = Color.Black)
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("your shadow") },
                        onClick = {
                            onPointPeakChange(false)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(celestialName) },
                        onClick = {
                            onPointPeakChange(true)
                            expanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls at bottom
            Column(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.Start
            ) {
                val gnssEnabled = hasLocationPermission
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = gnssEnabled) { onUseGNSSChange(!useGNSS) }
                ) {
                    Checkbox(
                        checked = useGNSS && gnssEnabled,
                        onCheckedChange = null,
                        enabled = gnssEnabled,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.Blue,
                            uncheckedColor = Color.Black,
                            checkmarkColor = Color.White,
                            disabledUncheckedColor = Color.Gray,
                            disabledCheckedColor = Color.Gray
                        )
                    )
                    Text("Use GNSS for ${if (isLunar) "Lunar" else "Solar"} North", color = if (gnssEnabled) Color.Black else Color.Gray)
                }

                val trueNorthEnabled = hasLocationPermission && location != null
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = trueNorthEnabled) { onUseTrueNorthChange(!useTrueNorth) }
                ) {
                    Checkbox(
                        checked = useTrueNorth && trueNorthEnabled,
                        onCheckedChange = null,
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

                val isFallbackActive = !useGNSS || !hasLocationPermission || location == null

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isFallbackActive) { onUseTimezoneSpaFallbackChange(!useTimezoneSpaFallback) }
                ) {
                    Checkbox(
                        checked = useTimezoneSpaFallback && isFallbackActive,
                        onCheckedChange = null,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isDstCheckboxEnabled) { onIsDstActiveChange(!isDstActive) }
                ) {
                    Checkbox(
                        checked = isDstActive && isDstCheckboxEnabled,
                        onCheckedChange = null,
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

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                    Text("Hemisphere: ", color = if (isFallbackActive) Color.Black else Color.Gray, fontSize = 14.sp)
                    Button(
                        onClick = { onIsNorthernHemisphereChange(true) },
                        enabled = isFallbackActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNorthernHemisphere) Color.Blue else Color.Gray,
                            disabledContainerColor = Color.LightGray
                        ),
                        modifier = Modifier.height(36.dp)
                    ) { Text("N") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onIsNorthernHemisphereChange(false) },
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
