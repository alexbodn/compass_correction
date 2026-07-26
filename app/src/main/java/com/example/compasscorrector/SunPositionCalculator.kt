package com.example.compasscorrector

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object SunPositionCalculator {

    // Calculate solar azimuth given latitude, longitude, and current time.
    // Follows SPA (Solar Position Algorithm) simplifications.
    // Returns azimuth in degrees (0 = North, 90 = East, 180 = South, 270 = West)
    fun calculateSolarAzimuth(
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

        // Julian Century
        val t = (jd - 2451545.0) / 36525.0

        // Geometric Mean Longitude of the Sun
        var l0 = 280.46646 + t * (36000.76983 + t * 0.0003032)
        l0 = l0 % 360.0

        // Geometric Mean Anomaly of the Sun
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)

        // Eccentricity of Earth's Orbit
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        // Sun Equation of Center
        val c = sin(Math.toRadians(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(Math.toRadians(2 * m)) * (0.019993 - 0.000101 * t) +
                sin(Math.toRadians(3 * m)) * 0.000289

        // Sun True Longitude
        val trueLong = l0 + c

        // Sun Apparent Longitude
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Mean Obliquity of the Ecliptic
        val epsilon0 = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 - t * (46.8150 + t * (0.00059 - t * 0.001813)) / 3600.0

        // Obliquity Correction
        val epsilon = epsilon0 + 0.00256 * cos(Math.toRadians(omega))

        // Sun Declination
        val delta = Math.toDegrees(asin(sin(Math.toRadians(epsilon)) * sin(Math.toRadians(lambda))))

        // Equation of Time
        val y = tan(Math.toRadians(epsilon / 2.0)).pow(2)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * Math.toRadians(l0)) -
            2 * e * sin(Math.toRadians(m)) +
            4 * e * y * sin(Math.toRadians(m)) * cos(2 * Math.toRadians(l0)) -
            0.5 * y * y * sin(4 * Math.toRadians(l0)) -
            1.25 * e * e * sin(2 * Math.toRadians(m))
        )

        // True Solar Time
        val tst = (decimalHour * 60.0 + eqTime + 4 * longitude) % 1440.0
        val tstHours = tst / 60.0

        // Solar Hour Angle
        val ha = if (tstHours < 12.0) {
            tstHours / 24.0 * 360.0 - 180.0
        } else {
            tstHours / 24.0 * 360.0 - 180.0
        }

        // Solar Zenith Angle
        val theta = Math.toDegrees(
            acos(
                sin(Math.toRadians(latitude)) * sin(Math.toRadians(delta)) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(delta)) * cos(Math.toRadians(ha))
            )
        )

        // Solar Azimuth Angle
        val azCos = (sin(Math.toRadians(latitude)) * cos(Math.toRadians(theta)) - sin(Math.toRadians(delta))) /
                    (cos(Math.toRadians(latitude)) * sin(Math.toRadians(theta)))

        var az = Math.toDegrees(acos(azCos.coerceIn(-1.0, 1.0)))

        if (ha > 0.0) {
            az = (az + 180.0) % 360.0
        } else {
            az = (540.0 - az) % 360.0
        }

        return az
    }

    // Rough check if it's night time given sunset/sunrise approximations.
    // If exact calculation is needed, we could compute sunset/sunrise from SPA.
    // For simplicity, we can use Zenith angle > 90 to mean sun is below horizon.
    fun isNight(
        latitude: Double,
        longitude: Double,
        currentTimeMillis: Long
    ): Boolean {
        // We will calculate zenith angle again.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = currentTimeMillis

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        val decimalHour = hour + minute / 60.0 + second / 3600.0
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5 + decimalHour / 24.0
        val t = (jd - 2451545.0) / 36525.0
        var l0 = 280.46646 + t * (36000.76983 + t * 0.0003032)
        l0 = l0 % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(Math.toRadians(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(Math.toRadians(2 * m)) * (0.019993 - 0.000101 * t) +
                sin(Math.toRadians(3 * m)) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val lambda = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val epsilon0 = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 - t * (46.8150 + t * (0.00059 - t * 0.001813)) / 3600.0
        val epsilon = epsilon0 + 0.00256 * cos(Math.toRadians(omega))
        val delta = Math.toDegrees(asin(sin(Math.toRadians(epsilon)) * sin(Math.toRadians(lambda))))
        val y = tan(Math.toRadians(epsilon / 2.0)).pow(2)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * Math.toRadians(l0)) -
            2 * e * sin(Math.toRadians(m)) +
            4 * e * y * sin(Math.toRadians(m)) * cos(2 * Math.toRadians(l0)) -
            0.5 * y * y * sin(4 * Math.toRadians(l0)) -
            1.25 * e * e * sin(2 * Math.toRadians(m))
        )
        val tst = (decimalHour * 60.0 + eqTime + 4 * longitude) % 1440.0
        val tstHours = tst / 60.0
        val ha = tstHours / 24.0 * 360.0 - 180.0

        val theta = Math.toDegrees(
            acos(
                sin(Math.toRadians(latitude)) * sin(Math.toRadians(delta)) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(delta)) * cos(Math.toRadians(ha))
            )
        )
        // If zenith is > 90, the sun is below the horizon (night)
        return theta > 90.0
    }

    // Fallback night check when location is unknown
    fun isNightFallback(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // Assume night between 18:00 and 06:00
        return hour >= 18 || hour < 6
    }

    // Calculates fallback relative north using a Timezone-estimated SPA.
    // This estimates longitude based on the timezone standard meridian, and uses a generic latitude.
    fun calculateTimezoneSpaFallbackNorthAzimuth(currentTimeMillis: Long, isNorthernHemisphere: Boolean): Double {
        val tzOffsetMillis = TimeZone.getDefault().rawOffset.toLong()
        val tzOffsetHours = tzOffsetMillis / 3600000.0

        // Estimate longitude: 15 degrees per hour of timezone offset.
        val estLongitude = tzOffsetHours * 15.0

        // Estimate latitude: Generic mid-latitude (45 degrees) based on hemisphere selection.
        val estLatitude = if (isNorthernHemisphere) 45.0 else -45.0

        // Calculate true astronomical solar azimuth using the exact SPA.
        val solarAbsoluteAzimuth = calculateSolarAzimuth(estLatitude, estLongitude, currentTimeMillis)

        // As before, the device points its bottom (180 deg) at the sun.
        // Device heading = solarAbsoluteAzimuth - 180
        // Relative angle to North (0) compared to device top:
        val relativeNorth = (180.0 - solarAbsoluteAzimuth + 360.0) % 360.0

        return relativeNorth
    }

    // Calculates fallback relative north using the analog watch bisect method.
    // The user points the *bottom* of the phone at the sun (since the sun icon is at the bottom).
    // This is equivalent to pointing the hour hand of the *upside down* clock at the sun.
    fun calculateFallbackNorthAzimuth(currentTimeMillis: Long, isNorthernHemisphere: Boolean, isDstActive: Boolean): Double {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTimeMillis

        // If DST is active, use the standard time hour for calculation.
        if (isDstActive) {
            cal.add(Calendar.HOUR_OF_DAY, -1)
        }

        val h12 = cal.get(Calendar.HOUR)
        val m = cal.get(Calendar.MINUTE)

        // Bisect method:
        // Point hour hand at sun. The sun is at the bottom of the phone (180 degrees).
        // The hour hand angle relative to 12 o'clock is:
        val angleFrom12 = (h12 * 30.0 + m * 0.5) % 360.0

        // The smaller angle between the hour hand and 12 o'clock must be bisected.
        // The hour hand is at 180 on our fixed phone reference frame.
        // Therefore, 12 o'clock is at (180 - angleFrom12).
        // The bisector of the *smaller* angle between them is South (in Northern Hemisphere)
        // or North (in Southern Hemisphere).

        val bisectorOfSmallerAngle = if (angleFrom12 < 180.0) {
            // PM (after noon)
            (180.0 - angleFrom12 / 2.0) % 360.0
        } else {
            // AM (before noon)
            (360.0 - angleFrom12 / 2.0) % 360.0
        }

        val relativeNorth = if (isNorthernHemisphere) {
            // In Northern Hemisphere, bisecting the smaller angle gives South.
            // So North is opposite (add 180).
            (bisectorOfSmallerAngle + 180.0) % 360.0
        } else {
            // In the Southern Hemisphere, bisecting the smaller angle gives North.
            bisectorOfSmallerAngle
        }

        return relativeNorth
    }
}
