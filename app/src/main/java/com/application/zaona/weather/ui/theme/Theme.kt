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
    var dynamicColorEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
        dynamicColorEnabled = prefs.getBoolean("dynamic_color_enabled", false)
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                "theme_mode" -> themeMode = sharedPrefs.getString("theme_mode", "system") ?: "system"
                "dynamic_color_enabled" -> dynamicColorEnabled = sharedPrefs.getBoolean("dynamic_color_enabled", false)
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

    val controller = remember(themeMode, dynamicColorEnabled) {
        if (dynamicColorEnabled) {
            when (themeMode) {
                "light" -> ThemeController(ColorSchemeMode.MonetLight)
                "dark" -> ThemeController(ColorSchemeMode.MonetDark)
                else -> ThemeController(ColorSchemeMode.MonetSystem)
            }
        } else {
            when (themeMode) {
                "light" -> ThemeController(ColorSchemeMode.System, isDark = false)
                "dark" -> ThemeController(ColorSchemeMode.System, isDark = true)
                else -> ThemeController(ColorSchemeMode.System)
            }
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
