package com.example.compasscorrector

import java.util.TimeZone

object TimezoneLatitudeLookup {
    private val timezoneLatitudes = mapOf(
        // North America
        "America/New_York" to 40.7,
        "America/Chicago" to 41.8,
        "America/Denver" to 39.7,
        "America/Los_Angeles" to 34.0,
        "America/Anchorage" to 61.2,
        "America/Toronto" to 43.7,
        "America/Vancouver" to 49.2,
        "America/Mexico_City" to 19.4,

        // South America
        "America/Sao_Paulo" to -23.5,
        "America/Buenos_Aires" to -34.6,
        "America/Santiago" to -33.4,
        "America/Bogota" to 4.7,

        // Europe
        "Europe/London" to 51.5,
        "Europe/Paris" to 48.8,
        "Europe/Berlin" to 52.5,
        "Europe/Rome" to 41.9,
        "Europe/Madrid" to 40.4,
        "Europe/Moscow" to 55.7,
        "Europe/Kiev" to 50.4,
        "Europe/Athens" to 37.9,

        // Middle East
        "Asia/Jerusalem" to 31.7,
        "Asia/Dubai" to 25.2,
        "Asia/Riyadh" to 24.7,
        "Asia/Tehran" to 35.6,

        // Asia
        "Asia/Tokyo" to 35.6,
        "Asia/Shanghai" to 31.2,
        "Asia/Seoul" to 37.5,
        "Asia/Kolkata" to 28.6,
        "Asia/Bangkok" to 13.7,
        "Asia/Singapore" to 1.3,
        "Asia/Jakarta" to -6.2,

        // Oceania
        "Australia/Sydney" to -33.8,
        "Australia/Melbourne" to -37.8,
        "Australia/Perth" to -31.9,
        "Pacific/Auckland" to -36.8,

        // Africa
        "Africa/Cairo" to 30.0,
        "Africa/Johannesburg" to -26.2,
        "Africa/Nairobi" to -1.2,
        "Africa/Lagos" to 6.5
    )

    fun getLatitudeForTimezone(timezoneId: String = TestLocationConfig.getEffectiveTimezone().id): Float {
        return timezoneLatitudes[timezoneId]?.toFloat() ?: 45f
    }
}
