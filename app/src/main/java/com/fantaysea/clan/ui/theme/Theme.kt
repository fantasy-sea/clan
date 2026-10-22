package com.fantaysea.clan.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WarmAmber,
    onPrimary = OnWarmAmber,
    primaryContainer = WarmAmberContainer,
    onPrimaryContainer = OnWarmAmberContainer,
    secondary = WarmBrown,
    onSecondary = Color.Black,
    secondaryContainer = WarmBrownContainer,
    onSecondaryContainer = NightCream,
    tertiary = WarmGolden,
    onTertiary = Color.Black,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OnWarmAmberContainer,
    background = NightBackground,
    onBackground = NightCream,
    surface = NightBackground,
    onSurface = NightCream,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightCream,
    outline = NightOutline,
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = WarmOrange,
    onPrimary = Color.White,
    primaryContainer = WarmOrangeContainer,
    onPrimaryContainer = OnWarmOrangeContainer,
    secondary = CoffeeBrown,
    onSecondary = Color.White,
    secondaryContainer = CoffeeBrownContainer,
    onSecondaryContainer = OnCoffeeBrownContainer,
    tertiary = GoldenYellow,
    onTertiary = Color.White,
    tertiaryContainer = GoldenYellowContainer,
    onTertiaryContainer = OnGoldenYellowContainer,
    background = WarmWhite,
    onBackground = WarmDarkBrown,
    surface = Color.White,
    onSurface = WarmDarkBrown,
    surfaceVariant = EggshellOrange,
    onSurfaceVariant = WarmDarkBrown,
    outline = WarmOutline,
    error = Color(0xFFBA1A1A)
)

@Composable
fun ClanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}