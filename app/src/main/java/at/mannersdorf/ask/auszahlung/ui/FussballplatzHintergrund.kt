package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Dezente Fußballplatz-Linien als Hintergrunddekoration (Spielfeldrand,
 * Mittellinie, Mittelkreis, Strafräume) - passend zum Vereins-Look, ähnlich
 * der Kassenrechner-App. Bewusst sehr blass, damit Tabellen darüber gut
 * lesbar bleiben. Um 90° gedreht, damit es in der breiten, flachen
 * Kopfzeile besser wirkt.
 */
@Composable
fun FussballplatzHintergrund(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        rotate(degrees = 90f) {
            val linienFarbe = Color.White.copy(alpha = 0.35f)
            val strichbreite = 2.5f
            val rand = 24f

            val breite = size.width - rand * 2
            val hoehe = size.height - rand * 2
            if (breite <= 0f || hoehe <= 0f) return@rotate

            // Spielfeldrand
            drawRect(
                color = linienFarbe,
                topLeft = Offset(rand, rand),
                size = androidx.compose.ui.geometry.Size(breite, hoehe),
                style = Stroke(width = strichbreite)
            )

            // Mittellinie
            val mitteY = rand + hoehe / 2
            drawLine(
                color = linienFarbe,
                start = Offset(rand, mitteY),
                end = Offset(rand + breite, mitteY),
                strokeWidth = strichbreite
            )

            // Mittelkreis
            val kreisRadius = minOf(breite, hoehe) * 0.14f
            drawCircle(
                color = linienFarbe,
                radius = kreisRadius,
                center = Offset(rand + breite / 2, mitteY),
                style = Stroke(width = strichbreite)
            )

            // Strafräume oben und unten
            val strafraumBreite = breite * 0.45f
            val strafraumHoehe = hoehe * 0.12f
            drawRect(
                color = linienFarbe,
                topLeft = Offset(rand + (breite - strafraumBreite) / 2, rand),
                size = androidx.compose.ui.geometry.Size(strafraumBreite, strafraumHoehe),
                style = Stroke(width = strichbreite)
            )
            drawRect(
                color = linienFarbe,
                topLeft = Offset(rand + (breite - strafraumBreite) / 2, rand + hoehe - strafraumHoehe),
                size = androidx.compose.ui.geometry.Size(strafraumBreite, strafraumHoehe),
                style = Stroke(width = strichbreite)
            )
        }
    }
}
