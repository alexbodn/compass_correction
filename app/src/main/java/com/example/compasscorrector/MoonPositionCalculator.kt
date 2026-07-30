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

    // Calculates if the Moon is below the horizon based on altitude.
    fun isMoonBelowHorizon(
        latitude: Double,
        longitude: Double,
        currentTimeMillis: Long
    ): Boolean {
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

        val radLat = Math.toRadians(latitude)
        val radDec = Math.toRadians(declination)
        val radHA = Math.toRadians(hourAngle)

        // Calculate altitude
        val altitude = Math.toDegrees(asin(
            sin(radLat) * sin(radDec) + cos(radLat) * cos(radDec) * cos(radHA)
        ))

        // If altitude is less than 0, it's below the horizon
        return altitude < 0.0
    }

    // Calculate the lunar phase using Meeus' Astronomical Algorithms.
    // Returns a value between 0.0 and 1.0 representing the illuminated fraction.
    // Near 0 is new moon, near 1 is full moon.
    fun calculateLunarPhase(currentTimeMillis: Long): Double {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = currentTimeMillis

        val year = calendar.get(Calendar.YEAR)
        var month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        // Decimal hours
        val decimalHour = hour + minute / 60.0 + second / 3600.0

        // Adjusted year/month for Julian Date
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }

        // Julian Date
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5 + decimalHour / 24.0

        // Days since J2000.0
        val d = jd - 2451545.0

        // Sun's mean anomaly
        val sunMeanAnomaly = 357.529 + 0.98560028 * d

        // Moon's mean anomaly
        val moonMeanAnomaly = 134.963 + 13.064993 * d

        // Moon's mean distance from ascending node
        val dTerm = 297.850 + 12.190749 * d // Mean elongation of the Moon

        // Phase angle calculation
        val radSunM = Math.toRadians(sunMeanAnomaly)
        val radMoonM = Math.toRadians(moonMeanAnomaly)
        val radD = Math.toRadians(dTerm)

        val phaseAngle = 180.0 - dTerm - 6.289 * sin(radMoonM) + 2.100 * sin(radSunM) - 1.274 * sin(2 * radD - radMoonM) - 0.658 * sin(2 * radD) - 0.214 * sin(2 * radMoonM) - 0.11 * sin(radD)

        // Calculate illuminated fraction
        var fraction = (1 + cos(Math.toRadians(phaseAngle))) / 2.0
        return fraction.coerceIn(0.0, 1.0)
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

    // Calculates if the Moon is below the horizon using Timezone-estimated algorithm.
    fun isMoonBelowHorizonFallback(currentTimeMillis: Long, isNorthernHemisphere: Boolean): Boolean {
        val tzOffsetMillis = TimeZone.getDefault().rawOffset.toLong()
        val tzOffsetHours = tzOffsetMillis / 3600000.0

        // Estimate longitude: 15 degrees per hour of timezone offset.
        val estLongitude = tzOffsetHours * 15.0

        // Estimate latitude: Generic mid-latitude (45 degrees) based on hemisphere selection.
        val estLatitude = if (isNorthernHemisphere) 45.0 else -45.0

        return isMoonBelowHorizon(estLatitude, estLongitude, currentTimeMillis)
    }
}
