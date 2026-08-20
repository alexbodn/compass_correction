package com.example.compasscorrector

import org.junit.Test
import org.junit.Assert.*
import java.util.TimeZone

class LocationDeducerTest {
    @Test
    fun testFullLocationDeduction() {
        val altitudeDegrees = 51.4f
        val shadowAzimuth = 301.4f
        val sunAzimuth = (shadowAzimuth - 180f + 360f) % 360f
        val declinationDegrees = 13.06

        // Let's guess the time. It is 10:01 in some time zone.
        // Let's iterate over possible hours of UTC to see which one gives ~42.83 longitude
        // For the sake of the test, let's just use the deduced HA logic.

        val altRad = Math.toRadians(altitudeDegrees.toDouble())
        val decRad = Math.toRadians(declinationDegrees)
        val azRad = Math.toRadians(sunAzimuth.toDouble())

        val A = Math.sin(altRad)
        val B = -Math.cos(altRad) * Math.cos(azRad)
        val C = Math.sin(decRad)

        val R = Math.sqrt(A * A + B * B)
        val alpha = Math.atan2(B, A)
        val arcsinCR = Math.asin(C / R)

        var latRad1 = arcsinCR - alpha
        while (latRad1 > Math.PI) latRad1 -= 2 * Math.PI
        while (latRad1 < -Math.PI) latRad1 += 2 * Math.PI

        var latRad2 = Math.PI - arcsinCR - alpha
        while (latRad2 > Math.PI) latRad2 -= 2 * Math.PI
        while (latRad2 < -Math.PI) latRad2 += 2 * Math.PI

        val latDeg1 = Math.toDegrees(latRad1)
        val latDeg2 = Math.toDegrees(latRad2)

        println("Sun Azimuth: " + sunAzimuth)
        println("Lat1: " + latDeg1)
        println("Lat2: " + latDeg2)

        val deducedLat = if (latDeg1 in -90.0..90.0) latDeg1 else latDeg2

        val latRad = Math.toRadians(deducedLat)
        val cosHa = (Math.sin(altRad) - Math.sin(latRad) * Math.sin(decRad)) / (Math.cos(latRad) * Math.cos(decRad))

        var haRad = Math.acos(cosHa)
        if (sunAzimuth < 180f) {
            haRad = -haRad
        }
        val haDegrees = Math.toDegrees(haRad)
        println("cosHa: " + cosHa + " haDegrees: " + haDegrees)

        // Wait, the user is saying: "Compared to a 19th century explorer... analyze the sun move on the sky... at different parallels (let's say every 10°)... create a better time to direction approximation?"
        // "let's do the experiment in a new page, forked from the sun, and connected to the menu."
        // "maybe we can deduce the parallel by the sun inclination using the phone sensor?"
    }
}
