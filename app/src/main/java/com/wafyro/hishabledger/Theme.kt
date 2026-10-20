package com.wafyro.hishabledger

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf

val Green = Color(0xFF176B52)

val GreenDark = Color(0xFF0F5942)

val Page = Color(0xFFF7FAF8)

val Danger = Color(0xFFB3261E)

object AppThemePreference {
    var mode by mutableStateOf("system")
    fun load(context: Context) { mode = context.getSharedPreferences("hishab_appearance", Context.MODE_PRIVATE).getString("mode", "system") ?: "system" }
    fun set(context: Context, value: String) {
        mode = value
        context.getSharedPreferences("hishab_appearance", Context.MODE_PRIVATE).edit().putString("mode", value).apply()
    }
}

val LocalDarkTheme = staticCompositionLocalOf { false }

private val HishabLightColors = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDC), onPrimaryContainer = Color(0xFF0A3B2C),
    secondary = GreenDark, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EFE7), onSecondaryContainer = Color(0xFF14372A),
    tertiary = Color(0xFF3A6B7E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E9F1), onTertiaryContainer = Color(0xFF0B3441),
    background = Page, onBackground = Color(0xFF1D3028),
    surface = Color.White, onSurface = Color(0xFF1D3028),
    surfaceVariant = Color(0xFFE3EDE7), onSurfaceVariant = Color(0xFF4A5C53),
    surfaceTint = Green,
    inverseSurface = Color(0xFF2E3A34), inverseOnSurface = Color(0xFFEEF4F0), inversePrimary = Color(0xFF72D6AE),
    error = Danger, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7B8C82), outlineVariant = Color(0xFFC9D8CF),
    scrim = Color.Black,
    surfaceBright = Color.White, surfaceDim = Color(0xFFD8E0DB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F7F4),
    surfaceContainer = Color(0xFFEDF3EF), surfaceContainerHigh = Color(0xFFE7EEEA),
    surfaceContainerHighest = Color(0xFFE1E9E4)
)

private val HishabDarkColors = darkColorScheme(
    primary = Color(0xFF72D6AE), onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF0F5942), onPrimaryContainer = Color(0xFFC2F0DB),
    secondary = Color(0xFF9ADFC0), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF244237), onSecondaryContainer = Color(0xFFC5EBD9),
    tertiary = Color(0xFF9CCBDB), onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF1F4D5C), onTertiaryContainer = Color(0xFFC0E9F7),
    background = Color(0xFF101713), onBackground = Color(0xFFE2EAE4),
    surface = Color(0xFF18211C), onSurface = Color(0xFFE2EAE4),
    surfaceVariant = Color(0xFF26332C), onSurfaceVariant = Color(0xFFB8C8BD),
    surfaceTint = Color(0xFF72D6AE),
    inverseSurface = Color(0xFFE2EAE4), inverseOnSurface = Color(0xFF1D3028), inversePrimary = Green,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6F8178), outlineVariant = Color(0xFF4A5D52),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2F3B35), surfaceDim = Color(0xFF0D130F),
    surfaceContainerLowest = Color(0xFF0B110D), surfaceContainerLow = Color(0xFF141C17),
    surfaceContainer = Color(0xFF18211C), surfaceContainerHigh = Color(0xFF202B25),
    surfaceContainerHighest = Color(0xFF26332C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun hishabTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
    actionIconContentColor = MaterialTheme.colorScheme.onBackground
)

@Composable
fun HishabTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AppThemePreference.load(context) }
    val dark = when (AppThemePreference.mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) HishabDarkColors else HishabLightColors

    // Keep the status bar, navigation bar and window background in sync with the chosen theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
                @Suppress("DEPRECATION")
                window.statusBarColor = colors.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors) {
            // Root surface: guarantees a themed background and a readable default text colour everywhere.
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground, content = content)
        }
    }
}
