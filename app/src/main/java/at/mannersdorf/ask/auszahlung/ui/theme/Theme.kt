package at.mannersdorf.ask.auszahlung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VereinsBlau = Color(0xFF1B4F8C)
private val VereinsBlauDunkel = Color(0xFF0E2E52)
private val Akzent = Color(0xFFE0A100)

private val HellesFarbschema = lightColorScheme(
    primary = VereinsBlau,
    onPrimary = Color.White,
    secondary = Akzent,
    background = Color(0xFFF5F7FA),
    surface = Color.White
)

private val DunklesFarbschema = darkColorScheme(
    primary = Color(0xFF6FA8E0),
    onPrimary = VereinsBlauDunkel,
    secondary = Akzent,
    background = Color(0xFF101418),
    surface = Color(0xFF1B2126)
)

@Composable
fun AuszahlungAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DunklesFarbschema else HellesFarbschema
    MaterialTheme(colorScheme = colorScheme, content = content)
}
