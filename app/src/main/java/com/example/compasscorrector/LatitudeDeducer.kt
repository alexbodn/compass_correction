package com.example.compasscorrector

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object LatitudeDeducer {
    fun deduceLatitude(
        altitudeDegrees: Float,
        declinationDegrees: Double,
        hourAngleDegrees: Double,
        isNorthernHemisphere: Boolean
    ): Double? {
        val altRad = Math.toRadians(altitudeDegrees.toDouble())
        val decRad = Math.toRadians(declinationDegrees)
        val haRad = Math.toRadians(hourAngleDegrees)

        val A = sin(decRad)
        val B = cos(decRad) * cos(haRad)
        val C = sin(altRad)

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

        val validSols = mutableListOf<Double>()
        if (latDeg1 in -90.0..90.0) validSols.add(latDeg1)
        if (latDeg2 in -90.0..90.0) validSols.add(latDeg2)

        if (validSols.isEmpty()) return null
        if (validSols.size == 1) return validSols[0]

        val northSol = validSols.maxOrNull()!!
        val southSol = validSols.minOrNull()!!

        return if (isNorthernHemisphere) northSol else southSol
    }
}
