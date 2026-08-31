package com.example.compasscorrector

import android.location.Location

sealed class LocationStatus {
    data class Valid(val location: Location, val source: String) : LocationStatus()
    data class Invalid(val reason: String) : LocationStatus()
}
