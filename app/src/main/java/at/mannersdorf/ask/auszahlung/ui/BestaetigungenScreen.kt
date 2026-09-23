package at.mannersdorf.ask.auszahlung.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.PdfErsteller
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung

private const val ALLE_FILTER = "Alle"

private fun jahrAusMonat(monat: String): String =
    Regex("(20\\d{2})").find(monat)?.value ?: ALLE_FILTER

private fun monatsNameOhneJahr(monat: String): String {
    val ohneJahr = monat.replace(Regex("20\\d{2}"), "").trim()
    return ohneJahr.ifBlank { monat }
}

/**
 * Ebene 4: Liste der bereits gespeicherten (unterschriebenen) Bestätigungen aus
 * Firebase. Nach Name, Jahr und Monat einzeln oder kombiniert filterbar.
 * Antippen öffnet die Details inkl. Unterschrift, von dort aus lässt sich ein
 * PDF erzeugen und über den normalen Android-Teilen-Dialog per Mail
 * verschicken, speichern oder in eine andere App übergeben.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BestaetigungenScreen(
    bestaetigungen: List<GespeicherteBestaetigung>,
    fehler: String?,
    onAktualisieren: () -> Unit,
    onLoeschen: (String) -> Unit,
    ladeUnterschrift: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    var ausgewaehlt by remember { mutableStateOf<GespeicherteBestaetigung?>(null) }
    var zumLoeschen by remember { mutableStateOf<GespeicherteBestaetigung?>(null) }
    var nameFilter by remember { mutableStateOf("") }
    var jahrFilter by remember { mutableStateOf(ALLE_FILTER) }
    var monatFilter by remember { mutableStateOf(ALLE_FILTER) }

    val verfuegbareJahre = remember(bestaetigungen) {
        listOf(ALLE_FILTER) + bestaetigungen.map { jahrAusMonat(it.monat) }.distinct().sorted()
    }
    val verfuegbareMonate = remember(bestaetigungen) {
        listOf(ALLE_FILTER) + bestaetigungen.map { monatsNameOhneJahr(it.monat) }.distinct().sorted()
    }

    val gefilterteListe = bestaetigungen.filter { b ->
        (nameFilter.isBlank() || b.spielerName.contains(nameFilter, ignoreCase = true)) &&
            (jahrFilter == ALLE_FILTER || jahrAusMonat(b.monat) == jahrFilter) &&
            (monatFilter == ALLE_FILTER || monatsNameOhneJahr(b.monat) == monatFilter)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bestätigungen", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onAktualisieren) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
            }
        }

        OutlinedTextField(
            value = nameFilter,
            onValueChange = { nameFilter = it },
            label = { Text("Nach Name filtern") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            MonatsDropdown(
                label = "Jahr",
                monate = verfuegbareJahre,
                gewaehlterMonat = jahrFilter,
                onMonatGewaehlt = { jahrFilter = it },
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            )
            MonatsDropdown(
                label = "Monat",
                monate = verfuegbareMonate,
                gewaehlterMonat = monatFilter,
                onMonatGewaehlt = { monatFilter = it },
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
        }

        fehler?.let {
            Snackbar(Modifier.padding(horizontal = 12.dp)) { Text(it) }
        }

        Text(
            "Zum Löschen eine Bestätigung lange gedrückt halten",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        if (gefilterteListe.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    if (bestaetigungen.isEmpty()) "Noch keine gespeicherten Bestätigungen."
                    else "Keine Bestätigungen für diesen Filter gefunden."
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(gefilterteListe, key = { it.id }) { b ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = { ausgewaehlt = b },
                                onLongClick = { zumLoeschen = b }
                            )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(b.spielerName, fontWeight = FontWeight.Bold)
                            Text("Monat: ${b.monat}  ·  Betrag: € ${b.betragErhalten}")
                            Text(
                                b.erstelltAm,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    ausgewaehlt?.let { b ->
        BestaetigungsDetailDialog(
            bestaetigung = b,
            ladeUnterschrift = ladeUnterschrift,
            onSchliessen = { ausgewaehlt = null }
        )
    }

    zumLoeschen?.let { b ->
        LoeschBestaetigungsDialog(
            bestaetigung = b,
            onAbbrechen = { zumLoeschen = null },
            onLoeschenBestaetigt = {
                onLoeschen(b.id)
                zumLoeschen = null
            }
        )
    }
}

/**
 * Zwei-Schritte-Bestätigung fürs Löschen: erst Ja-Kästchen ankreuzen, dann
 * erst wird der Löschen-Button aktiv. Landet nicht sofort im Nichts, sondern
 * 40 Tage im Papierkorb (siehe FirebaseRepository).
 */
