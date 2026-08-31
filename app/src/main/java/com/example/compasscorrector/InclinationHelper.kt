package com.example.compasscorrector

import kotlin.math.abs
import kotlin.math.sqrt

object InclinationHelper {

    fun calculateAltitudeAndOrientation(pitch: Float, roll: Float): Triple<Float, Boolean, Boolean> {
        val pitchRad = Math.toRadians(pitch.toDouble())
        val rollRad = Math.toRadians(roll.toDouble())

        val z = Math.cos(pitchRad) * Math.cos(rollRad)
        val tiltRad = Math.acos(z)

        var altitude = Math.toDegrees(tiltRad).toFloat()

        if (altitude > 90f) {
            altitude = 180f - altitude
        }

        // Determine if phone is held Landscape or Reverse Landscape.
        // In Android sensor coordinates (when phone is flat on table, portrait):
        // Pitch: rotation around X axis (tilting top edge up makes pitch negative)
        // Roll: rotation around Y axis (tilting right edge up makes roll negative)
        // If the user holds the phone sideways (landscape) to look at it, the long edge is horizontal.
        // If they hold it in standard Landscape (bottom edge points right), the Roll is roughly -90 or +90 depending on upright screen.
        // Actually, if held in landscape with the screen facing the user:
        // - Standard landscape (top edge points left): Roll is negative (-90)
        // - Reverse landscape (top edge points right): Roll is positive (+90)
        // Let's use the roll to detect this orientation.

        val isReverseLandscape = roll > 0f

        return Triple(altitude, false, isReverseLandscape)
    }
}
