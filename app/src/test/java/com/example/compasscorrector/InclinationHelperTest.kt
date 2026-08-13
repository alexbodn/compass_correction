package com.example.compasscorrector

import org.junit.Assert.assertEquals
import org.junit.Test

class InclinationHelperTest {

    @Test
    fun testInclinationCalculation() {
        // Flat phone -> 0 altitude
        val (alt1, _) = InclinationHelper.calculateAltitudeAndCrookedness(0f, 0f)
        assertEquals(0f, alt1, 0.01f)

        // Tilted exactly 45 deg on one axis
        val (alt2, _) = InclinationHelper.calculateAltitudeAndCrookedness(45f, 0f)
        assertEquals(45f, alt2, 0.01f)

        val (alt3, _) = InclinationHelper.calculateAltitudeAndCrookedness(0f, 45f)
        assertEquals(45f, alt3, 0.01f)

        // Tilted on both axes
        // If pitch=45, roll=45:
        // z = cos(45)*cos(45) = 0.707 * 0.707 = 0.5
        // acos(0.5) = 60 degrees.
        val (alt4, _) = InclinationHelper.calculateAltitudeAndCrookedness(45f, 45f)
        assertEquals(60f, alt4, 0.01f)
    }
}
