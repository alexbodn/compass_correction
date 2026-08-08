package com.example.compasscorrector

import org.junit.Assert.assertEquals
import org.junit.Test

class InclinationHelperTest {

    @Test
    fun testInclinationCalculation() {
        val (alt1, crooked1) = InclinationHelper.calculateAltitudeAndCrookedness(45f, 5f)
        assertEquals(45f, alt1, 0.01f)
        assertEquals(false, crooked1)

        val (alt2, crooked2) = InclinationHelper.calculateAltitudeAndCrookedness(10f, 60f)
        assertEquals(60f, alt2, 0.01f)
        assertEquals(false, crooked2)

        val (alt3, crooked3) = InclinationHelper.calculateAltitudeAndCrookedness(45f, 45f)
        assertEquals(45f, alt3, 0.01f)
        assertEquals(true, crooked3)

        val (alt4, crooked4) = InclinationHelper.calculateAltitudeAndCrookedness(100f, 0f)
        assertEquals(80f, alt4, 0.01f)
        assertEquals(false, crooked4)
    }
}
