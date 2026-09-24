package at.mannersdorf.ask.auszahlung.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.SpielerKosten
import at.mannersdorf.ask.auszahlung.data.model.VertragsDatei
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsTyp
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private sealed class VertraegeAnsicht {
    data object SpielerListe : VertraegeAnsicht()
    data class DateiListe(val spielerName: String) : VertraegeAnsicht()
    data class Formular(val spielerName: String, val typ: VertragsTyp, val formularId: String?) : VertraegeAnsicht()
}

/**
 * Ebene "Verträge": Spielerliste aus Kosten Spielbetrieb, bis zu 5 verknüpfte
 * PDF-Dateien pro Spieler (bestehende hochladen ODER per Formular neu
 * erstellen). "Neuer Vertrag" oben lässt zwischen den zwei Vorlagen wählen.
 */
@Composable
fun VertraegeScreen(
    alleSpieler: List<SpielerKosten>,
    viewModel: MainViewModel,
    angemeldeterBenutzer: at.mannersdorf.ask.auszahlung.data.model.AppBenutzer? = null,
    modifier: Modifier = Modifier
) {
    val istSpieler = angemeldeterBenutzer?.rolle == at.mannersdorf.ask.auszahlung.data.model.Benutzerrolle.SPIELER

    var ansicht by remember { mutableStateOf<VertraegeAnsicht>(VertraegeAnsicht.SpielerListe) }

    // Spieler landen direkt bei ihrer eigenen Dateiliste – nur einmal beim Start
    LaunchedEffect(angemeldeterBenutzer?.benutzername) {
        if (istSpieler && angemeldeterBenutzer != null) {
            ansicht = VertraegeAnsicht.DateiListe(angemeldeterBenutzer.benutzername)
        }
    }

    when (val a = ansicht) {
        is VertraegeAnsicht.SpielerListe -> SpielerListenAnsicht(
            alleSpieler = alleSpieler,
            onSpielerGewaehlt = { ansicht = VertraegeAnsicht.DateiListe(it) },
            modifier = modifier
        )
        is VertraegeAnsicht.DateiListe -> DateiListenAnsicht(
            spielerName = a.spielerName,
            viewModel = viewModel,
            istSpieler = istSpieler,
            onZurueck = { ansicht = VertraegeAnsicht.SpielerListe },
            onNeuesFormular = { typ -> ansicht = VertraegeAnsicht.Formular(a.spielerName, typ, null) },
            onFormularOeffnen = { typ, id -> ansicht = VertraegeAnsicht.Formular(a.spielerName, typ, id) },
            modifier = modifier
        )
        is VertraegeAnsicht.Formular -> {
            if (a.typ == VertragsTyp.VEREINBARUNG) {
                VereinbarungFormularScreen(
                    spielerName = a.spielerName,
                    formularId = a.formularId,
                    viewModel = viewModel,
                    onFertig = { ansicht = VertraegeAnsicht.DateiListe(a.spielerName) },
                    modifier = modifier
                )
            } else {
                ZusatzFormularScreen(
                    spielerName = a.spielerName,
                    formularId = a.formularId,
                    viewModel = viewModel,
                    onFertig = { ansicht = VertraegeAnsicht.DateiListe(a.spielerName) },
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun SpielerListenAnsicht(
    alleSpieler: List<SpielerKosten>,
    onSpielerGewaehlt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val namen = remember(alleSpieler) {
        alleSpieler
            .map { it.name.trim() }
            .filter { name ->
                name.isNotBlank() &&
                !name.contains("freie Zeile", ignoreCase = true) &&
                !name.startsWith("*")
            }
            .distinct()
            .sorted()
    }
    Column(modifier.fillMaxSize()) {
        Text("Verträge", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
        if (namen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Keine Spieler gefunden - zuerst einen Monat mit Daten wählen.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(namen) { name ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { onSpielerGewaehlt(name) }
                    ) {
                        Text(name, Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateiListenAnsicht(
    spielerName: String,
    viewModel: MainViewModel,
    istSpieler: Boolean = false,
    onZurueck: () -> Unit,
    onNeuesFormular: (VertragsTyp) -> Unit,
    onFormularOeffnen: (VertragsTyp, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dateien by remember { mutableStateOf<List<VertragsDatei>>(emptyList()) }
    var formulare by remember { mutableStateOf<List<VertragsFormular>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var zeigeVorlagenAuswahl by remember { mutableStateOf(false) }
    var hochladenLaeuft by remember { mutableStateOf(false) }
    var zumLoeschenFormularId by remember { mutableStateOf<String?>(null) }
    var zumLoeschenDateiId by remember { mutableStateOf<String?>(null) }
    var qrCodeUrl by remember { mutableStateOf<String?>(null) }

    suspend fun neuLaden() {
        laedt = true
        try {
            dateien = viewModel.ladeVertraegeFuerSpieler(spielerName)
        } catch (e: Exception) {
            fehler = "Dateien konnten nicht geladen werden: ${e.message}"
            dateien = emptyList()
        }
        try {
            formulare = viewModel.ladeVertragsformulareFuerSpieler(spielerName)
        } catch (e: Exception) {
            fehler = "Formulare konnten nicht geladen werden: ${e.message}"
            formulare = emptyList()
        }
        laedt = false
    }

    LaunchedEffect(spielerName) {
        try {
            neuLaden()
        } catch (e: Exception) {
            fehler = "Laden fehlgeschlagen: ${e.message}"
            laedt = false
        }
    }

    val dateiAuswahlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (dateien.size >= 5) {
            fehler = "Für $spielerName sind bereits 5 Verträge hinterlegt - erst einen entfernen."
            return@rememberLauncherForActivityResult
        }
        hochladenLaeuft = true
        val dateiName = ermittleDateiName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            fehler = "Datei konnte nicht gelesen werden."
            hochladenLaeuft = false
        } else {
            coroutineScope.launch {
                val ergebnis = viewModel.ladeVertragHoch(spielerName, dateiName, bytes)
                ergebnis.onSuccess { neuLaden() }.onFailure { fehler = it.message }
                hochladenLaeuft = false
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!istSpieler) {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
                Text(spielerName, style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = { zeigeVorlagenAuswahl = true }) {
                Text("Neuer Vertrag")
            }
        }

        fehler?.let {
            Snackbar(Modifier.padding(horizontal = 12.dp)) { Text(it) }
        }

        if (laedt || hochladenLaeuft) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Text(
            "Hochgeladene Dateien (${dateien.size}/5)",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(
                onClick = { dateiAuswahlLauncher.launch(arrayOf("application/pdf")) },
                enabled = dateien.size < 5
            ) {
                Text("Bestehende PDF hochladen")
            }
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            @OptIn(ExperimentalFoundationApi::class)
            items(dateien, key = { it.id }) { datei ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .combinedClickable(
                            onClick = { oeffnePdfUrl(context, datei.downloadUrl) },
                            onLongClick = { zumLoeschenDateiId = datei.id }
                        )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(datei.dateiName, fontWeight = FontWeight.Bold)
                            Text(formatiereZeitstempel(datei.hochgeladenAm), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            @OptIn(ExperimentalFoundationApi::class)
            items(formulare, key = { it.id }) { formular ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                if (formular.fertigesPdfUrl.isNotBlank()) {
                                    oeffnePdfUrl(context, formular.fertigesPdfUrl)
                                } else {
                                    onFormularOeffnen(formular.typ, formular.id)
                                }
                            },
                            onLongClick = { zumLoeschenFormularId = formular.id }
                        )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                val bezeichnung = if (formular.typ == VertragsTyp.VEREINBARUNG) "Vereinbarung ${formular.nummer}" else "Zusatz zur Vereinbarung"
                                Text(bezeichnung, fontWeight = FontWeight.Bold)
                                Text(
                                    if (formular.gestempelt) "Abgeschlossen (gestempelt)" else "Entwurf - noch nicht abgeschlossen",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (formular.gestempelt && formular.fertigesPdfUrl.isNotBlank()) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    teileViaWhatsApp(context, formular.fertigesPdfUrl,
                                        if (formular.typ == VertragsTyp.VEREINBARUNG)
                                            "Vereinbarung ${formular.spielerName} ${formular.nummer}"
                                        else "Zusatz zur Vereinbarung ${formular.spielerName}"
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Via WhatsApp teilen")
                            }
                            IconButton(onClick = { qrCodeUrl = formular.fertigesPdfUrl }) {
                                Icon(Icons.Filled.QrCode, contentDescription = "QR-Code anzeigen")
                            }
                        }
                    }
                }
            }
        }
    }

    // QR-Code-Dialog
    qrCodeUrl?.let { url ->
        QrCodeDialog(url = url, onSchliessen = { qrCodeUrl = null })
    }

    // Lösch-Dialog für hochgeladene Dateien
    zumLoeschenDateiId?.let { dateiId ->
        var pin by remember { mutableStateOf("") }
        var bestaetigt by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { zumLoeschenDateiId = null },
            title = { Text("Datei löschen?") },
            text = {
                Column {
                    Text("Diese hochgeladene Datei wirklich löschen?")
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it; bestaetigt = false },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = bestaetigt, onCheckedChange = { bestaetigt = it })
                        Text("Ja, wirklich löschen")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = bestaetigt && pin == "24521919",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.loescheVertrag(dateiId)
                            neuLaden()
                        }
                        zumLoeschenDateiId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { zumLoeschenDateiId = null }) { Text("Abbrechen") } }
        )
    }

    // Lösch-Dialog für Vertragsformulare
    zumLoeschenFormularId?.let { formularId ->
        var pin by remember { mutableStateOf("") }
        var bestaetigt by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { zumLoeschenFormularId = null },
            title = { Text("Vertrag löschen?") },
            text = {
                Column {
                    Text("Diesen Vertragsentwurf wirklich löschen?")
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            bestaetigt = false
                        },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Checkbox(checked = bestaetigt, onCheckedChange = { bestaetigt = it })
                        Text("Ja, wirklich löschen")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = bestaetigt && pin == "24521919",
                    onClick = {
                        coroutineScope.launch {
                            viewModel.loescheVertragsformular(formularId)
                            neuLaden()
                        }
                        zumLoeschenFormularId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { zumLoeschenFormularId = null }) { Text("Abbrechen") }
            }
        )
    }

    if (zeigeVorlagenAuswahl) {        AlertDialog(
            onDismissRequest = { zeigeVorlagenAuswahl = false },
            title = { Text("Neuer Vertrag") },
            text = { Text("Welche Vorlage soll ausgefüllt werden?") },
            confirmButton = {
                TextButton(onClick = {
                    zeigeVorlagenAuswahl = false
                    onNeuesFormular(VertragsTyp.VEREINBARUNG)
                }) { Text("Vereinbarung") }
            },
            dismissButton = {
                TextButton(onClick = {
                    zeigeVorlagenAuswahl = false
                    onNeuesFormular(VertragsTyp.ZUSATZ)
                }) { Text("Zusatz zur Vereinbarung") }
            }
        )
    }
}

private fun ermittleDateiName(context: android.content.Context, uri: Uri): String {
    var name = "Vertrag.pdf"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(index) ?: name
        }
    }
    return name
}

private fun oeffnePdfUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/**
 * Lädt das PDF von der Firebase-Storage-URL herunter und teilt es direkt via
 * WhatsApp. Ist WhatsApp nicht installiert, öffnet sich der normale Android-
 * Teilen-Dialog als Fallback.
 */
private suspend fun teileViaWhatsApp(context: android.content.Context, pdfUrl: String, betreff: String) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // PDF von URL herunterladen
            val verbindung = java.net.URL(pdfUrl).openConnection() as java.net.HttpURLConnection
            verbindung.connect()
            val bytes = verbindung.inputStream.use { it.readBytes() }
            verbindung.disconnect()

            // In Cache schreiben
            val dateiName = "Vertrag_${System.currentTimeMillis()}.pdf"
            val datei = java.io.File(context.cacheDir, dateiName)
            datei.writeBytes(bytes)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", datei
            )

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // Zuerst WhatsApp direkt versuchen
                val whatsAppIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    setPackage("com.whatsapp")
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, betreff)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val whatsAppBusiness = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    setPackage("com.whatsapp.w4b")
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, betreff)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val pm = context.packageManager
                val whatsAppVerfuegbar = pm.getLaunchIntentForPackage("com.whatsapp") != null
                val whatsAppBizVerfuegbar = pm.getLaunchIntentForPackage("com.whatsapp.w4b") != null

                when {
                    whatsAppVerfuegbar && whatsAppBizVerfuegbar -> {
                        // Beide installiert → Auswahl anbieten
                        val chooser = android.content.Intent.createChooser(whatsAppIntent, "Teilen via").apply {
                            putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, arrayOf(whatsAppBusiness))
                        }
                        context.startActivity(chooser)
                    }
                    whatsAppVerfuegbar -> context.startActivity(whatsAppIntent)
                    whatsAppBizVerfuegbar -> context.startActivity(whatsAppBusiness)
                    else -> {
                        // Fallback: normaler Teilen-Dialog
                        val fallback = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, betreff)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(fallback, "Vertrag teilen"))
                    }
                }
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // Fehler – direkt URL öffnen als letzter Ausweg
                oeffnePdfUrl(context, pdfUrl)
            }
        }
    }
}

private fun formatiereZeitstempel(millisText: String): String {
    val millis = millisText.toLongOrNull() ?: return millisText
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(millis))
}
