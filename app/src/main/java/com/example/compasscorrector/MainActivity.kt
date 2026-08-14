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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

data class SextantLockedData(
    val altitude: Float,
    val declination: Float,
    val deducedLatitude: Float?,
    val assumedOrDeducedLongitude: Float?
)

class MainActivity : ComponentActivity() {

    private lateinit var sensorHelper: SensorHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var appPreferences: AppPreferences

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
        appPreferences = AppPreferences(this)

        checkLocationPermission()

        setContent {
            MaterialTheme {
                CompassApp(sensorHelper, locationHelper, hasLocationPermission, appPreferences)
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
fun CompassApp(sensorHelper: SensorHelper, locationHelper: LocationHelper, hasLocationPermission: Boolean, appPreferences: AppPreferences) {
    var magneticAzimuth by remember { mutableStateOf(0f) }
    var location by remember { mutableStateOf<android.location.Location?>(null) }

    val defaultDst = java.util.TimeZone.getDefault().inDaylightTime(java.util.Date())

    // Global Settings from Preferences
    var globalUseTrueNorth by remember { mutableStateOf(appPreferences.useTrueNorth) }
    var globalDstMode by remember { mutableStateOf(appPreferences.dstMode) }
    val globalIsDstActive = remember(globalDstMode) { appPreferences.evaluateIsDstActive() }

    // Solar Settings
    var solarUseGNSS by remember { mutableStateOf(false) }
    var solarIsNorthernHemisphere by remember { mutableStateOf(true) }
    var solarUseTimezoneSpaFallback by remember { mutableStateOf(true) }
    var pointPeakAtSolar by remember { mutableStateOf(false) }
    var solarUseClockTimezoneCorrection by remember { mutableStateOf(false) }
    var solarUseMalleableWatchDial by remember { mutableStateOf(false) }
    var sextantLockedData by remember { mutableStateOf<SextantLockedData?>(null) }
    var useCompassForFullLocation by remember { mutableStateOf(false) }
    var livePitch by remember { mutableStateOf(0f) }
    var liveRoll by remember { mutableStateOf(0f) }

    // Lunar Settings
    var lunarUseGNSS by remember { mutableStateOf(false) }
    var lunarIsNorthernHemisphere by remember { mutableStateOf(true) }
    var lunarUseTimezoneSpaFallback by remember { mutableStateOf(true) }
    var pointPeakAtLunar by remember { mutableStateOf(false) }

    // Update magnetic azimuth and inclination
    DisposableEffect(Unit) {
        sensorHelper.onAzimuthChanged = { azimuth ->
            magneticAzimuth = azimuth
        }
        sensorHelper.onInclinationChanged = { pitch, roll ->
            livePitch = pitch
            liveRoll = roll
        }
        onDispose {
            sensorHelper.onAzimuthChanged = null
            sensorHelper.onInclinationChanged = null
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
    var initialTabCalculated by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val useTrueNorth = globalUseTrueNorth
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

    // Determine current app theme
    // We observe appPreferences.theme but must handle state triggering. Since appPreferences isn't a state itself,
    // we use a remember variable that gets updated.
    var currentThemePref by remember { mutableStateOf(appPreferences.theme) }
    // Hook into lifecycle resume to refresh this if it changed elsewhere (though usually UI changes it directly)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                currentThemePref = appPreferences.theme
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A callback for SettingsScreen to trigger state update
    val onThemeChanged: (AppTheme) -> Unit = { newTheme ->
        appPreferences.theme = newTheme
        currentThemePref = newTheme
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (currentThemePref) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemDark
        AppTheme.AUTO_SUNSET -> isSunBelowHorizon
    }

    // Set default tab on startup
    if (!initialTabCalculated) {
        if (isSunBelowHorizon && !isMoonBelowHorizon) {
            selectedTabIndex = 1 // Moon is up, sun is down -> default to Lunar
        } else {
            selectedTabIndex = 0 // Default to Solar if both up, both down, or only Sun is up
        }
        initialTabCalculated = true
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
            solarNorthRelativeAzimuth = SunPositionCalculator.calculateFallbackNorthAzimuth(
                currentTimeMillis,
                solarIsNorthernHemisphere,
                globalIsDstActive,
                solarUseClockTimezoneCorrection
            ).toFloat()
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // 0 = Solar, 1 = Lunar, 2 = Sextant, 3 = Settings
    var currentScreen by remember { mutableStateOf(selectedTabIndex) }

    // Sync selectedTabIndex with currentScreen if it's 0 or 1
    if (currentScreen == 0 || currentScreen == 1) {
        selectedTabIndex = currentScreen
    }

    // Update App Background Color
    val backgroundColor = if (isDarkTheme) Color.Black else Color.White
    val foregroundColor = if (isDarkTheme) Color.White else Color.Black
    val linkColor = if (isDarkTheme) Color(0xFF64B5F6) else Color.Blue

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = backgroundColor,
                drawerContentColor = foregroundColor
            ) {
                Spacer(Modifier.height(12.dp))
                val drawerColors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = if (isDarkTheme) Color.DarkGray else Color.LightGray,
                    unselectedContainerColor = Color.Transparent,
                    selectedTextColor = foregroundColor,
                    unselectedTextColor = foregroundColor,
                    selectedIconColor = foregroundColor,
                    unselectedIconColor = foregroundColor
                )
                NavigationDrawerItem(
                    label = { Text("Solar") },
                    selected = currentScreen == 0,
                    onClick = {
                        currentScreen = 0
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerColors
                )
                NavigationDrawerItem(
                    label = { Text("Lunar") },
                    selected = currentScreen == 1,
                    onClick = {
                        currentScreen = 1
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerColors
                )
                NavigationDrawerItem(
                    label = { Text("Sextant") },
                    selected = currentScreen == 2,
                    onClick = {
                        currentScreen = 2
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerColors
                )
                NavigationDrawerItem(
                    label = { Text("Watch Study") },
                    selected = currentScreen == 4,
                    onClick = {
                        currentScreen = 4
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerColors
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = currentScreen == 3,
                    onClick = {
                        currentScreen = 3
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = drawerColors
                )
            }
        }
    ) {
        Scaffold(
            containerColor = backgroundColor,
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Compass Corrector", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (currentScreen == 4) {
                    WatchStudyScreen(
                        foregroundColor = foregroundColor,
                        currentTimeMillis = currentTimeMillis,
                        isNorthernHemisphere = solarIsNorthernHemisphere
                    )
                } else if (currentScreen == 3) {
                    // Settings Screen
                    SettingsScreen(
                        currentTheme = currentThemePref,
                        onThemeChanged = onThemeChanged,
                        foregroundColor = foregroundColor,
                        useTrueNorth = globalUseTrueNorth,
                        onUseTrueNorthChange = {
                            globalUseTrueNorth = it
                            appPreferences.useTrueNorth = it
                        },
                        dstMode = globalDstMode,
                        onDstModeChange = {
                            globalDstMode = it
                            appPreferences.dstMode = it
                        },
                        hasLocationPermission = hasLocationPermission
                    )
                } else if (currentScreen == 2) {
                    // Sextant Screen
                    SextantScreen(
                        foregroundColor = foregroundColor,
                        currentTimeMillis = currentTimeMillis,
                        isNorthernHemisphere = solarIsNorthernHemisphere,
                        onIsNorthernHemisphereChange = {
                            solarIsNorthernHemisphere = it
                            lunarIsNorthernHemisphere = it
                        },
                        livePitch = livePitch,
                        liveRoll = liveRoll,
                        lockedData = sextantLockedData,
                        onLockedDataChange = { sextantLockedData = it },
                        useCompassForFullLocation = useCompassForFullLocation,
                        onUseCompassForFullLocationChange = { useCompassForFullLocation = it },
                        magneticAzimuth = magneticAzimuth,
                        useTrueNorth = globalUseTrueNorth,
                        location = location
                    )
                } else {
                    // Correction Angle line is always visible at the top for tools
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selectedTabIndex == 1 && isLunarAnalogFallbackActive) {
                        Text("Correction Angle: N/A", color = Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val currentDiff = if (selectedTabIndex == 0) solarDiff else lunarDiff
                        Text(String.format("Correction Angle: %.1f°", currentDiff), color = Color.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        if (selectedTabIndex == 0) {
                    CelestialToolTab(
                        isDarkTheme = isDarkTheme,
                        foregroundColor = foregroundColor,
                        linkColor = linkColor,
                        isLunar = false,
                        relativeCelestialNorth = solarNorthRelativeAzimuth,
                        relativeMagneticNorth = relativeMagneticNorth,
                        currentTimeMillis = currentTimeMillis,
                        location = location,
                        magneticAzimuth = magneticAzimuth,
                        useGNSS = solarUseGNSS,
                        onUseGNSSChange = { solarUseGNSS = it },
                        useTrueNorth = globalUseTrueNorth,

                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = solarUseTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { solarUseTimezoneSpaFallback = it },
                        isDstActive = globalIsDstActive,

                        isNorthernHemisphere = solarIsNorthernHemisphere,
                        onIsNorthernHemisphereChange = { solarIsNorthernHemisphere = it },
                        nightWarningMsg = solarWarningMsg,
                        textMeasurer = textMeasurer,
                        isLunarAnalogFallbackActive = false,
                        pointPeakAtBody = pointPeakAtSolar,
                        onPointPeakChange = { pointPeakAtSolar = it },
                        useClockTimezoneCorrection = solarUseClockTimezoneCorrection,
                        onUseClockTimezoneCorrectionChange = { solarUseClockTimezoneCorrection = it },
                        useMalleableWatchDial = solarUseMalleableWatchDial,
                        onUseMalleableWatchDialChange = { solarUseMalleableWatchDial = it },
                        userLockedAltitude = sextantLockedData?.altitude
                    )
                } else if (selectedTabIndex == 1) {
                    CelestialToolTab(
                        isDarkTheme = isDarkTheme,
                        foregroundColor = foregroundColor,
                        linkColor = linkColor,
                        isLunar = true,
                        relativeCelestialNorth = lunarNorthRelativeAzimuth,
                        relativeMagneticNorth = relativeMagneticNorth,
                        currentTimeMillis = currentTimeMillis,
                        location = location,
                        magneticAzimuth = magneticAzimuth,
                        useGNSS = lunarUseGNSS,
                        onUseGNSSChange = { lunarUseGNSS = it },
                        useTrueNorth = globalUseTrueNorth,

                        hasLocationPermission = hasLocationPermission,
                        useTimezoneSpaFallback = lunarUseTimezoneSpaFallback,
                        onUseTimezoneSpaFallbackChange = { lunarUseTimezoneSpaFallback = it },
                        isDstActive = globalIsDstActive,

                        isNorthernHemisphere = lunarIsNorthernHemisphere,
                        onIsNorthernHemisphereChange = { lunarIsNorthernHemisphere = it },
                        nightWarningMsg = lunarWarningMsg,
                        textMeasurer = textMeasurer,
                        isLunarAnalogFallbackActive = isLunarAnalogFallbackActive,
                        pointPeakAtBody = pointPeakAtLunar,
                        onPointPeakChange = { pointPeakAtLunar = it },
                        useClockTimezoneCorrection = false, // N/A for moon
                        onUseClockTimezoneCorrectionChange = { solarUseClockTimezoneCorrection = it },
                        useMalleableWatchDial = solarUseMalleableWatchDial,
                        onUseMalleableWatchDialChange = { solarUseMalleableWatchDial = it },
                        userLockedAltitude = sextantLockedData?.altitude
                    )
                }
            } // End of outer tab Box
            } // End of currentScreen check
        } // End of inner padding Column
    } // End of Scaffold
    } // End of ModalNavigationDrawer
}

@Composable
fun CelestialToolTab(
    isDarkTheme: Boolean,
    foregroundColor: Color,
    linkColor: Color,
    isLunar: Boolean,
    relativeCelestialNorth: Float,
    relativeMagneticNorth: Float,
    currentTimeMillis: Long,
    location: android.location.Location?,
    magneticAzimuth: Float,
    useGNSS: Boolean,
    onUseGNSSChange: (Boolean) -> Unit,
    useTrueNorth: Boolean,
    hasLocationPermission: Boolean,
    useTimezoneSpaFallback: Boolean,
    onUseTimezoneSpaFallbackChange: (Boolean) -> Unit,
    isDstActive: Boolean,
    isNorthernHemisphere: Boolean,
    onIsNorthernHemisphereChange: (Boolean) -> Unit,
    nightWarningMsg: String?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    isLunarAnalogFallbackActive: Boolean,
    pointPeakAtBody: Boolean,
    onPointPeakChange: (Boolean) -> Unit,
    useClockTimezoneCorrection: Boolean,
    onUseClockTimezoneCorrectionChange: (Boolean) -> Unit,
    useMalleableWatchDial: Boolean = false,
    onUseMalleableWatchDialChange: (Boolean) -> Unit = {},
    userLockedAltitude: Float? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Human shadow background silhouette
        if (!pointPeakAtBody) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dialCenter = Offset(size.width / 2, size.height * 0.5f)
                val maxAllowedRadiusByHeight = size.height / 7f
                val maxAllowedRadiusByWidth = size.width / 4f
                val rBound = Math.min(maxAllowedRadiusByHeight, maxAllowedRadiusByWidth)

                // Enlarge the triangle by expanding its bound relative to the dial
                val triangleBound = rBound + 40f
                val baseY = dialCenter.y + triangleBound

                clipRect(bottom = baseY) {
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
                    drawPath(path, Color.Gray)
                }
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
            Text(debugText, color = foregroundColor.copy(alpha = 0.5f), fontSize = 10.sp)
        }

        // Central Dial fixed in absolute center
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {

                val dialCenter = Offset(size.width / 2, size.height * 0.5f)

                // Determine a safe radius so nothing clips.
                // The triangle's top vertex is 2*rBound above center, and base is 1*rBound below.
                // Additionally, labels are placed outside the dial.
                val maxAllowedRadiusByHeight = size.height / 7f // (2 * rBound + labels) * 2 should fit in height
                val maxAllowedRadiusByWidth = size.width / 4f   // (base half width = rBound * sqrt(3) ~ 1.7 * rBound) * 2 should fit in width
                val rBound = Math.min(maxAllowedRadiusByHeight, maxAllowedRadiusByWidth)

                val trianglePadding = 10f
                val outerRadius = rBound - trianglePadding

                // Enlarge the triangle so the N and S labels fall completely within it
                val triangleBound = rBound + 40f

                // To keep the triangle centered vertically, its centroid is `dialCenter`.
                // Top vertex: y = dialCenter.y - 2*triangleBound
                // Bottom base: y = dialCenter.y + triangleBound
                // Base half-width: R * sqrt(3)
                val baseHalfWidth = (triangleBound * Math.sqrt(3.0)).toFloat()
                val peakY = dialCenter.y - 2 * triangleBound
                val baseY = dialCenter.y + triangleBound

                val path = Path().apply {
                    moveTo(dialCenter.x, peakY)
                    lineTo(dialCenter.x + baseHalfWidth, baseY)
                    lineTo(dialCenter.x - baseHalfWidth, baseY)
                    close()
                }
                drawPath(path, Color(0xFF00FF00)) // Vivid Green

                    val radius = 28f
                    val centerIconBase = if (pointPeakAtBody) {
                        Offset(dialCenter.x, peakY - radius * 0.5f)
                    } else {
                        Offset(dialCenter.x, baseY + radius * 0.5f)
                    }

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

                    val dialFillColor = if (isDarkTheme) Color.Black else Color.White
                    val dialOutlineColor = if (isDarkTheme) Color.White else Color.Black

                    drawCircle(dialFillColor, radius = outerRadius, center = dialCenter)
                    drawCircle(dialOutlineColor, radius = outerRadius, center = dialCenter, style = Stroke(width = 4f))

                    val textStyleCompassN = TextStyle(color = Color.Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val textStyleCompassS = TextStyle(color = Color.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val textStyleCelestial = TextStyle(color = foregroundColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    rotate(relativeMagneticNorth, dialCenter) {
                        // North Hand
                        drawLine(
                            color = Color.Red,
                            start = dialCenter,
                            end = Offset(dialCenter.x, dialCenter.y - outerRadius),
                            strokeWidth = 10f
                        )
                        // Arrow for North
                        val arrowPath = Path().apply {
                            moveTo(dialCenter.x, dialCenter.y - outerRadius - 10f)
                            lineTo(dialCenter.x - 10f, dialCenter.y - outerRadius + 10f)
                            lineTo(dialCenter.x + 10f, dialCenter.y - outerRadius + 10f)
                            close()
                        }
                        drawPath(arrowPath, Color.Red)

                        // South Hand
                        drawLine(
                            color = Color.Blue,
                            start = dialCenter,
                            end = Offset(dialCenter.x, dialCenter.y + outerRadius),
                            strokeWidth = 10f
                        )
                        // Arrow for South
                        val arrowPathS = Path().apply {
                            moveTo(dialCenter.x, dialCenter.y + outerRadius + 10f)
                            lineTo(dialCenter.x - 10f, dialCenter.y + outerRadius - 10f)
                            lineTo(dialCenter.x + 10f, dialCenter.y + outerRadius - 10f)
                            close()
                        }
                        drawPath(arrowPathS, Color.Blue)
                    }

                    if (!isLunarAnalogFallbackActive) {
                        rotate(relativeCelestialNorth, dialCenter) {
                            drawLine(
                                color = foregroundColor,
                                start = dialCenter,
                                end = Offset(dialCenter.x, dialCenter.y - outerRadius),
                                strokeWidth = 10f
                            )
                            // Arrow for Celestial North
                            val arrowPath = Path().apply {
                                moveTo(dialCenter.x, dialCenter.y - outerRadius - 10f)
                                lineTo(dialCenter.x - 10f, dialCenter.y - outerRadius + 10f)
                                lineTo(dialCenter.x + 10f, dialCenter.y - outerRadius + 10f)
                                close()
                            }
                            drawPath(arrowPath, foregroundColor)
                        }
                    }

                    // Draw Upright Labels independently from rotation state
                    val labelRadius = outerRadius + 25f

                    // Magnetic North Upright Label
                    val magNorthRad = Math.toRadians((relativeMagneticNorth - 90.0).toDouble()).toFloat()
                    val magNLoc = Offset(
                        dialCenter.x + labelRadius * cos(magNorthRad),
                        dialCenter.y + labelRadius * sin(magNorthRad)
                    )
                    val labelNOffset = textMeasurer.measure("N", textStyleCompassN)
                    drawText(textMeasurer, "N", Offset(magNLoc.x - labelNOffset.size.width/2f, magNLoc.y - labelNOffset.size.height/2f), style = textStyleCompassN)

                    // Magnetic South Upright Label
                    val magSouthRad = Math.toRadians((relativeMagneticNorth + 90.0).toDouble()).toFloat()
                    val magSLoc = Offset(
                        dialCenter.x + labelRadius * cos(magSouthRad),
                        dialCenter.y + labelRadius * sin(magSouthRad)
                    )
                    val labelSOffset = textMeasurer.measure("S", textStyleCompassS)
                    drawText(textMeasurer, "S", Offset(magSLoc.x - labelSOffset.size.width/2f, magSLoc.y - labelSOffset.size.height/2f), style = textStyleCompassS)

                    // Celestial North Upright Label
                    if (!isLunarAnalogFallbackActive) {
                        val celNorthRad = Math.toRadians((relativeCelestialNorth - 90.0).toDouble()).toFloat()
                        val celNLoc = Offset(
                            dialCenter.x + labelRadius * cos(celNorthRad),
                            dialCenter.y + labelRadius * sin(celNorthRad)
                        )
                        val labelCelNOffset = textMeasurer.measure("N", textStyleCelestial)
                        drawText(textMeasurer, "N", Offset(celNLoc.x - labelCelNOffset.size.width/2f, celNLoc.y - labelCelNOffset.size.height/2f), style = textStyleCelestial)
                    }

                    val clockRadius = outerRadius * 0.7f
                    drawCircle(dialFillColor, radius = clockRadius, center = dialCenter)
                    drawCircle(dialOutlineColor, radius = clockRadius, center = dialCenter, style = Stroke(width = 2f))

                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentTimeMillis
                    val localH = cal.get(Calendar.HOUR)
                    val localM = cal.get(Calendar.MINUTE)
                    val minuteAngleInside = Math.toRadians(-90.0 + localM * 6).toFloat()

                    if (useMalleableWatchDial && !isLunar && userLockedAltitude != null) {
                        val sunData = CelestialMathUtils.calculateSunPositionData(currentTimeMillis)
                        val deducedLat = LatitudeDeducer.deduceLatitude(
                            userLockedAltitude,
                            sunData.declination,
                            sunData.hourAngle,
                            isNorthernHemisphere
                        )

                        val textStyle = TextStyle(color = foregroundColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                        if (deducedLat != null) {
                            val currentAzimuth = SunPositionCalculator.calculateSolarAzimuth(deducedLat, sunData.estimatedLongitude, currentTimeMillis)
                            val dialRotation = if (pointPeakAtBody) {
                                (360f - currentAzimuth).toFloat()
                            } else {
                                (180f - currentAzimuth).toFloat()
                            }

                            rotate(dialRotation, dialCenter) {
                                for (h24 in 0..23) {
                                    val hourCal = Calendar.getInstance()
                                    hourCal.timeInMillis = currentTimeMillis
                                    hourCal.set(Calendar.HOUR_OF_DAY, h24)
                                    hourCal.set(Calendar.MINUTE, 0)
                                    hourCal.set(Calendar.SECOND, 0)

                                    val hourMillis = hourCal.timeInMillis

                                    if (!SunPositionCalculator.isNight(deducedLat, sunData.estimatedLongitude, hourMillis)) {
                                        val hourAzimuth = SunPositionCalculator.calculateSolarAzimuth(deducedLat, sunData.estimatedLongitude, hourMillis)
                                        val hourRad = Math.toRadians(hourAzimuth - 90.0).toFloat()
                                        val h12 = if (h24 == 0) 12 else if (h24 > 12) h24 - 12 else h24

                                        val markStart = Offset(
                                            dialCenter.x + clockRadius * 0.85f * cos(hourRad),
                                            dialCenter.y + clockRadius * 0.85f * sin(hourRad)
                                        )
                                        val markEnd = Offset(
                                            dialCenter.x + clockRadius * 0.95f * cos(hourRad),
                                            dialCenter.y + clockRadius * 0.95f * sin(hourRad)
                                        )
                                        drawLine(color = foregroundColor, start = markStart, end = markEnd, strokeWidth = 3f)

                                        if (h24 % 3 == 0) {
                                            val labelText = h12.toString()
                                            val labelMeas = textMeasurer.measure(labelText, textStyle)
                                            val textCenter = Offset(
                                                dialCenter.x + clockRadius * 0.7f * cos(hourRad),
                                                dialCenter.y + clockRadius * 0.7f * sin(hourRad)
                                            )
                                            drawText(
                                                textMeasurer,
                                                labelText,
                                                Offset(textCenter.x - labelMeas.size.width/2f, textCenter.y - labelMeas.size.height/2f),
                                                style = textStyle
                                            )
                                        }
                                    }
                                }

                                val currentAzimuthRad = Math.toRadians(currentAzimuth - 90.0).toFloat()
                                drawLine(
                                    color = foregroundColor,
                                    start = dialCenter,
                                    end = Offset(
                                        dialCenter.x + clockRadius * 0.6f * cos(currentAzimuthRad),
                                        dialCenter.y + clockRadius * 0.6f * sin(currentAzimuthRad)
                                    ),
                                    strokeWidth = 10f
                                )

                                drawLine(
                                    color = foregroundColor,
                                    start = dialCenter,
                                    end = Offset(
                                        dialCenter.x + clockRadius * 0.8f * cos(minuteAngleInside),
                                        dialCenter.y + clockRadius * 0.8f * sin(minuteAngleInside)
                                    ),
                                    strokeWidth = 6f
                                )
                            }
                        } else {
                            val msg = "No Valid Lat"
                            val textErr = textMeasurer.measure(msg, textStyle)
                            drawText(textMeasurer, msg, Offset(dialCenter.x - textErr.size.width/2f, dialCenter.y - textErr.size.height/2f), style = textStyle)
                        }
                    } else {
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
                            val textStyle = TextStyle(color = foregroundColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val offset12 = textMeasurer.measure("12", textStyle)
                            val offset3 = textMeasurer.measure("3", textStyle)
                            val offset6 = textMeasurer.measure("6", textStyle)
                            val offset9 = textMeasurer.measure("9", textStyle)

                            drawText(textMeasurer, "12", dialCenter + Offset(-offset12.size.width/2f, -clockRadius + 8f), style = textStyle)
                            drawText(textMeasurer, "6", dialCenter + Offset(-offset6.size.width/2f, clockRadius - offset6.size.height - 8f), style = textStyle)
                            drawText(textMeasurer, "3", dialCenter + Offset(clockRadius - offset3.size.width - 8f, -offset3.size.height/2f), style = textStyle)
                            drawText(textMeasurer, "9", dialCenter + Offset(-clockRadius + 8f, -offset9.size.height/2f), style = textStyle)

                            if (isDstActive && (!useGNSS || location == null) && !useTimezoneSpaFallback) {
                                val stdHourAngleInside = Math.toRadians(-90.0 + (stdH * 30 + stdM * 0.5)).toFloat()
                                val localHourAngleInside = Math.toRadians(-90.0 + (localH * 30 + localM * 0.5)).toFloat()

                                drawLine(
                                    color = foregroundColor,
                                    start = dialCenter,
                                    end = Offset(
                                        dialCenter.x + clockRadius * 0.6f * cos(localHourAngleInside),
                                        dialCenter.y + clockRadius * 0.6f * sin(localHourAngleInside)
                                    ),
                                    strokeWidth = 10f
                                )

                                drawLine(
                                    color = foregroundColor,
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
                                    color = foregroundColor,
                                    start = dialCenter,
                                    end = Offset(
                                        dialCenter.x + clockRadius * 0.6f * cos(hourAngleInside),
                                        dialCenter.y + clockRadius * 0.6f * sin(hourAngleInside)
                                    ),
                                    strokeWidth = 10f
                                )
                            }

                            drawLine(
                                color = foregroundColor,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Point triangle peak at: ", color = foregroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Box(modifier = Modifier.wrapContentSize(Alignment.Center)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { expanded = true }
                    ) {
                        Text(
                            if (pointPeakAtBody) celestialName else "your shadow",
                            color = linkColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Text(" ▼", color = foregroundColor)
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
                            uncheckedColor = foregroundColor,
                            checkmarkColor = Color.White,
                            disabledUncheckedColor = Color.Gray,
                            disabledCheckedColor = Color.Gray
                        )
                    )
                    Text("Use GNSS for ${if (isLunar) "Lunar" else "Solar"} North", color = if (gnssEnabled) foregroundColor else Color.Gray)
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
                            uncheckedColor = foregroundColor,
                            checkmarkColor = Color.White,
                            disabledUncheckedColor = Color.Gray,
                            disabledCheckedColor = Color.Gray
                        )
                    )
                    Text("Use Timezone-Estimated SPA Fallback", color = if (isFallbackActive) foregroundColor else Color.Gray, fontSize = 14.sp)
                }



                if (!isLunar) {
                    val isTzCorrectEnabled = isFallbackActive && !useTimezoneSpaFallback
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = isTzCorrectEnabled) { onUseClockTimezoneCorrectionChange(!useClockTimezoneCorrection) }
                    ) {
                        Checkbox(
                            checked = useClockTimezoneCorrection && isTzCorrectEnabled,
                            onCheckedChange = null,
                            enabled = isTzCorrectEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = foregroundColor,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("Apply Timezone & EoT to Clock Bisect", color = if (isTzCorrectEnabled) foregroundColor else Color.Gray, fontSize = 14.sp)
                    }

                    val isMalleableDialEnabled = isFallbackActive && !useTimezoneSpaFallback
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = isMalleableDialEnabled) { onUseMalleableWatchDialChange(!useMalleableWatchDial) }
                    ) {
                        Checkbox(
                            checked = useMalleableWatchDial && isMalleableDialEnabled,
                            onCheckedChange = null,
                            enabled = isMalleableDialEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color.Blue,
                                uncheckedColor = foregroundColor,
                                checkmarkColor = Color.White,
                                disabledUncheckedColor = Color.Gray,
                                disabledCheckedColor = Color.Gray
                            )
                        )
                        Text("Use Malleable Watch Dial (Approximation Study)", color = if (isMalleableDialEnabled) foregroundColor else Color.Gray, fontSize = 14.sp)
                    }

                    if (useMalleableWatchDial && isMalleableDialEnabled) {
                        if (userLockedAltitude == null) {
                            Text(
                                "Please open the 'Sextant' menu tool to measure your latitude.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                            )
                        } else {
                            Text(
                                "Locked Altitude: ${String.format("%.1f°", userLockedAltitude)} (Set via Sextant)",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                    Text("Hemisphere: ", color = if (isFallbackActive) foregroundColor else Color.Gray, fontSize = 14.sp)
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

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChanged: (AppTheme) -> Unit,
    foregroundColor: Color,
    useTrueNorth: Boolean,
    onUseTrueNorthChange: (Boolean) -> Unit,
    dstMode: DstMode,
    onDstModeChange: (DstMode) -> Unit,
    hasLocationPermission: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Theme", style = MaterialTheme.typography.titleLarge, color = foregroundColor)
        Spacer(modifier = Modifier.height(16.dp))

        AppTheme.values().forEach { theme ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeChanged(theme) }
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = currentTheme == theme,
                    onClick = { onThemeChanged(theme) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color.Blue, unselectedColor = foregroundColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val title = when (theme) {
                    AppTheme.LIGHT -> "Light"
                    AppTheme.DARK -> "Dark"
                    AppTheme.SYSTEM -> "System Default"
                    AppTheme.AUTO_SUNSET -> "Auto (Dark at night)"
                }
                Text(title, color = foregroundColor)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Global Settings", style = MaterialTheme.typography.titleLarge, color = foregroundColor)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(enabled = hasLocationPermission) { onUseTrueNorthChange(!useTrueNorth) }.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = useTrueNorth && hasLocationPermission,
                onCheckedChange = null,
                enabled = hasLocationPermission,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Blue,
                    uncheckedColor = foregroundColor,
                    checkmarkColor = Color.White,
                    disabledUncheckedColor = Color.Gray,
                    disabledCheckedColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Adjust Magnetic to True North", color = if (hasLocationPermission) foregroundColor else Color.Gray)
                if (!hasLocationPermission) {
                    Text("Requires GNSS permission.", color = Color.Red, fontSize = 12.sp)
                }
            }
        }

        Text("DST (Daylight Saving Time)", style = MaterialTheme.typography.titleMedium, color = foregroundColor, modifier = Modifier.padding(top = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        DstMode.values().forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDstModeChange(mode) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = dstMode == mode,
                    onClick = { onDstModeChange(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color.Blue, unselectedColor = foregroundColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val title = when (mode) {
                    DstMode.AUTO_SYSTEM -> "Auto (System Timezone)"
                    DstMode.ALWAYS_ON -> "Always On"
                    DstMode.ALWAYS_OFF -> "Always Off"
                }
                Text(title, color = foregroundColor)
            }
        }
    }
}

@Composable
fun SextantScreen(
    foregroundColor: Color,
    currentTimeMillis: Long,
    isNorthernHemisphere: Boolean,
    onIsNorthernHemisphereChange: (Boolean) -> Unit,
    livePitch: Float,
    liveRoll: Float,
    lockedData: SextantLockedData?,
    onLockedDataChange: (SextantLockedData?) -> Unit,
    useCompassForFullLocation: Boolean,
    onUseCompassForFullLocationChange: (Boolean) -> Unit,
    magneticAzimuth: Float,
    useTrueNorth: Boolean,
    location: android.location.Location?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val sunData = CelestialMathUtils.calculateSunPositionData(currentTimeMillis)
        val (liveAlt, _, isReverseLandscape) = InclinationHelper.calculateAltitudeAndOrientation(livePitch, liveRoll)


        val interactiveControlsData = @Composable { modifier: Modifier ->
            // Pre-calculate live values
            var liveTrueAzimuth = magneticAzimuth
            if (useTrueNorth && location != null) {
                val declination = SensorHelper.getDeclination(location.latitude, location.longitude, location.altitude, currentTimeMillis)
                liveTrueAzimuth = (magneticAzimuth + declination + 360f) % 360f
            }
            val liveSunAzimuth = if (isReverseLandscape) {
                (liveTrueAzimuth + 90f) % 360f
            } else {
                (liveTrueAzimuth - 90f + 360f) % 360f
            }
            val liveFullLoc = if (useCompassForFullLocation) LocationDeducer.deduceFullLocation(liveAlt, liveSunAzimuth, sunData.declination, currentTimeMillis) else null
            val liveDeducedLat = if (!useCompassForFullLocation) LatitudeDeducer.deduceLatitude(liveAlt, sunData.declination, sunData.hourAngle, isNorthernHemisphere) else null

            val displayAltitude = lockedData?.altitude ?: liveAlt
            val displayDeclination = lockedData?.declination ?: sunData.declination

            Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Phone Sextant Tool", style = MaterialTheme.typography.titleLarge, color = foregroundColor)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Measured Altitude: ${String.format("%.1f°", displayAltitude)}", color = foregroundColor, fontWeight = FontWeight.Bold)
                    Text("Sun Declination: ${String.format("%.2f°", displayDeclination)}", color = foregroundColor, fontWeight = FontWeight.Bold)
                }

                if (lockedData != null) {
                    if (useCompassForFullLocation) {
                        if (lockedData.deducedLatitude != null && lockedData.assumedOrDeducedLongitude != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Deduced Lat: ${String.format("%.2f°", lockedData.deducedLatitude)}", color = Color.Green, fontWeight = FontWeight.Bold)
                                Text("Deduced Lon: ${String.format("%.2f°", lockedData.assumedOrDeducedLongitude)}", color = Color.Green, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Could not solve spherical math for this attitude.", color = Color.Red)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (lockedData.deducedLatitude != null) {
                                Text("Deduced Lat: ${String.format("%.2f°", lockedData.deducedLatitude)}", color = Color.Green, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Could not deduce Lat.", color = Color.Red)
                            }
                            Text("Assumed Lon (Timezone): ${String.format("%.2f°", lockedData.assumedOrDeducedLongitude)}", color = foregroundColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!useCompassForFullLocation) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hemisphere: ", color = foregroundColor, fontSize = 16.sp)
                        Button(
                            onClick = { onIsNorthernHemisphereChange(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isNorthernHemisphere) Color.Blue else Color.Gray),
                            modifier = Modifier.height(36.dp)
                        ) { Text("N") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onIsNorthernHemisphereChange(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (!isNorthernHemisphere) Color.Blue else Color.Gray),
                            modifier = Modifier.height(36.dp)
                        ) { Text("S") }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onUseCompassForFullLocationChange(!useCompassForFullLocation) }) {
                    Checkbox(
                        checked = useCompassForFullLocation,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Color.Blue, uncheckedColor = foregroundColor, checkmarkColor = Color.White)
                    )
                    Text("Use compass direction to deduce full Lat & Lon", color = foregroundColor, fontSize = 14.sp)
                }
                if (useCompassForFullLocation) {
                    Text("Warning: Check compass accuracy against the sun before calculating.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }

                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (lockedData == null) {
                            onLockedDataChange(SextantLockedData(
                                altitude = liveAlt,
                                declination = sunData.declination.toFloat(),
                                deducedLatitude = if (useCompassForFullLocation) liveFullLoc?.first?.toFloat() else liveDeducedLat?.toFloat(),
                                assumedOrDeducedLongitude = if (useCompassForFullLocation) liveFullLoc?.second?.toFloat() else sunData.estimatedLongitude.toFloat()
                            ))
                        } else {
                            onLockedDataChange(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).height(56.dp)
                ) {
                    Text(if (lockedData == null) "Lock Measurement" else "Retake Measurement", fontSize = 18.sp)
                }
            }
        }

        val staticInstructionsIllustration = @Composable { modifier: Modifier ->
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    "Hold phone horizontally above your head.",
                    color = foregroundColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp),
                    fontWeight = FontWeight.Bold
                )

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Left 1/3: Profile View
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                            Text("1. Profile", fontSize = 10.sp, color = foregroundColor, modifier = Modifier.padding(bottom = 4.dp))
                            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val scale = canvasHeight / 150f
                                val cx = canvasWidth / 2 + 10f * scale
                                val cy = canvasHeight / 2 + 10f * scale

                                // Sun
                                val sunCx = cx + 30f * scale
                                val sunCy = cy - 50f * scale
                                drawCircle(color = Color(0xFFFFD700), radius = 10f * scale, center = Offset(sunCx, sunCy))

                                // Phone blocking sun rays
                                val phoneCx = cx - 20f * scale
                                val phoneCy = cy - 40f * scale
                                rotate(degrees = 30f, pivot = Offset(phoneCx, phoneCy)) {
                                    drawRect(color = Color.Gray, topLeft = Offset(phoneCx - 15f * scale, phoneCy - 2f * scale), size = androidx.compose.ui.geometry.Size(30f * scale, 4f * scale))
                                }

                                // Sun Rays stopped by phone
                                drawLine(color = Color(0xFFFFD700), start = Offset(sunCx, sunCy), end = Offset(phoneCx + 5f * scale, phoneCy - 15f * scale), strokeWidth = 1f * scale)
                                drawLine(color = Color(0xFFFFD700), start = Offset(sunCx, sunCy), end = Offset(phoneCx + 15f * scale, phoneCy - 5f * scale), strokeWidth = 1f * scale)

                                // Person
                                val bodyPath = androidx.compose.ui.graphics.Path().apply {
                                    addOval(androidx.compose.ui.geometry.Rect(left = cx - 8f * scale, top = cy - 30f * scale, right = cx + 8f * scale, bottom = cy - 14f * scale))
                                    moveTo(cx, cy - 14f * scale)
                                    lineTo(cx, cy + 20f * scale)
                                    lineTo(cx - 5f * scale, cy + 50f * scale)
                                    moveTo(cx, cy + 20f * scale)
                                    lineTo(cx + 5f * scale, cy + 50f * scale)
                                }
                                drawPath(path = bodyPath, color = foregroundColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * scale))

                                // Arm holding phone
                                drawLine(color = foregroundColor, start = Offset(cx, cy - 10f * scale), end = Offset(phoneCx, phoneCy), strokeWidth = 3f * scale)

                                // Ground
                                drawLine(color = foregroundColor.copy(alpha = 0.3f), start = Offset(0f, cy + 50f * scale), end = Offset(canvasWidth, cy + 50f * scale), strokeWidth = 2f)
                            }
                            Text("Tilt to align with rays", fontSize = 9.sp, color = foregroundColor, textAlign = TextAlign.Center, lineHeight = 10.sp)
                        }
                    }

                    // Middle 1/3: View Upwards
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                            Text("2. View Up", fontSize = 10.sp, color = foregroundColor, modifier = Modifier.padding(bottom = 4.dp))
                            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val scale = canvasHeight / 150f
                                val cx = canvasWidth / 2
                                val cy = canvasHeight / 2

                                // Screen View (looking up from behind)
                                drawRect(
                                    color = Color.LightGray,
                                    topLeft = Offset(cx - 25f * scale, cy - 30f * scale),
                                    size = androidx.compose.ui.geometry.Size(50f * scale, 30f * scale)
                                )
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(cx - 23f * scale, cy - 28f * scale),
                                    size = androidx.compose.ui.geometry.Size(46f * scale, 26f * scale),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                                )

                                // Arms coming from bottom
                                drawLine(color = foregroundColor, start = Offset(cx - 30f * scale, cy + 40f * scale), end = Offset(cx - 25f * scale, cy), strokeWidth = 6f * scale)
                                drawLine(color = foregroundColor, start = Offset(cx + 30f * scale, cy + 40f * scale), end = Offset(cx + 25f * scale, cy), strokeWidth = 6f * scale)

                                // Hands
                                drawCircle(color = foregroundColor, radius = 5f * scale, center = Offset(cx - 25f * scale, cy))
                                drawCircle(color = foregroundColor, radius = 5f * scale, center = Offset(cx + 25f * scale, cy))
                            }
                            Text("Lock altitude\nreading on screen", fontSize = 9.sp, color = foregroundColor, textAlign = TextAlign.Center, lineHeight = 10.sp)
                        }
                    }

                    // Right 1/3: View Downwards
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                            Text("3. View Down", fontSize = 10.sp, color = foregroundColor, modifier = Modifier.padding(bottom = 4.dp))
                            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val scale = canvasHeight / 150f
                                val cx = canvasWidth / 2
                                val cy = canvasHeight / 2

                                // Ground Area
                                drawRect(
                                    color = foregroundColor.copy(alpha = 0.1f),
                                    topLeft = Offset(0f, 0f),
                                    size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight)
                                )

