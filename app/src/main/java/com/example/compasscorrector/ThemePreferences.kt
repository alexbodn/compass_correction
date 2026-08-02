package com.example.compasscorrector

import android.content.Context
import android.content.SharedPreferences

enum class AppTheme {
    LIGHT, DARK, SYSTEM, AUTO_SUNSET
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

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
}
