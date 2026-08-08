package com.example.compasscorrector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LatitudeDeducerTest {

    @Test
    fun testLatitudeDeduction() {
        val latNorth = LatitudeDeducer.deduceLatitude(40f, 0.0, 0.0, true)
        assertNotNull(latNorth)
        assertEquals(50.0, latNorth!!, 0.01)

        val latSouth = LatitudeDeducer.deduceLatitude(40f, 0.0, 0.0, false)
        assertNotNull(latSouth)
        assertEquals(-50.0, latSouth!!, 0.01)

        val latSummer = LatitudeDeducer.deduceLatitude(60f, 23.44, 0.0, true)
        assertNotNull(latSummer)
        assertEquals(53.44, latSummer!!, 0.01)
    }
}
