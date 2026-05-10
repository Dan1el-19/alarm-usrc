package dev.usrc.alarm.ui.theme

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
    primary = OneUIBlue,
    onPrimary = Color.White,
    background = OneUIGrayBackgroundDark,
    surface = OneUISurfaceDark,
    surfaceVariant = OneUICardDark,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.LightGray,
    error = OneUIRed,
    errorContainer = OneUIRed.copy(alpha = 0.2f),
    onErrorContainer = OneUIRed
)

private val LightColorScheme = lightColorScheme(
    primary = OneUIBlue,
    onPrimary = Color.White,
    background = OneUIGrayBackgroundLight,
    surface = OneUISurfaceLight,
    surfaceVariant = OneUICardLight,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color.DarkGray,
    error = OneUIRed,
    errorContainer = OneUIRed.copy(alpha = 0.2f),
    onErrorContainer = OneUIRed
)

@Composable
fun AlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // One UI loves dynamic colors from wallpaper (Android 12+)
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
