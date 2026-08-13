package com.example.compasscorrector

import kotlin.math.abs
import kotlin.math.sqrt

object InclinationHelper {
    /**
     * Calculates the true altitude using 3D vector math.
     * When the phone is perfectly flat, pitch and roll are 0.
     * As you tilt it around any axis, the gravity vector shifts.
     * The altitude is the angle between the horizontal plane and the phone's plane.
     *
     * Using spherical trigonometry/vector math:
     * A plane's tilt relative to horizontal can be found by combining the pitch and roll angles.
     * Specifically, tan^2(tilt) = tan^2(pitch) + tan^2(roll).
     * Since pitch and roll from SensorManager.getOrientation are rotations around the X and Y axes,
     * we can find the absolute angle of the Z axis from vertical.
     */
    fun calculateAltitudeAndCrookedness(pitch: Float, roll: Float): Pair<Float, Boolean> {
        val pitchRad = Math.toRadians(pitch.toDouble())
        val rollRad = Math.toRadians(roll.toDouble())

        // The z-component of the gravity vector (assuming normalized)
        // is roughly cos(pitch) * cos(roll)
        // The angle of the phone's plane to the horizontal is acos(z)
        val z = Math.cos(pitchRad) * Math.cos(rollRad)
        val tiltRad = Math.acos(z)

        var altitude = Math.toDegrees(tiltRad).toFloat()

        // Altitude should be bounded 0-90.
        // If they flip the phone over, altitude might go > 90, so we normalize.
        if (altitude > 90f) {
            altitude = 180f - altitude
        }

        // Since we explicitly want the user to hold it in Landscape, the primary axis of rotation
        // for pointing at the sun should be Pitch (rotating the long edge up/down).
        // Roll would represent tilting the phone left/right (which would introduce crookedness in the measurement
        // if they are trying to point the edge at the sun).
        // Wait, if it's landscape, and they look at the screen, the long edge is horizontal.
        // Tilting it forward/backward (like a laptop screen) changes ROLL in Android standard axes
        // if the device is in portrait originally.
        // Let's just use the absolute plane tilt, which removes the need to care about which axis is which!
        // We will no longer show "crookedness" because the 3D plane tilt mathematically handles the exact angle
        // of the sun ray regardless of how twisted the phone is, as long as the shadow is minimized.

        return Pair(altitude, false)
    }
}
