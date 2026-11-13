package com.emmah.inshape.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ComponentActivity.ActivityTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val mode by settings.mode.collectAsStateWithLifecycle()
    val dark = mode.isDark()
    DisposableEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    InShapeTheme(mode, content)
}
