package fr.conichonhaa.nakivo.ui.theme

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
import fr.conichonhaa.nakivo.data.Tone

private val Navy = Color(0xFF0B3D91)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Color(0xFF3A5BA0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    secondary = Color(0xFFBBC7E8),
)

@Composable
fun NakivoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Couleurs de statut, lisibles en clair comme en sombre. */
@Composable
fun toneColor(tone: Tone): Color {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        Tone.GOOD -> if (dark) Color(0xFF6DD58C) else Color(0xFF1E7B3A)
        Tone.BAD -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        Tone.WARN -> if (dark) Color(0xFFFFCC80) else Color(0xFFB26A00)
        Tone.RUNNING -> if (dark) Color(0xFF82B1FF) else Color(0xFF1565C0)
        Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
