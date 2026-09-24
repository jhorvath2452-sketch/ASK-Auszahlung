package at.mannersdorf.ask.auszahlung.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.mannersdorf.ask.auszahlung.R
import at.mannersdorf.ask.auszahlung.data.PdfErsteller
import at.mannersdorf.ask.auszahlung.data.SettingsStore
import at.mannersdorf.ask.auszahlung.ui.theme.SchreibmaschinenSchrift
import at.mannersdorf.ask.auszahlung.ui.theme.VereinsGruen
import at.mannersdorf.ask.auszahlung.viewmodel.Ebene
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    angemeldeterBenutzer: at.mannersdorf.ask.auszahlung.data.model.AppBenutzer,
    userStore: at.mannersdorf.ask.auszahlung.data.UserStore,
    onAbmelden: () -> Unit
) {
    val zustand by viewModel.zustand.collectAsState()
    var zeigeEinstellungen by remember { mutableStateOf(false) }
    var zeigeBenutzerverwaltung by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val istAdmin = angemeldeterBenutzer.rolle == at.mannersdorf.ask.auszahlung.data.model.Benutzerrolle.ADMINS

    // Wurde die App über eine Push-Benachrichtigung geöffnet (siehe
    // MainActivity), lädt dieser Effekt die betreffende Bestätigung samt
    // Unterschrift und öffnet sie automatisch als PDF über den normalen
    // Android-Teilen-Dialog.
    LaunchedEffect(zustand.ausPushZuOeffnendeBestaetigungId) {
        val id = zustand.ausPushZuOeffnendeBestaetigungId ?: return@LaunchedEffect
        val bestaetigung = viewModel.ladeBestaetigungFuerPush(id)
        if (bestaetigung != null) {
            val unterschriftBytes = viewModel.ladeUnterschriftBytes(bestaetigung.unterschriftUrl)
            val bitmap = unterschriftBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            val datei = PdfErsteller.erstellePdf(context, bestaetigung, bitmap)
            PdfErsteller.teilePdf(
                context,
                datei,
                "Auszahlungsbestätigung ${bestaetigung.spielerName} ${bestaetigung.monat}"
            )
        }
        viewModel.bestaetigungAusPushGeoeffnet()
    }

    Scaffold(
        topBar = {
            AppKopfzeile(
                zeigtEinstellungen = zeigeEinstellungen,
                bereit = zustand.bereit,
                istAdmin = istAdmin,
                benutzername = angemeldeterBenutzer.benutzername,
                onZurueck = { zeigeEinstellungen = false; zeigeBenutzerverwaltung = false },
                onEinstellungen = { zeigeEinstellungen = true },
                onBenutzerverwaltung = { zeigeBenutzerverwaltung = true },
                onAbmelden = onAbmelden
            )
        }
    ) { innenAbstand ->
        Box(Modifier.padding(innenAbstand).fillMaxSize()) {
            FussballplatzHintergrund(Modifier.fillMaxSize())

            when {
                zeigeBenutzerverwaltung -> BenutzerverwaltungScreen(
                    userStore = userStore,
                    onZurueck = { zeigeBenutzerverwaltung = false }
                )
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
                            Spacer(Modifier.padding(top = 16.dp))
                            Text("Wird geladen …", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleChoiceSegmentedButtonRow(Modifier.height(40.dp)) {
                                SegmentedButton(
                                    selected = zustand.trainingslisteSheetId != SettingsStore.TEST_TRAININGSLISTE_ID,
                                    onClick = { viewModel.waehleDatensatz(test = false) },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                                ) { Text("2026-27") }
                                SegmentedButton(
                                    selected = zustand.trainingslisteSheetId == SettingsStore.TEST_TRAININGSLISTE_ID,
                                    onClick = { viewModel.waehleDatensatz(test = true) },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                                ) { Text("TEST") }
                            }
                            if (zustand.aktiveEbene == Ebene.TRAININGSLISTE) {
                                TrainingsInfoButton()
                            }
                        }

                        if (zustand.verfuegbareMonate.isNotEmpty()) {
                            MonatsDropdown(
                                label = "Monat",
                                monate = zustand.verfuegbareMonate,
                                gewaehlterMonat = zustand.gewaehlterMonat,
                                onMonatGewaehlt = viewModel::waehleMonat,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        ScrollableTabRow(
                            selectedTabIndex = Ebene.entries.indexOf(zustand.aktiveEbene),
                            edgePadding = 12.dp
                        ) {
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.TRAININGSLISTE,
                                onClick = { viewModel.wechsleEbene(Ebene.TRAININGSLISTE) },
                                text = { Text("Training") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.KOSTEN_SPIELBETRIEB,
                                onClick = { viewModel.wechsleEbene(Ebene.KOSTEN_SPIELBETRIEB) },
                                text = { Text("Kosten") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.SPIELER,
                                onClick = { viewModel.wechsleEbene(Ebene.SPIELER) },
                                text = { Text("Spieler") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.MASSEUR,
                                onClick = { viewModel.wechsleEbene(Ebene.MASSEUR) },
                                text = { Text("Masseur") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.TORMANNTRAINER,
                                onClick = { viewModel.wechsleEbene(Ebene.TORMANNTRAINER) },
                                text = { Text("Tormanntrainer extra") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.BETREUUNG,
                                onClick = { viewModel.wechsleEbene(Ebene.BETREUUNG) },
                                text = { Text("Betreuung") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.VERTRAEGE,
                                onClick = { viewModel.wechsleEbene(Ebene.VERTRAEGE) },
                                text = { Text("Verträge") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.STATISTIK,
                                onClick = { viewModel.wechsleEbene(Ebene.STATISTIK) },
                                text = { Text("Statistik") }
                            )
                            Tab(
                                selected = zustand.aktiveEbene == Ebene.BESTAETIGUNGEN,
                                onClick = { viewModel.wechsleEbene(Ebene.BESTAETIGUNGEN) },
                                text = { Text("Bestätigung") }
                            )
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
                            Ebene.MASSEUR -> MasseurScreen(
                                monat = zustand.gewaehlterMonat,
                                alleSpieler = zustand.kostenSpielbetrieb?.spieler ?: emptyList(),
                                gewaehlterName = zustand.gewaehlterMasseur,
                                speichernErfolgreich = zustand.speichernErfolgreich,
                                onAusgewaehlt = viewModel::waehleMasseur,
                                onDatenUebernehmen = viewModel::speichereMasseurAuszahlung,
                                modifier = Modifier.fillMaxSize()
                            )
                            Ebene.TORMANNTRAINER -> TormanntrainerScreen(
                                monat = zustand.gewaehlterMonat,
                                alleSpieler = zustand.kostenSpielbetrieb?.spieler ?: emptyList(),
                                gewaehlterName = zustand.gewaehlterTormanntrainer,
                                speichernErfolgreich = zustand.speichernErfolgreich,
                                onAusgewaehlt = viewModel::waehleTormanntrainer,
                                onDatenUebernehmen = viewModel::speichereTormanntrainerAuszahlung,
                                modifier = Modifier.fillMaxSize()
                            )
                            Ebene.BETREUUNG -> BetreuungScreen(
                                monat = zustand.gewaehlterMonat,
                                alleSpieler = zustand.kostenSpielbetrieb?.spieler ?: emptyList(),
                                gewaehlterName = zustand.gewaehlterBetreuer,
                                speichernErfolgreich = zustand.speichernErfolgreich,
                                onAusgewaehlt = viewModel::waehleBetreuer,
                                onDatenUebernehmen = viewModel::speichereBetreuungAuszahlung,
                                modifier = Modifier.fillMaxSize()
                            )
                            Ebene.VERTRAEGE -> VertraegeScreen(
                                alleSpieler = zustand.kostenSpielbetrieb?.spieler ?: emptyList(),
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                            Ebene.STATISTIK -> StatistikScreen(
                                namen = zustand.statistikNamen,
                                gewaehlterName = zustand.statistikGewaehlterName,
                                gewaehlteSaison = zustand.statistikGewaehlteSaison,
                                gewaehlteMonate = zustand.statistikGewaehlteMonate,
                                ergebnisse = zustand.statistikErgebnisse,
                                fehler = zustand.statistikLadenFehler,
                                onNameGewaehlt = viewModel::waehleStatistikName,
                                onSaisonGewaehlt = viewModel::waehleStatistikSaison,
                                onMonateGewaehlt = viewModel::waehleStatistikMonate,
                                modifier = Modifier.fillMaxSize()
                            )
                            Ebene.BESTAETIGUNGEN -> BestaetigungenScreen(
                                bestaetigungen = zustand.bestaetigungen,
                                fehler = zustand.bestaetigungenLadenFehler,
                                onAktualisieren = viewModel::ladeBestaetigungen,
                                onLoeschen = viewModel::loescheBestaetigung,
                                ladeUnterschrift = viewModel::ladeUnterschriftBytes,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Eigene, größere Kopfzeile statt der Standard-TopAppBar: ASK-Wappen links
 * (100dp, vertikal mittig), mittig groß "ASK MANNERSDORF" mit "#manaschdooaaf"
 * darunter (beide etwa halbe Kopfzeilenhöhe), "©chigo2452" groß unten rechts
 * in der Ecke, dezente Fußballplatz-Linien im Hintergrund.
 */
@Composable
private fun AppKopfzeile(
    zeigtEinstellungen: Boolean,
    bereit: Boolean,
    istAdmin: Boolean,
    benutzername: String,
    onZurueck: () -> Unit,
    onEinstellungen: () -> Unit,
    onBenutzerverwaltung: () -> Unit,
    onAbmelden: () -> Unit
) {
    Box(Modifier.fillMaxWidth().background(VereinsGruen)) {
        FussballplatzHintergrund(Modifier.matchParentSize())

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ask_wappen),
                contentDescription = "ASK Mannersdorf Wappen",
                modifier = Modifier.height(100.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (zeigtEinstellungen) "EINSTELLUNGEN" else "ASK MANNERSDORF AUSZAHLUNG",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!zeigtEinstellungen) {
                    Text(
                        "#manaschdooaaf",
                        color = Color.White,
                        fontFamily = SchreibmaschinenSchrift,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    zeigtEinstellungen -> IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                    }
                    bereit -> {
                        if (istAdmin) {
                            IconButton(onClick = onBenutzerverwaltung) {
                                Icon(Icons.Filled.Person, contentDescription = "Benutzerverwaltung", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onEinstellungen) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen", tint = Color.White)
                        }
                        IconButton(onClick = onAbmelden) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Abmelden", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (!zeigtEinstellungen) {
            Text(
                "©chigo2452",
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 6.dp)
            )
        }
    }
}
