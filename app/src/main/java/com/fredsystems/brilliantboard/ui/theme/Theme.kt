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

data class AccentTheme(val id: String, val label: String, val color: Color)

object AccentThemes {
    val all = listOf(
        AccentTheme("teal", "Teal", Color(0xFF66FCF1)),
        AccentTheme("violet", "Violet", Color(0xFF9C7CFF)),
        AccentTheme("emerald", "Emerald", Color(0xFF81B64C)),
        AccentTheme("amber", "Amber", Color(0xFFF3C969)),
        AccentTheme("rose", "Rose", Color(0xFFF277A6)),
        AccentTheme("azure", "Azure", Color(0xFF6BB8FF)),
    )

    fun byId(id: String): AccentTheme = all.firstOrNull { it.id.equals(id, true) } ?: all.first()
}
val AccentSecondary = Color(0xFF45A29E)
val SurfaceDark = Color(0xFF0B0C10)
val SurfaceRaised = Color(0xFF1F2833)
val TextPrimary = Color(0xFFC5C6C7)
val Good = Color(0xFF81B64C)
val Warning = Color(0xFFF3C969)
val Danger = Color(0xFFE76A5E)

@Composable
fun BrilliantBoardTheme(
    dynamicColor: Boolean = false,
    amoled: Boolean = true,
    accent: Color = Accent,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF05201F),
        secondary = AccentSecondary,
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = accent,
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
