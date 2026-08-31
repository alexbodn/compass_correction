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
}