                                // Phone shadow on ground
                                drawRect(
                                    color = foregroundColor.copy(alpha = 0.4f),
                                    topLeft = Offset(cx - 20f * scale, cy - 5f * scale),
                                    size = androidx.compose.ui.geometry.Size(40f * scale, 10f * scale)
                                )

                                // Hands shadows
                                drawCircle(color = foregroundColor.copy(alpha = 0.4f), radius = 6f * scale, center = Offset(cx - 25f * scale, cy))
                                drawCircle(color = foregroundColor.copy(alpha = 0.4f), radius = 6f * scale, center = Offset(cx + 25f * scale, cy))

                                // Arms shadows extending down
                                drawLine(color = foregroundColor.copy(alpha = 0.4f), start = Offset(cx - 25f * scale, cy), end = Offset(cx - 30f * scale, cy + 40f * scale), strokeWidth = 8f * scale)
                                drawLine(color = foregroundColor.copy(alpha = 0.4f), start = Offset(cx + 25f * scale, cy), end = Offset(cx + 30f * scale, cy + 40f * scale), strokeWidth = 8f * scale)
                            }
                            Text("Minimize phone\nshadow to an edge", fontSize = 9.sp, color = foregroundColor, textAlign = TextAlign.Center, lineHeight = 10.sp)
                        }
                    }
                }
            }
        }

        // Top 60%: Rotated Interactive Controls
        BoxWithConstraints(
            modifier = Modifier.weight(0.6f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val rotatedWidth = maxHeight
            val rotatedHeight = maxWidth

            Box(
                modifier = Modifier
                    .graphicsLayer { rotationZ = if (isReverseLandscape) -90f else 90f }
                    .requiredSize(width = rotatedWidth, height = rotatedHeight)
                    .padding(16.dp)
            ) {
                interactiveControlsData(Modifier.fillMaxSize())
            }
        }

        // Bottom 40%: Static Portrait Instructions
        Box(modifier = Modifier.weight(0.4f).fillMaxWidth().padding(16.dp)) {
            staticInstructionsIllustration(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun WatchStudyScreen(
    foregroundColor: Color,
    currentTimeMillis: Long,
    isNorthernHemisphere: Boolean
) {
    var selectedParallel by remember { mutableStateOf(40f) }

    // We want to calculate the true sun azimuth for hours 6 AM to 6 PM
    // at the given latitude. Since we don't have longitude, we'll assume
    // standard meridian noon and calculate hour angles manually.

    // The sun's declination for today
    val sunData = CelestialMathUtils.calculateSunPositionData(currentTimeMillis)
    val declination = sunData.declination

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Watch Approximation Study", style = MaterialTheme.typography.titleLarge, color = foregroundColor)
        Text("Comparing 30° rigid watch dials vs True Sun Azimuth", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Latitude: ${String.format("%.0f°", selectedParallel)}", color = foregroundColor, modifier = Modifier.width(100.dp))
            Slider(
                value = selectedParallel,
                onValueChange = { selectedParallel = it },
                valueRange = 0f..90f,
                steps = 8, // 10, 20, ... 80
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize(0.9f)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val radius = minOf(canvasWidth, canvasHeight) / 2f
                val cx = canvasWidth / 2f
                val cy = canvasHeight / 2f

                // Draw Base Watch Dial (Outer circle)
                drawCircle(color = foregroundColor, radius = radius, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))

                // Text Paint
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val trueTextPaint = android.graphics.Paint().apply {
                    color = if (foregroundColor == Color.White) android.graphics.Color.CYAN else android.graphics.Color.BLUE
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }

                // Draw the standard uniform watch face (12 hours = 30 deg each)
                for (hour in 1..12) {
                    val standardAngle = (hour * 30f) - 90f // 12 is at top (-90 deg)
                    val standardRad = Math.toRadians(standardAngle.toDouble())
                    val standardX = cx + (radius * 0.75f) * kotlin.math.cos(standardRad).toFloat()
                    val standardY = cy + (radius * 0.75f) * kotlin.math.sin(standardRad).toFloat()

                    // Draw tick mark
                    val tickOuterX = cx + radius * kotlin.math.cos(standardRad).toFloat()
                    val tickOuterY = cy + radius * kotlin.math.sin(standardRad).toFloat()
                    val tickInnerX = cx + (radius * 0.9f) * kotlin.math.cos(standardRad).toFloat()
                    val tickInnerY = cy + (radius * 0.9f) * kotlin.math.sin(standardRad).toFloat()
                    drawLine(color = Color.Gray, start = Offset(tickInnerX, tickInnerY), end = Offset(tickOuterX, tickOuterY), strokeWidth = 2f)

                    drawContext.canvas.nativeCanvas.drawText(hour.toString(), standardX, standardY + 15f, textPaint)
                }

                // Draw True Sun Azimuth "Soft" Dial
                // From 6 AM (Hour Angle = -90) to 6 PM (Hour Angle = +90)
                // Note: This is an approximation for standard time, assuming 12:00 is solar noon.

                for (hour in 6..18) {
                    val displayHour = if (hour > 12) hour - 12 else hour
                    val hourAngle = (hour - 12) * 15.0 // Degrees

                    // Spherical trig to find azimuth
                    val latRad = Math.toRadians(selectedParallel.toDouble() * if (isNorthernHemisphere) 1.0 else -1.0)
                    val decRad = Math.toRadians(declination)
                    val haRad = Math.toRadians(hourAngle)

                    // Altitude
                    val sinAlt = kotlin.math.sin(latRad) * kotlin.math.sin(decRad) + kotlin.math.cos(latRad) * kotlin.math.cos(decRad) * kotlin.math.cos(haRad)
                    val altRad = kotlin.math.asin(sinAlt)

                    // Azimuth
                    val cosAz = (kotlin.math.sin(decRad) - kotlin.math.sin(latRad) * kotlin.math.sin(altRad)) / (kotlin.math.cos(latRad) * kotlin.math.cos(altRad))
                    var trueAzimuth = Math.toDegrees(kotlin.math.acos(cosAz.coerceIn(-1.0, 1.0)))

                    if (hour > 12) {
                        trueAzimuth = 360.0 - trueAzimuth
                    }

                    // Map True Azimuth to dial drawing angle (Azimuth 0 is North. On watch, 12 is South typically for the sun in N.H.)
                    // If we treat 12 as South (Azimuth 180), then 6 AM is East (Az 90), 6 PM is West (Az 270)
                    // Let's draw it such that Azimuth 180 is at the top (-90 degrees on Canvas).
                    // Canvas Angle = TrueAzimuth - 180 - 90 = TrueAzimuth - 270

                    val sunCanvasAngle = if (isNorthernHemisphere) {
                        trueAzimuth - 270.0
                    } else {
                        // In Southern Hemisphere, sun is North at noon. So Azimuth 0 is at the top.
                        // Canvas Angle = TrueAzimuth - 90
                        trueAzimuth - 90.0
                    }

                    val sunRad = Math.toRadians(sunCanvasAngle)

                    // Draw "Soft" Marker
                    val trueX = cx + (radius * 0.5f) * kotlin.math.cos(sunRad).toFloat()
                    val trueY = cy + (radius * 0.5f) * kotlin.math.sin(sunRad).toFloat()

                    // Draw line connecting standard hour to true hour to show the stretch/warp
                    val standardAngle = (displayHour * 30f) - 90f
                    val standardRad = Math.toRadians(standardAngle.toDouble())
                    val standardX = cx + (radius * 0.65f) * kotlin.math.cos(standardRad).toFloat()
                    val standardY = cy + (radius * 0.65f) * kotlin.math.sin(standardRad).toFloat()

                    val warpColor = if (foregroundColor == Color.White) Color(0x6600FFFF) else Color(0x660000FF)
                    drawLine(color = warpColor, start = Offset(standardX, standardY), end = Offset(trueX, trueY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

                    drawContext.canvas.nativeCanvas.drawText(displayHour.toString(), trueX, trueY + 15f, trueTextPaint)
                }

                // Draw compass directions
                val compassPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val southPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLUE
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                if (isNorthernHemisphere) {
                    drawContext.canvas.nativeCanvas.drawText("S (12)", cx, cy - radius + 40f, compassPaint)
                    drawContext.canvas.nativeCanvas.drawText("E", cx + radius - 30f, cy + 10f, compassPaint)
                    drawContext.canvas.nativeCanvas.drawText("W", cx - radius + 30f, cy + 10f, compassPaint)
                } else {
                    drawContext.canvas.nativeCanvas.drawText("N (12)", cx, cy - radius + 40f, southPaint)
                    drawContext.canvas.nativeCanvas.drawText("E", cx + radius - 30f, cy + 10f, southPaint)
                    drawContext.canvas.nativeCanvas.drawText("W", cx - radius + 30f, cy + 10f, southPaint)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "The gray outer numbers show a perfect 30° watch dial. The colored inner numbers show where the sun ACTUALLY is at that hour for the selected latitude based on today's declination.",
            color = foregroundColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
