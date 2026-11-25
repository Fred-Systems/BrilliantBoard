package com.fredsystems.brilliantboard.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val Accent = Color(0xFF66FCF1)
val AccentSecondary = Color(0xFF45A29E)
val SurfaceDark = Color(0xFF0B0C10)
val SurfaceRaised = Color(0xFF1F2833)
val TextPrimary = Color(0xFFC5C6C7)
val Good = Color(0xFF81B64C)
val Warning = Color(0xFFF3C969)
val Danger = Color(0xFFE76A5E)

enum class BoardTheme(val light: Color, val dark: Color, val lastMove: Color) {
    EMERALD(Color(0xFFEEEED2), Color(0xFF769656), Color(0xFFBACA44)),
    BLUE(Color(0xFFDEE3E6), Color(0xFF6E8296), Color(0xFFB0C2D4)),
    GREY(Color(0xFFE8E8E8), Color(0xFF7A7A7A), Color(0xFFB0B0B0)),
    WOOD(Color(0xFFF0D9B5), Color(0xFFB58863), Color(0xFFCFA97B)),
    AMOLED(Color(0xFF2B2B2B), Color(0xFF111111), Color(0xFF4A5D3A));

    companion object {
        fun from(name: String): BoardTheme = entries.firstOrNull { it.name.equals(name, true) } ?: EMERALD
    }
}

@Composable
fun BrilliantBoardTheme(
    dynamicColor: Boolean = false,
    amoled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkScheme = darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFF05201F),
        secondary = AccentSecondary,
        tertiary = Warning,
        background = if (amoled) Color.Black else SurfaceDark,
        onBackground = TextPrimary,
        surface = if (amoled) Color(0xFF050505) else SurfaceRaised,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceRaised,
        onSurfaceVariant = Color(0xFF9AA0A6),
        error = Danger,
    )
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicColorScheme(context)
    } else darkScheme

    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicColorScheme(context: android.content.Context) = dynamicDarkColorScheme(context)
