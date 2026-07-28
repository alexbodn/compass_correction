package com.example.compasscorrector

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object MoonPositionCalculator {

    // Calculate lunar azimuth based on Meeus' Astronomical Algorithms.
    fun calculateLunarAzimuth(
        latitude: Double,
        longitude: Double,
        currentTimeMillis: Long
    ): Double {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = currentTimeMillis

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // Decimal hours
        val decimalHour = hour + minute / 60.0 + second / 3600.0

        // Julian Date
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5 + decimalHour / 24.0

        // Days since J2000.0
        val d = jd - 2451545.0

        // Ecliptic coordinates of the moon
        val l = 218.316 + 13.176396 * d
        val m = 134.963 + 13.064993 * d
        val f = 93.272 + 13.229350 * d

        val eclipticLongitude = l + 6.289 * sin(Math.toRadians(m))
        val eclipticLatitude = 5.128 * sin(Math.toRadians(f))

        // Obliquity of the ecliptic
        val obliquity = 23.439 - 0.00000036 * d

        // Equatorial coordinates
        val radEclipticLong = Math.toRadians(eclipticLongitude)
        val radEclipticLat = Math.toRadians(eclipticLatitude)
        val radObliquity = Math.toRadians(obliquity)

        val rightAscension = Math.toDegrees(atan2(
            sin(radEclipticLong) * cos(radObliquity) - tan(radEclipticLat) * sin(radObliquity),
            cos(radEclipticLong)
        ))

        val declination = Math.toDegrees(asin(
            sin(radEclipticLat) * cos(radObliquity) + cos(radEclipticLat) * sin(radObliquity) * sin(radEclipticLong)
        ))

        // Local Sidereal Time
        val lst = (100.46 + 0.985647 * d + longitude + 15 * decimalHour) % 360.0

        // Hour Angle
        val hourAngle = (lst - rightAscension + 360.0) % 360.0

        // Azimuth
        val radLat = Math.toRadians(latitude)
        val radDec = Math.toRadians(declination)
        val radHA = Math.toRadians(hourAngle)

        val azimuth = Math.toDegrees(atan2(
            sin(radHA),
            cos(radHA) * sin(radLat) - tan(radDec) * cos(radLat)
        ))

        return (azimuth + 180.0 + 360.0) % 360.0
    }

    // Calculates fallback relative north using a Timezone-estimated algorithm.
    // This estimates longitude based on the timezone standard meridian, and uses a generic latitude.
    fun calculateTimezoneFallbackNorthAzimuth(currentTimeMillis: Long, isNorthernHemisphere: Boolean): Double {
        val tzOffsetMillis = TimeZone.getDefault().rawOffset.toLong()
        val tzOffsetHours = tzOffsetMillis / 3600000.0

        // Estimate longitude: 15 degrees per hour of timezone offset.
        val estLongitude = tzOffsetHours * 15.0

        // Estimate latitude: Generic mid-latitude (45 degrees) based on hemisphere selection.
        val estLatitude = if (isNorthernHemisphere) 45.0 else -45.0

        // Calculate true astronomical lunar azimuth.
        val lunarAbsoluteAzimuth = calculateLunarAzimuth(estLatitude, estLongitude, currentTimeMillis)

        // The device points its bottom (180 deg) at the moon.
        // Device heading = lunarAbsoluteAzimuth - 180
        // Relative angle to North (0) compared to device top:
        val relativeNorth = (180.0 - lunarAbsoluteAzimuth + 360.0) % 360.0

        return relativeNorth
    }
}
