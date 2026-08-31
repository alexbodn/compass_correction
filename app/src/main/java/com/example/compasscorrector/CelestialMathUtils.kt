package com.example.compasscorrector

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object CelestialMathUtils {

    data class SunPositionData(
        val declination: Double,
        val hourAngle: Double,
        val estimatedLongitude: Double
    )

    fun calculateSunPositionData(currentTimeMillis: Long): SunPositionData {
        val calendarUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendarUtc.timeInMillis = currentTimeMillis

        val year = calendarUtc.get(Calendar.YEAR)
        val month = calendarUtc.get(Calendar.MONTH) + 1
        val day = calendarUtc.get(Calendar.DAY_OF_MONTH)
        val hour = calendarUtc.get(Calendar.HOUR_OF_DAY)
        val minute = calendarUtc.get(Calendar.MINUTE)
        val second = calendarUtc.get(Calendar.SECOND)

        val decimalHour = hour + minute / 60.0 + second / 3600.0

        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5 + decimalHour / 24.0

        val t = (jd - 2451545.0) / 36525.0
        var l0 = 280.46646 + t * (36000.76983 + t * 0.0003032)
        l0 = l0 % 360.0
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eOrbit = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        val c = sin(Math.toRadians(meanAnomaly)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(Math.toRadians(2 * meanAnomaly)) * (0.019993 - 0.000101 * t) +
                sin(Math.toRadians(3 * meanAnomaly)) * 0.000289
        val trueLong = l0 + c

        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        val epsilon0 = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 - t * (46.8150 + t * (0.00059 - t * 0.001813)) / 3600.0
        val epsilon = epsilon0 + 0.00256 * cos(Math.toRadians(omega))

        val delta = Math.toDegrees(asin(sin(Math.toRadians(epsilon)) * sin(Math.toRadians(lambda))))

        val y = tan(Math.toRadians(epsilon / 2.0)).pow(2)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * Math.toRadians(l0)) -
            2 * eOrbit * sin(Math.toRadians(meanAnomaly)) +
            4 * eOrbit * y * sin(Math.toRadians(meanAnomaly)) * cos(2 * Math.toRadians(l0)) -
            0.5 * y * y * sin(4 * Math.toRadians(l0)) -
            1.25 * eOrbit * eOrbit * sin(2 * Math.toRadians(meanAnomaly))
        )

        val tzOffsetMillis = TimeZone.getDefault().rawOffset.toLong()
        val tzOffsetHours = tzOffsetMillis / 3600000.0
        val estLongitude = tzOffsetHours * 15.0

        val tst = (decimalHour * 60.0 + eqTime + 4 * estLongitude) % 1440.0
        val tstHours = tst / 60.0
        val ha = tstHours / 24.0 * 360.0 - 180.0

        return SunPositionData(declination = delta, hourAngle = ha, estimatedLongitude = estLongitude)
    }
}
