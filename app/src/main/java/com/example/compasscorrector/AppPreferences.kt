package com.example.compasscorrector

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme {
    LIGHT, DARK, SYSTEM, AUTO_SUNSET
}

enum class DstMode {
    AUTO_SYSTEM, ALWAYS_ON, ALWAYS_OFF
}

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var theme: AppTheme
        get() {
            val name = prefs.getString("app_theme", AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
            return try {
                AppTheme.valueOf(name)
            } catch (e: Exception) {
                AppTheme.SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString("app_theme", value.name).apply()
        }

    var useTrueNorth: Boolean
        get() = prefs.getBoolean("use_true_north", false)
        set(value) = prefs.edit().putBoolean("use_true_north", value).apply()

    var dstMode: DstMode
        get() {
            val name = prefs.getString("dst_mode", DstMode.AUTO_SYSTEM.name) ?: DstMode.AUTO_SYSTEM.name
            return try {
                DstMode.valueOf(name)
            } catch (e: Exception) {
                DstMode.AUTO_SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString("dst_mode", value.name).apply()
        }

    fun evaluateIsDstActive(): Boolean {
        return when (dstMode) {
            DstMode.ALWAYS_ON -> true
            DstMode.ALWAYS_OFF -> false
            DstMode.AUTO_SYSTEM -> java.util.TimeZone.getDefault().inDaylightTime(java.util.Date())
        }
    }
}
