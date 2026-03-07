package com.application.zaona.weather.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun SimpleweathersyncerngTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf("system") }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "theme_mode") {
                themeMode = sharedPrefs.getString("theme_mode", "system") ?: "system"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isDarkMode = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val controller = remember(themeMode) {
        when (themeMode) {
            "light" -> ThemeController(ColorSchemeMode.System, isDark = false)
            "dark" -> ThemeController(ColorSchemeMode.System, isDark = true)
            else -> ThemeController(ColorSchemeMode.System)
        }
    }

    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
    }
    MiuixTheme(
        controller = controller,
        content = content
    )
}
