package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteDaten

private val SPALTENBREITE = 96.dp

/** Ebene 1: zeigt die Trainingsliste (Spalte A–AM) für den gewählten Monat, wie im Google Sheet. */
@Composable
fun TrainingslisteScreen(daten: TrainingslisteDaten?, modifier: Modifier = Modifier) {
    if (daten == null || daten.zeilen.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            Text("Keine Daten für diesen Monat gefunden.")
        }
        return
    }

    val scrollZustand = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        Text(
            "Trainingsliste – ${daten.monat}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp)
        )
        Column(Modifier.horizontalScroll(scrollZustand)) {
            HeaderZeile(daten.kopfzeile)
            LazyColumn {
                items(daten.zeilen) { zeile ->
                    Row {
                        zeile.werte.forEach { wert ->
                            ZellenText(wert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderZeile(kopfzeile: List<String>) {
    Row(Modifier.background(MaterialTheme.colorScheme.primary)) {
        kopfzeile.forEach { titel ->
            Box(
                Modifier.width(SPALTENBREITE).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp)
            ) {
                Text(
                    titel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ZellenText(wert: String) {
    Box(
        Modifier.width(SPALTENBREITE).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp)
    ) {
        Text(wert, style = MaterialTheme.typography.bodySmall)
    }
}
