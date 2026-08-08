package com.example.compasscorrector

import kotlin.math.abs
import kotlin.math.max

object InclinationHelper {
    fun calculateAltitudeAndCrookedness(pitch: Float, roll: Float): Pair<Float, Boolean> {
        val absPitch = abs(pitch)
        val absRoll = abs(roll)

        val primaryTilt = max(absPitch, absRoll)
        val secondaryTilt = if (primaryTilt == absPitch) absRoll else absPitch

        var altitude = primaryTilt
        if (altitude > 90f) {
            altitude = 180f - altitude
        }

        val isCrooked = secondaryTilt > 15f

        return Pair(altitude, isCrooked)
    }
}
