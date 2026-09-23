package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteDaten

private val SPALTENBREITE = 88.dp
private val NAMENSSPALTENBREITE = 168.dp
private val MarkierGelb = Color(0xFFFFF59D)
private val MarkierRot = Color(0xFFFFCDD2)
private val MarkierOrange = Color(0xFFFFE0B2)

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

// "Gegner 1"-"Gegner 4" / "Spalten ausblenden"-Logik: siehe SpaltenErkennung.kt
// (von Training UND Kosten Spielbetrieb gemeinsam genutzt).

/**
 * Ebene 1: zeigt die Trainingsliste (Spalte A–AM) für den gewählten Monat, wie
 * im Google Sheet. Namen mit einem "*" am Ende markieren die ganze Zeile rot,
 * mit "**" hell-orange. Die Zelle mit dem Text "zahlt Hans" wird ebenfalls
 * hell-orange hervorgehoben. Die letzte Zeile ist immer hellgrün (wie die
 * helle Zebra-Streifen-Farbe) mit fetter Schrift. Antippen einer Zeile markiert sie hellgelb (nochmals
 * antippen hebt die Markierung wieder auf). Über "Spalten ausblenden" lassen
 * sich die Gegner-Spalten P/R/T/V ausblenden, wenn dort tatsächlich
 * "Gegner 1"-"Gegner 4" steht (und die zugehörige "x"-Spalte Q/S/U/W).
 */
@Composable
fun TrainingslisteScreen(daten: TrainingslisteDaten?, modifier: Modifier = Modifier) {
    var spaltenAusblenden by remember { mutableStateOf(false) }
    var markierteZeilen by remember { mutableStateOf(setOf<Int>()) }

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
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Spalten ausblenden")
            Switch(checked = spaltenAusblenden, onCheckedChange = { spaltenAusblenden = it })
        }

        if (daten == null || daten.zeilen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Keine Daten für diesen Monat gefunden.")
            }
        } else {
            val rohZeilen = remember(daten) { daten.zeilen.map { it.werte } }
            val auszublendendeSpalten = remember(daten) { ermittleAuszublendendeSpalten(rohZeilen) }

            val scrollZustand = rememberScrollState()
            Column(Modifier.horizontalScroll(scrollZustand)) {
                HeaderZeile(daten.kopfzeile, spaltenAusblenden, auszublendendeSpalten)
                LazyColumn {
                    items(daten.zeilen.size) { index ->
                        val zeile = daten.zeilen[index]
                        val zeilenSchluessel = zeile.werte.hashCode()
                        val istLetzteZeile = index == daten.zeilen.size - 1
                        val ersteZelle = zeile.werte.firstOrNull()?.trim() ?: ""
                        val istTrainingMatchZeile = ersteZelle.contains("Training", ignoreCase = true) &&
                            ersteZelle.contains("Match", ignoreCase = true)
                        val sterne = sternAnzahl(ersteZelle)

                        val hintergrund = when {
                            istLetzteZeile -> MaterialTheme.colorScheme.surfaceVariant
                            sterne == 1 -> MarkierRot
                            sterne == 2 -> MarkierOrange
                            zeilenSchluessel in markierteZeilen -> MarkierGelb
                            index % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        }

                        Row(
                            Modifier
                                .background(hintergrund)
                                .clickable {
                                    markierteZeilen = if (zeilenSchluessel in markierteZeilen) {
                                        markierteZeilen - zeilenSchluessel
                                    } else {
                                        markierteZeilen + zeilenSchluessel
                                    }
                                }
                        ) {
                            sichtbareSpaltenIndiziert(zeile.werte, spaltenAusblenden, auszublendendeSpalten).forEach { (spaltenIndex, wert) ->
                                ZellenText(
                                    wert = wert,
                                    istNamensSpalte = spaltenIndex == 0,
                                    erzwingeFett = istTrainingMatchZeile || istLetzteZeile,
                                    zahltHansFeld = wert.contains("zahlt Hans", ignoreCase = true)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Namen mit "**" am Ende -> 2, mit einem "*" -> 1, sonst 0. */
private fun sternAnzahl(text: String): Int {
    val getrimmt = text.trim()
    return when {
        getrimmt.endsWith("**") -> 2
        getrimmt.endsWith("*") -> 1
        else -> 0
    }
}


/**
 * Info-Button + Kürzel-Erklärungs-Dialog für die Trainingsliste, eigenständig
 * verwendbar (z.B. direkt neben dem Monats-Dropdown in der MainScreen).
 */
@Composable
fun TrainingsInfoButton(modifier: Modifier = Modifier) {
    var zeigeInfo by remember { mutableStateOf(false) }

    IconButton(onClick = { zeigeInfo = true }, modifier = modifier) {
        Icon(Icons.Filled.Info, contentDescription = "Kürzel-Erklärung")
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
private fun HeaderZeile(kopfzeile: List<String>, spaltenAusblenden: Boolean, auszublendendeSpalten: Set<Int>) {
    Row(Modifier.background(MaterialTheme.colorScheme.primary)) {
        sichtbareSpaltenIndiziert(kopfzeile, spaltenAusblenden, auszublendendeSpalten).forEach { (index, titel) ->
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
private fun ZellenText(
    wert: String,
    istNamensSpalte: Boolean,
    erzwingeFett: Boolean = false,
    erzwingeWeiss: Boolean = false,
    zahltHansFeld: Boolean = false
) {
    val hervorheben = erzwingeFett ||
        wert.trim() in WOCHENTAGE ||
        wert.trim() in TRAINING_MATCH_KUERZEL ||
        DATUMS_MUSTER.containsMatchIn(wert)

    Box(
        Modifier
            .width(if (istNamensSpalte) NAMENSSPALTENBREITE else SPALTENBREITE)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(if (zahltHansFeld) MarkierOrange else Color.Transparent)
            .padding(6.dp)
    ) {
        Text(
            wert,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hervorheben) FontWeight.Bold else FontWeight.Normal,
            color = if (erzwingeWeiss) Color.White else Color.Unspecified,
            maxLines = 1,
            overflow = if (istNamensSpalte) TextOverflow.Visible else TextOverflow.Clip,
            softWrap = false
        )
    }
}
