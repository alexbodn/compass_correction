package com.example.compasscorrector

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme {
    LIGHT, DARK, SYSTEM, AUTO_SUNSET
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

    var isDstActive: Boolean
        get() = prefs.getBoolean("is_dst_active", java.util.TimeZone.getDefault().inDaylightTime(java.util.Date()))
        set(value) = prefs.edit().putBoolean("is_dst_active", value).apply()
}
