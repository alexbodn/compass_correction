package com.example.compasscorrector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TestLocationConfig {
    var isTestingMode by mutableStateOf(false)

    var networkSpoofEnabled by mutableStateOf(false)
    var networkAvailable by mutableStateOf(false)
    var networkSpoofCoords by mutableStateOf("0.0, 0.0")

    var gnssSpoofEnabled by mutableStateOf(false)
    var gnssInFix by mutableStateOf("0")
    var gnssSpoofCoords by mutableStateOf("0.0, 0.0")

    var timeSpoofEnabled by mutableStateOf(false)
    var timeSpoofStr by mutableStateOf("2024-01-01T12:00:00") // ISO format
    var timezoneSpoofEnabled by mutableStateOf(false)
    var timezoneSpoofStr by mutableStateOf("UTC")

    fun getEffectiveTimeMillis(): Long {
        if (!timeSpoofEnabled) return System.currentTimeMillis()
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            format.timeZone = if (timezoneSpoofEnabled) java.util.TimeZone.getTimeZone(timezoneSpoofStr) else java.util.TimeZone.getDefault()
            val date = format.parse(timeSpoofStr.trim())
            date?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getEffectiveTimezone(): java.util.TimeZone {
        return if (timezoneSpoofEnabled) {
            java.util.TimeZone.getTimeZone(timezoneSpoofStr)
        } else {
            java.util.TimeZone.getDefault()
        }
    }
}
