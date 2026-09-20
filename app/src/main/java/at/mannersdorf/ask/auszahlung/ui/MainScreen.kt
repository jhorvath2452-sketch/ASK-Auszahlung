package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.viewmodel.Ebene
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val zustand by viewModel.zustand.collectAsState()
    var zeigeEinstellungen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (zeigeEinstellungen) "Einstellungen" else "ASK Auszahlung") },
                navigationIcon = {
                    if (zeigeEinstellungen) {
                        IconButton(onClick = { zeigeEinstellungen = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (!zeigeEinstellungen && zustand.bereit) {
                        IconButton(onClick = { zeigeEinstellungen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    }
                }
            )
        }
    ) { innenAbstand ->
        Box(Modifier.padding(innenAbstand).fillMaxSize()) {
            when {
                zeigeEinstellungen -> {
                    SettingsScreen(
                        trainingslisteId = zustand.trainingslisteSheetId,
                        kostenSpielbetriebId = zustand.kostenSpielbetriebSheetId,
                        bestaetigungId = zustand.bestaetigungSheetId,
                        spaltenZuordnung = zustand.spaltenZuordnung,
                        onSheetIdsSpeichern = viewModel::setSheetIds,
                        onSpaltenSpeichern = viewModel::setSpaltenZuordnung
                    )
                }

                !zustand.bereit -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                            Text("Wird geladen …", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (zustand.verfuegbareMonate.isNotEmpty()) {
                            MonatsAuswahl(
                                monate = zustand.verfuegbareMonate,
                                gewaehlterMonat = zustand.gewaehlterMonat,
                                onMonatGewaehlt = viewModel::waehleMonat
                            )
                        }

                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            SegmentedButton(
                                selected = zustand.aktiveEbene == Ebene.TRAININGSLISTE,
                                onClick = { viewModel.wechsleEbene(Ebene.TRAININGSLISTE) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(0, 3)
                            ) { Text("Training") }
                            SegmentedButton(
                                selected = zustand.aktiveEbene == Ebene.KOSTEN_SPIELBETRIEB,
                                onClick = { viewModel.wechsleEbene(Ebene.KOSTEN_SPIELBETRIEB) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(1, 3)
                            ) { Text("Kosten") }
                            SegmentedButton(
                                selected = zustand.aktiveEbene == Ebene.SPIELER,
                                onClick = { viewModel.wechsleEbene(Ebene.SPIELER) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(2, 3)
                            ) { Text("Spieler") }
                        }

                        zustand.fehler?.let { fehlertext ->
                            Snackbar(modifier = Modifier.padding(12.dp)) { Text(fehlertext) }
                        }

                        if (zustand.ladeVorgang) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        when (zustand.aktiveEbene) {
                            Ebene.TRAININGSLISTE -> TrainingslisteScreen(zustand.trainingsliste, Modifier.fillMaxSize())
                            Ebene.KOSTEN_SPIELBETRIEB -> KostenSpielbetriebScreen(zustand.kostenSpielbetrieb, Modifier.fillMaxSize())
                            Ebene.SPIELER -> AuszahlungScreen(
                                monat = zustand.gewaehlterMonat,
                                spielerListe = zustand.kostenSpielbetrieb?.spieler ?: emptyList(),
                                gewaehlterSpielerName = zustand.gewaehlterSpieler,
                                speichernErfolgreich = zustand.speichernErfolgreich,
                                onSpielerGewaehlt = viewModel::waehleSpieler,
                                onDatenUebernehmen = viewModel::speichereAuszahlung,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonatsAuswahl(monate: List<String>, gewaehlterMonat: String?, onMonatGewaehlt: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = offen,
        onExpandedChange = { offen = it },
        modifier = Modifier.padding(12.dp)
    ) {
        OutlinedTextField(
            value = gewaehlterMonat ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Monat") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = offen) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            monate.forEach { monat ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(monat) },
                    onClick = {
                        onMonatGewaehlt(monat)
                        offen = false
                    }
                )
            }
        }
    }
}
