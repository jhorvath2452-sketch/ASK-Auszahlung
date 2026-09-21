package at.mannersdorf.ask.auszahlung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Vereinsfarbe: sattes Fußballplatz-Grün.
val VereinsGruen = Color(0xFF1B7A3C)
val VereinsGruenDunkel = Color(0xFF0F4D26)
val HellgruenerHintergrund = Color(0xFFE7F5EA)
private val Akzent = Color(0xFFE0A100)

private val HellesFarbschema = lightColorScheme(
    primary = VereinsGruen,
    onPrimary = Color.White,
    secondary = Akzent,
    background = HellgruenerHintergrund,
    surface = Color.White,
    surfaceVariant = Color(0xFFD9EEDF)
)

private val DunklesFarbschema = darkColorScheme(
    primary = Color(0xFF6FCB8F),
    onPrimary = VereinsGruenDunkel,
    secondary = Akzent,
    background = Color(0xFF10160F),
    surface = Color(0xFF1A231A)
)

@Composable
fun AuszahlungAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DunklesFarbschema else HellesFarbschema
    MaterialTheme(colorScheme = colorScheme, content = content)
}
