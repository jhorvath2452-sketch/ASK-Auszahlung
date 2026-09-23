package at.mannersdorf.ask.auszahlung.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.formatiereDeutscheZahl
import at.mannersdorf.ask.auszahlung.data.model.SpielerKosten
import at.mannersdorf.ask.auszahlung.data.model.istMasseur
import at.mannersdorf.ask.auszahlung.data.parseDeutscheZahl

/**
 * Ebene "Masseur": betrifft alle Zeilen aus "Kosten Spielbetrieb", deren Name
 * "Masseur" enthält. Vereinfachtes Formular: nur eine Aufwandsentschädigung
 * (Spalte C × Spalte F) statt der Trainingsgeld/Punkte/Abzüge-Aufteilung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasseurScreen(
    monat: String?,
    alleSpieler: List<SpielerKosten>,
    gewaehlterName: String?,
    speichernErfolgreich: Boolean,
    onAusgewaehlt: (String) -> Unit,
    onDatenUebernehmen: (bemerkung: String, korrektur: String, unterschriftBase64: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val masseure = remember(alleSpieler) { alleSpieler.filter { it.istMasseur() } }
    val spieler = masseure.find { it.name == gewaehlterName }
    var dropdownOffen by remember { mutableStateOf(false) }
    var bemerkung by remember(gewaehlterName) { mutableStateOf("") }
    var korrektur by remember(gewaehlterName) { mutableStateOf("0") }
    var unterschriftBase64 by remember(gewaehlterName) { mutableStateOf<String?>(null) }
    var zeigeSignaturPad by remember { mutableStateOf(false) }

    val aufwandsentschaedigung = spieler?.let {
        (parseDeutscheZahl(it.ap) ?: 0.0) * (parseDeutscheZahl(it.masseurFaktor) ?: 0.0)
    } ?: 0.0
    val ausbezahlterBetrag = aufwandsentschaedigung + (parseDeutscheZahl(korrektur) ?: 0.0)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        ExposedDropdownMenuBox(expanded = dropdownOffen, onExpandedChange = { dropdownOffen = it }) {
            OutlinedTextField(
                value = gewaehlterName ?: "Masseur wählen",
                onValueChange = {},
                readOnly = true,
                label = { Text("Masseur") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = dropdownOffen, onDismissRequest = { dropdownOffen = false }) {
                masseure.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = {
                            onAusgewaehlt(s.name)
                            unterschriftBase64 = null
                            dropdownOffen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (spieler == null) {
            Text(if (masseure.isEmpty()) "Keine Masseur-Einträge gefunden." else "Bitte auswählen.")
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${spieler.name}  (€ ${spieler.ap.replace("€", "").trim()} pro Anwesenheit)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text("Monat: ${monat ?: "-"}", fontWeight = FontWeight.Bold)
                Text("Einsätze pro Monat: " + ganzzahlOderRoh(spieler.masseurEinsaetze), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                InfoZeileMasseur("AUFWANDSENTSCHÄDIGUNG", "€ " + formatiereDeutscheZahl(aufwandsentschaedigung))
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = korrektur,
            onValueChange = { neu -> korrektur = begrenzeKorrekturMasseur(neu) },
            label = { Text("Korrektur (€)") },
            supportingText = { Text("Manuelle Korrektur, -2000 bis +2000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = "€ " + formatiereDeutscheZahl(ausbezahlterBetrag),
            onValueChange = {},
            readOnly = true,
            label = { Text("Ausbezahlter Betrag") },
            supportingText = { Text("Aufwandsentschädigung + Korrektur") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = bemerkung,
            onValueChange = { if (it.length <= 250) bemerkung = it },
            label = { Text("Bemerkung") },
            supportingText = { Text("${bemerkung.length}/250") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Text("Betrag erhalten:", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .clickable { zeigeSignaturPad = true },
            contentAlignment = Alignment.Center
        ) {
            val aktuelleUnterschrift = unterschriftBase64
            if (aktuelleUnterschrift == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Draw, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zum Unterschreiben antippen")
                }
            } else {
                val bytes = Base64.decode(aktuelleUnterschrift, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Unterschrift")
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            enabled = unterschriftBase64 != null,
            onClick = { onDatenUebernehmen(bemerkung, korrektur, unterschriftBase64!!) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Daten übernehmen")
        }

        if (speichernErfolgreich) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                Spacer(Modifier.width(8.dp))
                Text("Gespeichert.", color = Color(0xFF2E7D32))
            }
        }
    }

    if (zeigeSignaturPad) {
        SignaturePad(
            onUebernehmen = { base64 -> unterschriftBase64 = base64; zeigeSignaturPad = false },
            onAbbrechen = { zeigeSignaturPad = false }
        )
    }
}

@Composable
private fun InfoZeileMasseur(bezeichnung: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(bezeichnung, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, fontWeight = FontWeight.Medium)
    }
}

/** Ganzzahlige Anzeige ohne Nachkommastellen und ohne €, z.B. für "Einsätze pro Monat". */
internal fun ganzzahlOderRoh(rohwert: String): String {
    val zahl = parseDeutscheZahl(rohwert)
    return if (zahl != null) zahl.toInt().toString() else "(Rohwert: \"$rohwert\")"
}

private fun begrenzeKorrekturMasseur(eingabe: String): String {
    val zahl = parseDeutscheZahl(eingabe) ?: return eingabe
    return when {
        zahl > 2000.0 -> "2000"
        zahl < -2000.0 -> "-2000"
        else -> eingabe
    }
}
