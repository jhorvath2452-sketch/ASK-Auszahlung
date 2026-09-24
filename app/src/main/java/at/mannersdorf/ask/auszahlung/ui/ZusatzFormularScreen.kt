package at.mannersdorf.ask.auszahlung.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.VertragsPdfErsteller
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsTyp
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ausfüllbares Formular für den "Zusatz zur Vereinbarung" (Bonus/Punkteprämie).
 * Gleicher Ablauf wie die Vereinbarung: Entwurf speichern, per PIN-geschütztem
 * Stempel abschließen (PDF erzeugen + hochladen + sperren).
 */
@Composable
fun ZusatzFormularScreen(
    spielerName: String,
    formularId: String?,
    viewModel: MainViewModel,
    onFertig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var formular by remember { mutableStateOf(VertragsFormular(typ = VertragsTyp.ZUSATZ, spielerName = spielerName)) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var arbeitetGerade by remember { mutableStateOf(false) }
    var zeigePinDialog by remember { mutableStateOf(false) }
    var neueUnterschriften by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(formularId) {
        laedt = true
        if (formularId != null) {
            val geladen = viewModel.ladeVertragsformular(formularId)
            if (geladen != null) {
                formular = geladen
            } else {
                fehler = "Formular konnte nicht geladen werden."
            }
        } else {
            formular = VertragsFormular(
                typ = VertragsTyp.ZUSATZ,
                spielerName = spielerName,
                name = spielerName,
                erstelltAm = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
            )
        }
        laedt = false
    }

    val gesperrt = formular.gestempelt

    fun speichereEntwurf() {
        coroutineScope.launch {
            arbeitetGerade = true
            val ergebnis = viewModel.speichereVertragsFormular(formular)
            ergebnis.onSuccess { id ->
                if (formular.id.isBlank()) formular = formular.copy(id = id)
                fehler = null
            }.onFailure {
                fehler = "Speichern fehlgeschlagen: ${it.message}"
            }
            arbeitetGerade = false
        }
    }

    fun stempeln() {
        coroutineScope.launch {
            arbeitetGerade = true
            try {
                val formularIdSicher = formular.id.ifBlank {
                    viewModel.speichereVertragsFormular(formular).getOrThrow()
                }
                var aktuell = formular.copy(id = formularIdSicher)

                for ((feld, base64) in neueUnterschriften) {
                    val pfad = "vertragsunterschriften/$formularIdSicher/$feld.png"
                    val url = viewModel.ladeBildHoch(pfad, base64).getOrThrow()
                    aktuell = when (feld) {
                        "obmann" -> aktuell.copy(unterschriftObmannUrl = url)
                        "spieler" -> aktuell.copy(unterschriftSpielerUrl = url)
                        else -> aktuell.copy(unterschriftSportlicherLeiterUrl = url)
                    }
                }

                val bitmaps = mutableMapOf<String, Bitmap?>()
                val urlProFeld = mapOf(
                    "obmann" to aktuell.unterschriftObmannUrl,
                    "spieler" to aktuell.unterschriftSpielerUrl,
                    "sportlicherLeiter" to aktuell.unterschriftSportlicherLeiterUrl
                )
                for ((feld, url) in urlProFeld) {
                    bitmaps[feld] = if (url.isNotBlank()) {
                        viewModel.ladeUnterschriftBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } else null
                }

                val zeitstempel = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
                val fertig = aktuell.copy(gestempelt = true, gestempeltAm = zeitstempel)
                val pdfDatei = VertragsPdfErsteller.erstellePdf(context, fertig, bitmaps)
                val pdfUrl = viewModel.ladeDateiHoch("vertragsformulare/$formularIdSicher.pdf", pdfDatei.readBytes()).getOrThrow()

                val endgueltig = fertig.copy(fertigesPdfUrl = pdfUrl)
                viewModel.speichereVertragsFormular(endgueltig).getOrThrow()

                formular = endgueltig
                arbeitetGerade = false
                onFertig()
            } catch (e: Exception) {
                fehler = "Stempeln fehlgeschlagen: ${e.message}"
                arbeitetGerade = false
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFertig) { Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück") }
            Text("Zusatz zur Vereinbarung", style = MaterialTheme.typography.titleMedium)
        }

        if (laedt) {
            CircularProgressIndicator(Modifier.padding(16.dp))
            return@Column
        }

        fehler?.let { Snackbar(Modifier.padding(vertical = 8.dp)) { Text(it) } }

        if (gesperrt) {
            Text(
                "Dieses Dokument ist abgeschlossen (gestempelt) und kann nicht mehr geändert werden.",
                color = Color(0xFFB3261E),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        OutlinedTextField(
            value = formular.nummer, onValueChange = { formular = formular.copy(nummer = it) },
            enabled = !gesperrt, label = { Text("Nr. / Referenz zur Vereinbarung") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formular.name, onValueChange = { formular = formular.copy(name = it) },
            enabled = !gesperrt, label = { Text("Spieler") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formular.bonus, onValueChange = { formular = formular.copy(bonus = it) },
            enabled = !gesperrt, label = { Text("Bonus (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formular.siegProPunkt, onValueChange = { formular = formular.copy(siegProPunkt = it) },
            enabled = !gesperrt, label = { Text("Sieg pro Punkt (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formular.unentschieden, onValueChange = { formular = formular.copy(unentschieden = it) },
            enabled = !gesperrt, label = { Text("Unentschieden (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = formular.anmerkungen, onValueChange = { formular = formular.copy(anmerkungen = it) },
            enabled = !gesperrt, label = { Text("Anmerkungen") }, minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        DatumsFeld(
            label = "Datum", wert = formular.datum, aenderbar = !gesperrt,
            onWertGeaendert = { formular = formular.copy(datum = it) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("Unterschriften", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row {
            UnterschriftsButton(
                "Sportlicher Leiter",
                istErledigt = formular.unterschriftSportlicherLeiterUrl.isNotBlank() || neueUnterschriften.containsKey("sportlicherLeiter"),
                aenderbar = !gesperrt,
                onNeueUnterschrift = { neueUnterschriften = neueUnterschriften + ("sportlicherLeiter" to it) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            UnterschriftsButton(
                "Obmann",
                istErledigt = formular.unterschriftObmannUrl.isNotBlank() || neueUnterschriften.containsKey("obmann"),
                aenderbar = !gesperrt,
                onNeueUnterschrift = { neueUnterschriften = neueUnterschriften + ("obmann" to it) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        UnterschriftsButton(
            "Spieler",
            istErledigt = formular.unterschriftSpielerUrl.isNotBlank() || neueUnterschriften.containsKey("spieler"),
            aenderbar = !gesperrt,
            onNeueUnterschrift = { neueUnterschriften = neueUnterschriften + ("spieler" to it) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Row {
            Button(onClick = { speichereEntwurf() }, enabled = !gesperrt && !arbeitetGerade) {
                Text("Speichern")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { zeigePinDialog = true }, enabled = !gesperrt && !arbeitetGerade) {
                Text("Stempeln")
            }
        }
        if (arbeitetGerade) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(24.dp))
    }

    if (zeigePinDialog) {
        PinBestaetigungsDialog(
            titel = "Vertrag stempeln",
            hinweistext = "Nach dem Stempeln kann dieses Dokument nicht mehr geändert werden. PIN eingeben, um fortzufahren.",
            erwarteterPin = VERTRAG_STEMPEL_PIN,
            bestaetigenBeschriftung = "Stempeln",
            onAbbrechen = { zeigePinDialog = false },
            onBestaetigt = {
                zeigePinDialog = false
                stempeln()
            }
        )
    }
}
