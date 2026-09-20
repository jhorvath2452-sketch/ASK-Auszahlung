package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.SpaltenZuordnung

/**
 * Einstellungen: Sheet-IDs, Spaltenzuordnung der Kosten-Spielbetrieb-Tabelle
 * und Adresse/Token des eigenen Sync-Servers. Alles ist änderbar, ohne die
 * App neu bauen zu müssen.
 */
@Composable
fun SettingsScreen(
    trainingslisteId: String,
    kostenSpielbetriebId: String,
    bestaetigungId: String,
    spaltenZuordnung: SpaltenZuordnung,
    onSheetIdsSpeichern: (String, String, String) -> Unit,
    onSpaltenSpeichern: (SpaltenZuordnung) -> Unit,
    modifier: Modifier = Modifier
) {
    var trainingsliste by remember { mutableStateOf(trainingslisteId) }
    var kostenSpielbetrieb by remember { mutableStateOf(kostenSpielbetriebId) }
    var bestaetigung by remember { mutableStateOf(bestaetigungId) }

    var nameSpalte by remember { mutableStateOf(spaltenZuordnung.nameSpalte) }
    var fixumSpalte by remember { mutableStateOf(spaltenZuordnung.fixumSpalte) }
    var punkteSpalte by remember { mutableStateOf(spaltenZuordnung.punkteSpalte) }
    var sonstigesSpalte by remember { mutableStateOf(spaltenZuordnung.abzugSonstigesSpalte) }
    var masseurSpalte by remember { mutableStateOf(spaltenZuordnung.abzugMasseurSpalte) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Google-Sheet-IDs", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(trainingsliste, { trainingsliste = it }, label = { Text("Trainingsliste – Sheet-ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(kostenSpielbetrieb, { kostenSpielbetrieb = it }, label = { Text("Kosten Spielbetrieb – Sheet-ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bestaetigung, { bestaetigung = it }, label = { Text("Bestätigung – Sheet-ID (Referenz)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSheetIdsSpeichern(trainingsliste, kostenSpielbetrieb, bestaetigung) }, modifier = Modifier.fillMaxWidth()) {
            Text("Sheet-IDs speichern")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Spaltenzuordnung – Kosten Spielbetrieb", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(nameSpalte, { nameSpalte = it.uppercase() }, label = { Text("Spalte: Spielername") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fixumSpalte, { fixumSpalte = it.uppercase() }, label = { Text("Spalte: Fixum") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(punkteSpalte, { punkteSpalte = it.uppercase() }, label = { Text("Spalte: Punkte") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sonstigesSpalte, { sonstigesSpalte = it.uppercase() }, label = { Text("Spalte: Abzug Sonstiges") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(masseurSpalte, { masseurSpalte = it.uppercase() }, label = { Text("Spalte: Abzug Masseur") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                onSpaltenSpeichern(
                    SpaltenZuordnung(nameSpalte, fixumSpalte, punkteSpalte, sonstigesSpalte, masseurSpalte)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Spaltenzuordnung speichern")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            "Bestätigungen werden über Firebase gespeichert (Firestore + Cloud Storage). " +
                "Die Konfiguration dafür kommt aus app/google-services.json und ist hier " +
                "nicht einstellbar – siehe README für die Einrichtung.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
