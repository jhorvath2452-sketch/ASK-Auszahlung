package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteDaten

private val SPALTENBREITE = 88.dp
private val NAMENSSPALTENBREITE = 168.dp

/** Kürzel-Legende der Trainingsliste, wie im Google Sheet hinterlegt. */
private val ABKUERZUNGEN = listOf(
    "A" to "Arbeit",
    "V" to "Verletzt",
    "E" to "Entschuldigt",
    "AN" to "Anwesend ohne Training",
    "DR" to "Dienstreise",
    "BE" to "Berufsschule",
    "BH" to "Bundesheer",
    "UR" to "Urlaub",
    "UE" to "Unentschuldigt",
    "K" to "Krank",
    "K+" to "Krank inkl. Krankmeldung",
    "K-" to "Krank ohne Krankmeldung"
)

private val WOCHENTAGE = setOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val TRAINING_MATCH_KUERZEL = setOf("1", "M")
private val DATUMS_MUSTER = Regex("""\d{1,2}[./]\d{1,2}""")

/** Ebene 1: zeigt die Trainingsliste (Spalte A–AM) für den gewählten Monat, wie im Google Sheet. */
@Composable
fun TrainingslisteScreen(daten: TrainingslisteDaten?, modifier: Modifier = Modifier) {
    var zeigeInfo by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Trainingsliste" + (daten?.let { " – ${it.monat}" } ?: ""),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { zeigeInfo = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Kürzel-Erklärung")
            }
        }

        if (daten == null || daten.zeilen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Keine Daten für diesen Monat gefunden.")
            }
        } else {
            val scrollZustand = rememberScrollState()
            Column(Modifier.horizontalScroll(scrollZustand)) {
                HeaderZeile(daten.kopfzeile)
                LazyColumn {
                    items(daten.zeilen.size) { index ->
                        val zeile = daten.zeilen[index]
                        val hintergrund = if (index % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        Row(Modifier.background(hintergrund)) {
                            zeile.werte.forEachIndexed { spaltenIndex, wert ->
                                ZellenText(wert, istNamensSpalte = spaltenIndex == 0)
                            }
                        }
                    }
                }
            }
        }
    }

    if (zeigeInfo) {
        AlertDialog(
            onDismissRequest = { zeigeInfo = false },
            title = { Text("Kürzel-Erklärung") },
            text = {
                Column {
                    ABKUERZUNGEN.forEach { (kuerzel, bedeutung) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(kuerzel, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                            Text(bedeutung)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { zeigeInfo = false }) { Text("Schließen") }
            }
        )
    }
}

@Composable
private fun HeaderZeile(kopfzeile: List<String>) {
    Row(Modifier.background(MaterialTheme.colorScheme.primary)) {
        kopfzeile.forEachIndexed { index, titel ->
            Box(
                Modifier
                    .width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(6.dp)
            ) {
                Text(
                    titel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (index == 0) 1 else Int.MAX_VALUE,
                    overflow = if (index == 0) TextOverflow.Ellipsis else TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
private fun ZellenText(wert: String, istNamensSpalte: Boolean) {
    val hervorheben = wert.trim() in WOCHENTAGE ||
        wert.trim() in TRAINING_MATCH_KUERZEL ||
        DATUMS_MUSTER.containsMatchIn(wert)

    Box(
        Modifier
            .width(if (istNamensSpalte) NAMENSSPALTENBREITE else SPALTENBREITE)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(6.dp)
    ) {
        Text(
            wert,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hervorheben) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = if (istNamensSpalte) TextOverflow.Visible else TextOverflow.Clip,
            softWrap = false
        )
    }
}
