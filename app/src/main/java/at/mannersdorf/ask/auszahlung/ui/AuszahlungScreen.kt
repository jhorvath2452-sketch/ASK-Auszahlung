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
import at.mannersdorf.ask.auszahlung.data.parseDeutscheZahl

private const val KORREKTUR_MIN = -2000.0
private const val KORREKTUR_MAX = 2000.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuszahlungScreen(
    monat: String?,
    spielerListe: List<SpielerKosten>,
    gewaehlterSpielerName: String?,
    speichernErfolgreich: Boolean,
    onSpielerGewaehlt: (String) -> Unit,
    onDatenUebernehmen: (bemerkung: String, betragErhalten: String, korrektur: String, unterschriftBase64: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spieler = spielerListe.find { it.name == gewaehlterSpielerName }
    var dropdownOffen by remember { mutableStateOf(false) }
    var bemerkung by remember(gewaehlterSpielerName) { mutableStateOf("") }
    var korrektur by remember(gewaehlterSpielerName) { mutableStateOf("0") }
    var unterschriftBase64 by remember(gewaehlterSpielerName) { mutableStateOf<String?>(null) }
    var zeigeSignaturPad by remember { mutableStateOf(false) }

    // Ausbezahlter Betrag ist nicht editierbar, sondern wird immer aus FIXUM,
    // Punkten, Abzügen und der Korrektur berechnet.
    val ausbezahlterBetrag = berechneAusbezahltenBetrag(spieler, korrektur)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(expanded = dropdownOffen, onExpandedChange = { dropdownOffen = it }) {
            OutlinedTextField(
                value = gewaehlterSpielerName ?: "Spieler wählen",
                onValueChange = {},
                readOnly = true,
                label = { Text("Spieler") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = dropdownOffen,
                onDismissRequest = { dropdownOffen = false }
            ) {
                spielerListe.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = {
                            onSpielerGewaehlt(s.name)
                            unterschriftBase64 = null
                            dropdownOffen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (spieler == null) {
            Text("Bitte einen Spieler auswählen.")
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${spieler.name}  (Fixkosten: ${spieler.fixum}  |  AP: ${spieler.ap}  |  Punkte: ${spieler.punkte})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text("Monat: ${monat ?: "-"}", fontWeight = FontWeight.Bold)
                Text(
                    "Punkte pro Monat: " + zeigeWertOderRoh(spieler.punkteMultiplikator),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                InfoZeile("FIXUM", "€ ${spieler.fixum.replace("€", "").trim()}")
                InfoZeile("PUNKTE", berechnePunkteBetrag(spieler))
                InfoZeile("Abzug Masseur", "€ ${spieler.abzugMasseur.replace("€", "").trim()}")
                InfoZeile("Abzug Sonstiges", "€ ${spieler.abzugSonstiges.replace("€", "").trim()}")
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = korrektur,
            onValueChange = { neu -> korrektur = begrenzeKorrektur(neu) },
            label = { Text("Korrektur (€)") },
            supportingText = { Text("Manuelle Korrektur, -2000 bis +2000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = "€ $ausbezahlterBetrag",
            onValueChange = {},
            readOnly = true,
            label = { Text("Ausbezahlter Betrag") },
            supportingText = { Text("FIXUM + PUNKTE − Abzug Masseur − Abzug Sonstiges + Korrektur") },
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
            onClick = {
                onDatenUebernehmen(bemerkung, ausbezahlterBetrag, korrektur, unterschriftBase64!!)
            },
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
            onUebernehmen = { base64 ->
                unterschriftBase64 = base64
                zeigeSignaturPad = false
            },
            onAbbrechen = { zeigeSignaturPad = false }
        )
    }
}

@Composable
private fun InfoZeile(bezeichnung: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(bezeichnung, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, fontWeight = FontWeight.Medium)
    }
}

/** PUNKTE-Betrag = Spalte D ("Punkte") × Spalte O ("Punkte pro Monat"). */
private fun berechnePunkteBetrag(spieler: SpielerKosten): String {
    val punkte = parseDeutscheZahl(spieler.punkte)
    val punkteProMonat = parseDeutscheZahl(spieler.punkteMultiplikator)
    if (punkte == null || punkteProMonat == null) return spieler.punkteMultiplikator
    return "€ " + formatiereDeutscheZahl(punkte * punkteProMonat)
}

/**
 * Zeigt den formatierten Euro-Betrag, falls der Rohwert aus dem Sheet als Zahl
 * gelesen werden konnte - sonst den Rohwert selbst (z.B. leer oder Text), damit
 * sofort sichtbar ist, WAS tatsächlich aus der Tabelle angekommen ist, statt
 * das still als € 0,00 zu verstecken.
 */
private fun zeigeWertOderRoh(rohwert: String): String {
    val zahl = parseDeutscheZahl(rohwert)
    return if (zahl != null) "€ " + formatiereDeutscheZahl(zahl) else "(Rohwert: \"$rohwert\")"
}

/** Ausbezahlter Betrag = FIXUM + PUNKTE − Abzug Masseur − Abzug Sonstiges + Korrektur. */
private fun berechneAusbezahltenBetrag(spieler: SpielerKosten?, korrekturText: String): String {
    if (spieler == null) return ""
    val fixum = parseDeutscheZahl(spieler.fixum) ?: 0.0
    val punkte = parseDeutscheZahl(spieler.punkte) ?: 0.0
    val punkteProMonat = parseDeutscheZahl(spieler.punkteMultiplikator) ?: 0.0
    val punkteBetrag = punkte * punkteProMonat
    val abzugMasseur = parseDeutscheZahl(spieler.abzugMasseur) ?: 0.0
    val abzugSonstiges = parseDeutscheZahl(spieler.abzugSonstiges) ?: 0.0
    val korrektur = parseDeutscheZahl(korrekturText) ?: 0.0
    val summe = fixum + punkteBetrag - abzugMasseur - abzugSonstiges + korrektur
    return formatiereDeutscheZahl(summe)
}

/** Begrenzt die Korrektur-Eingabe auf -2000 bis +2000, erlaubt aber Zwischenzustände beim Tippen. */
private fun begrenzeKorrektur(eingabe: String): String {
    val zahl = parseDeutscheZahl(eingabe) ?: return eingabe
    return when {
        zahl > KORREKTUR_MAX -> KORREKTUR_MAX.toInt().toString()
        zahl < KORREKTUR_MIN -> KORREKTUR_MIN.toInt().toString()
        else -> eingabe
    }
}
