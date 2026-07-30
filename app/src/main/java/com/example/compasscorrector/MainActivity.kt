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

    // Alignment Preferences
    var pointPeakAtSolar by remember { mutableStateOf(false) }
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
    val compassAzimuth: Float

    if (location != null) {
        val declination = SensorHelper.getDeclination(location!!.latitude, location!!.longitude, location!!.altitude, currentTimeMillis)
        compassAzimuth = if (useTrueNorth) (magneticAzimuth + declination + 360f) % 360f else magneticAzimuth
    } else {
        compassAzimuth = magneticAzimuth
    }

    var nightWarningMsg: String? = null
    var solarNorthRelativeAzimuth: Float
    if (useGNSS && location != null) {
        val lat = location!!.latitude
        val lon = location!!.longitude
        val isNight = SunPositionCalculator.isNight(lat, lon, currentTimeMillis)
        if (isNight) {
            nightWarningMsg = "Warning: Sun is below horizon."
        }
        val solarAbsoluteAzimuth = SunPositionCalculator.calculateSolarAzimuth(lat, lon, currentTimeMillis).toFloat()
        solarNorthRelativeAzimuth = (180f - solarAbsoluteAzimuth + 360f) % 360f
    } else {
        val isNight = SunPositionCalculator.isNightFallback(currentTimeMillis, isNorthernHemisphere)
        if (isNight) {
            nightWarningMsg = "Warning: Sun may be below horizon."
        }
        if (useTimezoneSpaFallback) {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateTimezoneSpaFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere).toFloat()
        } else {
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere, isDstActive).toFloat()
        }
    }
    if (pointPeakAtSolar) {
        solarNorthRelativeAzimuth = (solarNorthRelativeAzimuth + 180f) % 360f
    }

    // --- Moon Azimuth Calculation ---
    var lunarNorthRelativeAzimuth: Float
    if (useGNSS && location != null) {
        val lat = location!!.latitude
        val lon = location!!.longitude
        val lunarAbsoluteAzimuth = MoonPositionCalculator.calculateLunarAzimuth(lat, lon, currentTimeMillis).toFloat()
        lunarNorthRelativeAzimuth = (180f - lunarAbsoluteAzimuth + 360f) % 360f
    } else {
        if (useTimezoneSpaFallback) {
            lunarNorthRelativeAzimuth = MoonPositionCalculator.calculateTimezoneFallbackNorthAzimuth(currentTimeMillis, isNorthernHemisphere).toFloat()
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
    val isLunarAnalogFallbackActive = (!useGNSS || location == null) && !useTimezoneSpaFallback

    val textMeasurer = rememberTextMeasurer()
    var selectedTabIndex by remember { mutableStateOf(0) }
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
                        useGNSS = useGNSS,
                        onUseGNSSChange = { useGNSS = it },
                        useTrueNorth = useTrueNorth,
                        onUseTrueNorthChange = { useTrueNorth = it },
                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = useTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { useTimezoneSpaFallback = it },
                        isDstActive = isDstActive,
                        onIsDstActiveChange = { isDstActive = it },
                        isNorthernHemisphere = isNorthernHemisphere,
                        onIsNorthernHemisphereChange = { isNorthernHemisphere = it },
                        nightWarningMsg = nightWarningMsg,
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
                        useGNSS = useGNSS,
                        onUseGNSSChange = { useGNSS = it },
                        useTrueNorth = useTrueNorth,
                        onUseTrueNorthChange = { useTrueNorth = it },
                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = useTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { useTimezoneSpaFallback = it },
                        isDstActive = isDstActive,
                        onIsDstActiveChange = { isDstActive = it },
                        isNorthernHemisphere = isNorthernHemisphere,
                        onIsNorthernHemisphereChange = { isNorthernHemisphere = it },
                        nightWarningMsg = null, // No day warning for moon (simplified)
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

                    val arcStartAngle = if (pointPeakAtBody) 0f else 180f

                    if (!isLunar) {
                        // Sun Rays
                        val rayLength = 12f
                        val rayOffset = 6f
                        for (i in 0..6) {
                            val angle = arcStartAngle + (180f / 6) * i
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

                        // Sun Body
                        drawArc(
                            color = Color(0xFFFFD700),
                            startAngle = arcStartAngle,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(centerIconBase.x - radius, centerIconBase.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
                    } else {
                        // Pale Yellow Moon Icon
                        drawArc(
                            color = Color(0xFFFFF59D), // Pale Yellow
                            startAngle = arcStartAngle,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(centerIconBase.x - radius, centerIconBase.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                        )
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = pointPeakAtBody,
                    onCheckedChange = onPointPeakChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Blue,
                        uncheckedColor = Color.Black,
                        checkmarkColor = Color.White
                    )
                )
                val celestialName = if (isLunar) "Moon" else "Sun"
                Text("Point triangle peak at $celestialName", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            val instructionText = if (pointPeakAtBody) {
                val celestialName = if (isLunar) "moon" else "sun"
                "Point the triangle peak at the $celestialName"
            } else {
                "Align the triangle base with your shadow"
            }
            Text(instructionText, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.weight(1f))

            // Controls at bottom
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val gnssEnabled = hasLocationPermission
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useGNSS && gnssEnabled,
                        onCheckedChange = onUseGNSSChange,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useTrueNorth && trueNorthEnabled,
                        onCheckedChange = onUseTrueNorthChange,
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useTimezoneSpaFallback && isFallbackActive,
                        onCheckedChange = onUseTimezoneSpaFallbackChange,
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
                        onCheckedChange = onIsDstActiveChange,
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
