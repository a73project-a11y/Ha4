package app.farmpulse.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Soil = Color(0xFF0F1612)
val SoilCard = Color(0xFF1A2420)
val Wheat = Color(0xFFC6A15B)
val PulseGreen = Color(0xFF3DDC84)
val Cream = Color(0xFFE8EDE9)
val Muted = Color(0xFF9AA69E)
val Danger = Color(0xFFE85D4C)

private val scheme = darkColorScheme(
    primary = Wheat,
    onPrimary = Soil,
    secondary = PulseGreen,
    onSecondary = Soil,
    background = Soil,
    onBackground = Cream,
    surface = SoilCard,
    onSurface = Cream,
    surfaceVariant = Color(0xFF24302A),
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Cream,
)

@Composable
fun FarmPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
