package com.example.compasscorrector

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object LocationDeducer {

    /**
     * Deduces the full location (Latitude AND Longitude) using the measured sun altitude,
     * the measured true sun azimuth (via compass), and the universal UTC time.
     *
     * Formula 1: sin(Alt) = sin(Lat)*sin(Dec) + cos(Lat)*cos(Dec)*cos(HA)
     * Formula 2: cos(Az) = (sin(Lat)*sin(Alt) - sin(Dec)) / (cos(Lat)*cos(Alt))
     *
     * From Formula 2, we can solve directly for Latitude without hemisphere ambiguity
     * IF the azimuth is accurate!
     *
     * cos(Az) * cos(Lat) * cos(Alt) = sin(Lat) * sin(Alt) - sin(Dec)
     * sin(Dec) = sin(Lat) * sin(Alt) - cos(Lat) * cos(Alt) * cos(Az)
     *
     * Let x = Lat.
     * sin(x)*sin(Alt) - cos(x)*cos(Alt)*cos(Az) = sin(Dec)
     *
     * Let A = sin(Alt)
     * Let B = -cos(Alt)*cos(Az)
     * Let C = sin(Dec)
     *
     * A*sin(x) + B*cos(x) = C
     * R = sqrt(A^2 + B^2)
     * sin(x + alpha) = C/R  where alpha = atan2(B, A)
     * x1 = arcsin(C/R) - alpha
     * x2 = PI - arcsin(C/R) - alpha
     *
     * Once we have Latitude, we can use Formula 1 to find the Hour Angle (HA).
     * cos(HA) = (sin(Alt) - sin(Lat)*sin(Dec)) / (cos(Lat)*cos(Dec))
     *
     * The sign of HA (east or west of meridian) comes from the Azimuth.
     * If Azimuth < 180, HA is negative (morning). If Azimuth > 180, HA is positive (afternoon).
     *
     * Once we have HA, we calculate Longitude using the True Solar Time relation:
     * HA (in degrees) = (TST_hours / 24) * 360 - 180
     * TST_mins = DecimalHourUTC*60 + EquationOfTime + 4*Longitude
     */
    fun deduceFullLocation(
        altitudeDegrees: Float,
        azimuthDegrees: Float,
        declinationDegrees: Double,
        currentTimeMillis: Long
    ): Pair<Double, Double>? {
        val altRad = Math.toRadians(altitudeDegrees.toDouble())
        val decRad = Math.toRadians(declinationDegrees)
        val azRad = Math.toRadians(azimuthDegrees.toDouble())

        // Solve for Latitude
        val A = sin(altRad)
        val B = -cos(altRad) * cos(azRad)
        val C = sin(decRad)

        val R = Math.sqrt(A * A + B * B)

        if (C / R > 1.0 || C / R < -1.0) {
            return null
        }

        val alpha = Math.atan2(B, A)
        val arcsinCR = asin(C / R)

        var latRad1 = arcsinCR - alpha
        while (latRad1 > PI) latRad1 -= 2 * PI
        while (latRad1 < -PI) latRad1 += 2 * PI

        var latRad2 = PI - arcsinCR - alpha
        while (latRad2 > PI) latRad2 -= 2 * PI
        while (latRad2 < -PI) latRad2 += 2 * PI

        val latDeg1 = Math.toDegrees(latRad1)
        val latDeg2 = Math.toDegrees(latRad2)

        val validLats = mutableListOf<Double>()
        if (latDeg1 in -90.0..90.0) validLats.add(latDeg1)
        if (latDeg2 in -90.0..90.0) validLats.add(latDeg2)

        if (validLats.isEmpty()) return null

        // Use the first valid latitude. In most cases, these formulas yield one valid real-world latitude.
        // If there are two, we pick the most reasonable one (or just the first, as spherical trig usually filters it tightly here).
        val deducedLat = validLats[0]

        // Solve for Hour Angle
        val latRad = Math.toRadians(deducedLat)
        val cosHa = (sin(altRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))

        if (cosHa > 1.0 || cosHa < -1.0) return null

        var haRad = acos(cosHa)
        // If Azimuth is < 180 (morning), sun is east, HA is negative
        if (azimuthDegrees < 180f) {
            haRad = -haRad
        }
        val haDegrees = Math.toDegrees(haRad)

        // Find Longitude
        // Get UTC time and EqTime
        val sunData = CelestialMathUtils.calculateSunPositionData(currentTimeMillis)
        // From CelestialMathUtils:
        // HA = TST_hours / 24 * 360 - 180
        // TST_hours = (DecimalHourUTC*60 + EqTime + 4*Longitude) / 60
        // HA = (DecimalHourUTC*60 + EqTime + 4*Longitude) / (60 * 24) * 360 - 180
        // HA = (DecimalHourUTC*60 + EqTime + 4*Longitude) / 1440 * 360 - 180
        // HA + 180 = (DecimalHourUTC*60 + EqTime + 4*Longitude) / 4
        // (HA + 180) * 4 = DecimalHourUTC*60 + EqTime + 4*Longitude
        // 4*Longitude = (HA + 180) * 4 - (DecimalHourUTC*60 + EqTime)
        // Longitude = (HA + 180) - (DecimalHourUTC*60 + EqTime) / 4

        // Wait, calculateSunPositionData is already calculating HA internally using assumed longitude.
        // Let's extract EqTime and DecimalHour instead of duplicating logic.
        val calendarUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendarUtc.timeInMillis = currentTimeMillis
        val hour = calendarUtc.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendarUtc.get(java.util.Calendar.MINUTE)
        val second = calendarUtc.get(java.util.Calendar.SECOND)
        val decimalHourUTC = hour + minute / 60.0 + second / 3600.0

        // We can approximate EqTime using the existing function.
        // A clean way is to see what HA it calculated for Longitude 0.
        // HA_zero = TST_hours_zero / 24 * 360 - 180
        // TST_mins_zero = DecimalHourUTC*60 + EqTime
        // EqTime = TST_mins_zero - DecimalHourUTC*60

        // Let's do a hack to get EqTime cleanly:
        // We know HA calculated by CelestialMathUtils uses `estLongitude`.
        val estLon = sunData.estimatedLongitude
        val haEst = sunData.hourAngle
        // haEst = (DecimalHourUTC*60 + EqTime + 4*estLon) / 4 - 180
        // haEst + 180 = (DecimalHourUTC*60 + EqTime)/4 + estLon
        // (DecimalHourUTC*60 + EqTime)/4 = haEst + 180 - estLon
        val timeComponent = haEst + 180.0 - estLon

        // Now substitute into our deduced HA equation:
        // haDegrees + 180 = timeComponent + deducedLongitude
        var deducedLongitude = haDegrees + 180.0 - timeComponent

        // Normalize Longitude
        while (deducedLongitude > 180.0) deducedLongitude -= 360.0
        while (deducedLongitude < -180.0) deducedLongitude += 360.0

        return Pair(deducedLat, deducedLongitude)
    }
}
