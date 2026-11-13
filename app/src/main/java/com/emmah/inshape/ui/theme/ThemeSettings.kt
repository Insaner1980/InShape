package com.emmah.inshape.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

class ThemeSettings(context: Context) {
    private val preferences = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(ThemeMode.entries.find { it.name == preferences.getString("theme_mode", null) } ?: ThemeMode.System)
    val mode: StateFlow<ThemeMode> = current.asStateFlow()
    fun set(mode: ThemeMode) {
        preferences.edit().putString("theme_mode", mode.name).apply()
        current.value = mode
    }
}