@Composable
private fun LoeschBestaetigungsDialog(
    bestaetigung: GespeicherteBestaetigung,
    onAbbrechen: () -> Unit,
    onLoeschenBestaetigt: () -> Unit
) {
    var bestaetigt by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Bestätigung löschen?") },
        text = {
            Column {
                Text("${bestaetigung.spielerName} – ${bestaetigung.monat} wirklich löschen?")
                Text(
                    "Liegt danach noch 40 Tage im Papierkorb, bevor sie endgültig entfernt wird.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = bestaetigt, onCheckedChange = { bestaetigt = it })
                    Text("Ja, wirklich löschen")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = bestaetigt,
                onClick = onLoeschenBestaetigt,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) {
                Text("Löschen")
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun BestaetigungsDetailDialog(
    bestaetigung: GespeicherteBestaetigung,
    ladeUnterschrift: suspend (String) -> ByteArray?,
    onSchliessen: () -> Unit
) {
    val context = LocalContext.current
    var unterschriftBytes by remember(bestaetigung.id) { mutableStateOf<ByteArray?>(null) }
    var wirdGeladen by remember(bestaetigung.id) { mutableStateOf(true) }

    LaunchedEffect(bestaetigung.id) {
        unterschriftBytes = ladeUnterschrift(bestaetigung.unterschriftUrl)
        wirdGeladen = false
    }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text(bestaetigung.spielerName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailZeile("Monat", bestaetigung.monat)
                DetailZeile("Fixum", bestaetigung.fixum)
                DetailZeile("Punkte", bestaetigung.punkte)
                DetailZeile("Abzug Masseur", bestaetigung.abzugMasseur)
                DetailZeile("Abzug Sonstiges", bestaetigung.abzugSonstiges)
                DetailZeile("Korrektur", bestaetigung.korrektur)
                DetailZeile("Ausbezahlter Betrag", "€ ${bestaetigung.betragErhalten}")
                if (bestaetigung.bemerkung.isNotBlank()) {
                    DetailZeile("Bemerkung", bestaetigung.bemerkung)
                }
                DetailZeile("Erstellt am", bestaetigung.erstelltAm)

                Spacer(Modifier.height(8.dp))
                Text("Unterschrift:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                when {
                    wirdGeladen -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    unterschriftBytes != null -> {
                        val bytes = unterschriftBytes!!
                        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Unterschrift",
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                    else -> Text("Unterschrift konnte nicht geladen werden.")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val bytes = unterschriftBytes
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                val datei = PdfErsteller.erstellePdf(context, bestaetigung, bitmap)
                PdfErsteller.teilePdf(
                    context,
                    datei,
                    "Auszahlungsbestätigung ${bestaetigung.spielerName} ${bestaetigung.monat}"
                )
            }) {
                Text("Als PDF teilen / per Mail senden")
            }
        },
        dismissButton = {
            TextButton(onClick = onSchliessen) { Text("Schließen") }
        }
    )
}

@Composable
private fun DetailZeile(label: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, fontWeight = FontWeight.Medium)
    }
}
