package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten

private val SPALTENBREITE = 108.dp
private val NAMENSSPALTENBREITE = 168.dp

/**
 * Ebene 2: zeigt "Kosten Spielbetrieb" optisch an das Google Sheet angelehnt.
 * Zeilen, deren erste Zelle "Name" und zweite Zelle "Fixkosten" enthält, werden
 * als blauer Balken mit weißer, fetter Schrift dargestellt (es gibt zwei solche
 * Kopfzeilen im Sheet: eine kleine Name/Fixkosten/Bemerkung-Übersicht und die
 * große Detailtabelle). Komplett leere Zeilen erzeugen einen Leerraum, so wie im
 * Sheet selbst (z.B. zwischen "Summe Betreuung" und der Detailtabelle).
 */
@Composable
fun KostenSpielbetriebScreen(daten: KostenSpielbetriebDaten?, modifier: Modifier = Modifier) {
    if (daten == null || daten.rohZeilen.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            Text("Keine Daten für diesen Monat gefunden.")
        }
        return
    }

    val scrollZustand = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        Text(
            "Kosten Spielbetrieb – ${daten.monat}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp)
        )
        Column(Modifier.horizontalScroll(scrollZustand)) {
            LazyColumn {
                items(daten.rohZeilen.size) { index ->
                    val zeile = daten.rohZeilen[index]
                    when {
                        istKomplettLeer(zeile) -> Box(Modifier.height(20.dp))
                        istKopfzeile(zeile) -> KopfzeilenBalken(zeile)
                        else -> DatenZeile(zeile)
                    }
                }
            }
        }
    }
}

private fun istKomplettLeer(zeile: List<String>): Boolean = zeile.all { it.isBlank() }

private fun istKopfzeile(zeile: List<String>): Boolean {
    val erste = zeile.getOrNull(0)?.trim() ?: ""
    val zweite = zeile.getOrNull(1)?.trim() ?: ""
    return erste.equals("Name", ignoreCase = true) && zweite.contains("Fixkosten", ignoreCase = true)
}

@Composable
private fun KopfzeilenBalken(zeile: List<String>) {
    val letzteBefuellteSpalte = zeile.indexOfLast { it.isNotBlank() }
    val sichtbareZeile = if (letzteBefuellteSpalte >= 0) zeile.take(letzteBefuellteSpalte + 1) else zeile

    Row(Modifier.background(MaterialTheme.colorScheme.primary)) {
        sichtbareZeile.forEachIndexed { index, wert ->
            Box(
                Modifier.width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE).padding(6.dp)
            ) {
                Text(
                    wert,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DatenZeile(zeile: List<String>) {
    Row {
        zeile.forEachIndexed { index, wert ->
            Box(
                Modifier
                    .width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(6.dp)
            ) {
                Text(wert, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
        }
    }
}
