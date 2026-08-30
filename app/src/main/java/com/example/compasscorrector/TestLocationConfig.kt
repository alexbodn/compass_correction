package com.example.compasscorrector

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TestLocationConfig {
    var isTestingMode by mutableStateOf(false)
    var networkAvailable by mutableStateOf(false)
    var networkLat by mutableStateOf("0.0")
    var networkLon by mutableStateOf("0.0")
    var gnssInFix by mutableStateOf("0")
    var gnssLat by mutableStateOf("0.0")
    var gnssLon by mutableStateOf("0.0")
}
