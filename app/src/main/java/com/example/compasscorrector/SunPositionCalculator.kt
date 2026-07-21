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

    // Calculates fallback north using the clock/bisect method.
    // The user points the *bottom* of the phone (6 o'clock on a normal clock) at the sun.
    // So the sun is always at relative azimuth 180 degrees to the device heading.
    // In Northern Hemisphere:
    // With hour hand pointed at sun, South is halfway between hour hand and 12.
    // Since 6 o'clock is pointed at sun, we imagine the hour hand is at the sun.
    // Wait, the standard method says: point the hour hand at the sun.
    // We are pointing the BOTTOM of the phone at the sun.
    // This means relative to the phone, the sun is at 180 degrees.
    // Let current time hour be H (24h format).
    // On a 12h clock face, the hour hand angle is (H % 12) * 30 + (M/60)*30 degrees from 12 o'clock (0 deg).
    fun calculateFallbackNorthAzimuth(currentTimeMillis: Long, isNorthernHemisphere: Boolean): Double {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTimeMillis
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)

        val hourAngle = ((h % 12) + m / 60.0) * 30.0 // 0 to 360 where 0 is 12 o'clock

        // If we pointed the hour hand at the sun, the device heading would be such that hourAngle points at sun.
        // But the user points the BOTTOM of the phone (180 deg) at the sun.
        // So the sun's direction relative to the phone is 180.
        // We want to find where North is relative to the phone's current heading.

        // Watch method logic:
        // Northern hemisphere: South is halfway between hour hand and 12.
        // Since hour hand is at 180 (because bottom is pointed at sun), South is halfway between 180 and 0 (which is 90 or 270 depending on time).
        // Let's do this mathematically.
        // Sun is at absolute azimuth S.
        // Device is pointing at absolute azimuth D.
        // Bottom of device points at D + 180.
        // User aligns bottom of device to Sun: D + 180 = S -> D = S - 180.
        // So device top points away from the sun.

        // Watch method tells us where North is based on the sun.
        // Let's find Sun absolute azimuth using watch method:
        // In North Hemi: Sun is roughly at S = (hourAngle - 0)*2 ??? No.
        // At 12:00, Sun is South (180 deg).
        // At 6:00 (AM), Sun is East (90 deg).
        // At 18:00, Sun is West (270 deg).
        // So S = (hour / 24) * 360 - ... Wait.
        // Sun moves 15 deg per hour.
        // S = 180 + (H + M/60 - 12) * 15.
        // Let's use this simple assumption.
        val decimalHour = h + m / 60.0
        val sunAzimuthApprox = if (isNorthernHemisphere) {
             (180.0 + (decimalHour - 12.0) * 15.0) % 360.0
        } else {
             (360.0 + (decimalHour - 12.0) * 15.0) % 360.0
        }

        // If user points bottom of phone at sun (azimuth sunAzimuthApprox)
        // Then device top is pointing at sunAzimuthApprox - 180.
        // We want to draw an arrow pointing to North (0 deg absolute).
        // So relative to device top, North is at: 0 - (sunAzimuthApprox - 180) = 180 - sunAzimuthApprox.

        val relativeNorth = (180.0 - sunAzimuthApprox + 360.0) % 360.0
        return relativeNorth
    }
}
