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

    // Calculates fallback relative north using the watch/bisect method.
    // The user points the *bottom* of the phone at the sun.
    // This is mathematically equivalent to holding an upside-down clock where 12 is at the bottom,
    // which automatically points the 12 o'clock mark at the sun.
    fun calculateFallbackNorthAzimuth(currentTimeMillis: Long, isNorthernHemisphere: Boolean, isDstActive: Boolean): Double {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTimeMillis

        // If DST is active, subtract 1 hour to get true standard local time for the sun.
        if (isDstActive) {
            cal.add(Calendar.HOUR_OF_DAY, -1)
        }

        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)

        // The angle of the hour hand on a 12h clock face (0 degrees is straight UP relative to phone top).
        // Because the clock on screen is upside down, 12 is at the bottom (180 degrees).
        // Wait, the hour hand on the *on-screen upside-down clock* has an angle relative to phone top.
        // 12 is at 180 deg. The hour hand moves clockwise from there.
        // Let's calculate the physical angle of the hour hand relative to the phone top (0 deg).
        // 12:00 = 180 deg
        // 3:00 = 270 deg
        // 6:00 = 0 deg
        // 9:00 = 90 deg
        val hourAngleFrom12 = ((h % 12) + m / 60.0) * 30.0 // 0 to 360
        val physicalHourAngle = (180.0 + hourAngleFrom12) % 360.0 // Relative to phone top

        // Watch method states:
        // Northern Hemisphere: Point the hour hand at the sun. South is halfway between hour hand and 12.
        // But the user points the *bottom* (12 on our upside-down clock, i.e., 180 degrees) at the sun.
        // So we are pointing the 12 mark at the sun, not the hour hand.
        // Watch method alternative: Point 12 at the sun. South is halfway between hour hand and 12. (This is exactly what we are doing!)
        // Halfway between the physical hour angle and 180 degrees.

        // Let's find the bisector angle between physicalHourAngle and 180.
        // To find the shortest path bisector, we average them, but need to be careful about wrapping.
        var a1 = physicalHourAngle
        var a2 = 180.0

        // Calculate difference
        var diff = a1 - a2
        if (diff < -180.0) diff += 360.0
        if (diff > 180.0) diff -= 360.0

        // Bisector is a2 + diff / 2
        var bisect = (a2 + diff / 2.0) % 360.0
        if (bisect < 0) bisect += 360.0

        // In Northern hemisphere, this bisect line points South. North is opposite.
        // In Southern hemisphere, this bisect line points North.
        val relativeNorth = if (isNorthernHemisphere) {
            (bisect + 180.0) % 360.0
        } else {
            bisect
        }

        return relativeNorth
    }
}
