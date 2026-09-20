package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten

/** Ebene 2: rohe Ansicht der Tabelle "Kosten Spielbetrieb" für den gewählten Monat. */
@Composable
fun KostenSpielbetriebScreen(daten: KostenSpielbetriebDaten?, modifier: Modifier = Modifier) {
    if (daten == null || daten.spieler.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            Text("Keine Daten für diesen Monat gefunden.")
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "Kosten Spielbetrieb – ${daten.monat}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp)
        )
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            SpaltenKopf("Spieler", 2f)
            SpaltenKopf("Fixum", 1f)
            SpaltenKopf("Punkte", 1f)
            SpaltenKopf("Abzug Sonst.", 1f)
            SpaltenKopf("Abzug Masseur", 1f)
        }
        LazyColumn {
            items(daten.spieler) { spieler ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp)) {
                    ZelleGewicht(spieler.name, 2f, FontWeight.Medium)
                    ZelleGewicht(spieler.fixum, 1f)
                    ZelleGewicht(spieler.punkte, 1f)
                    ZelleGewicht(spieler.abzugSonstiges, 1f)
                    ZelleGewicht(spieler.abzugMasseur, 1f)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SpaltenKopf(text: String, gewicht: Float) {
    Text(
        text,
        modifier = Modifier.weight(gewicht),
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ZelleGewicht(
    text: String,
    gewicht: Float,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(text, modifier = Modifier.weight(gewicht), fontWeight = fontWeight)
}
