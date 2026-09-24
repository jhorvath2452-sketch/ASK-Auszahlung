package at.mannersdorf.ask.auszahlung.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.mannersdorf.ask.auszahlung.data.FirebaseRepository
import at.mannersdorf.ask.auszahlung.data.SettingsStore
import at.mannersdorf.ask.auszahlung.data.SheetsRepository
import at.mannersdorf.ask.auszahlung.data.model.Auszahlungsbestaetigung
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung
import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten
import at.mannersdorf.ask.auszahlung.data.model.SpaltenZuordnung
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteDaten
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsDatei
import at.mannersdorf.ask.auszahlung.data.model.istBetreuung
import at.mannersdorf.ask.auszahlung.data.model.istMasseur
import at.mannersdorf.ask.auszahlung.data.model.istTormanntrainer
import at.mannersdorf.ask.auszahlung.data.parseDeutscheZahl
import at.mannersdorf.ask.auszahlung.data.formatiereDeutscheZahl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Ebene { TRAININGSLISTE, KOSTEN_SPIELBETRIEB, SPIELER, MASSEUR, TORMANNTRAINER, BETREUUNG, VERTRAEGE, STATISTIK, BESTAETIGUNGEN }

/** Alle Monate einer Saison, in chronologischer Reihenfolge (Dezember/Winterpause bewusst ausgelassen). */
val SAISON_MONATE = listOf(
    "Jänner2026", "Februar2026", "März2026", "April2026", "Mai2026",
    "Juni2026", "Juli2026", "August2026", "September2026", "Oktober2026", "November2026"
)
val VERFUEGBARE_SAISONEN = listOf("2026-27", "TEST")

data class HauptZustand(
    /** true, sobald die anonyme Firebase-Anmeldung + der erste Datenabruf durch sind. */
    val bereit: Boolean = false,
    val aktiveEbene: Ebene = Ebene.TRAININGSLISTE,
    val ladeVorgang: Boolean = false,
    val fehler: String? = null,

    // EIN gemeinsamer Monat für beide Tabellen, da gleiche Monate zusammengehören
    // (z.B. "Juli 2026" in der Trainingsliste = "Juli 2026" bei Kosten Spielbetrieb).
    // Die Liste enthält nur Tab-Namen, die in BEIDEN Tabellen existieren - reine
    // Übersichts-Tabs wie "GESAMT", die es nur in einer der beiden Tabellen gibt,
    // fallen damit automatisch raus.
    val verfuegbareMonate: List<String> = emptyList(),
    val gewaehlterMonat: String? = null,

    val trainingsliste: TrainingslisteDaten? = null,
    val kostenSpielbetrieb: KostenSpielbetriebDaten? = null,

    val gewaehlterSpieler: String? = null,
    val gewaehlterMasseur: String? = null,
    val gewaehlterTormanntrainer: String? = null,
    val gewaehlterBetreuer: String? = null,
    val speichernErfolgreich: Boolean = false,
    val trainingslisteSheetId: String = "",
    val kostenSpielbetriebSheetId: String = "",
    val bestaetigungSheetId: String = "",
    val spaltenZuordnung: SpaltenZuordnung = SpaltenZuordnung(),

    // Ebene 4: bereits gespeicherte Bestätigungen (aus Firestore).
    val bestaetigungen: List<GespeicherteBestaetigung> = emptyList(),
    val bestaetigungenLadenFehler: String? = null,

    // Ebene "Statistik": Name+Saison bestimmen die geladenen Bestätigungen,
    // die Monatsauswahl filtert rein clientseitig (keine neue Abfrage nötig).
    val statistikNamen: List<String> = emptyList(),
    val statistikGewaehlterName: String? = null,
    val statistikGewaehlteSaison: String = VERFUEGBARE_SAISONEN.first(),
    val statistikGewaehlteMonate: Set<String> = SAISON_MONATE.toSet(),
    val statistikErgebnisse: List<GespeicherteBestaetigung> = emptyList(),
    val statistikLadenFehler: String? = null,

    // Gesetzt, wenn die App über eine Push-Benachrichtigung geöffnet wurde -
    // dann soll die betreffende Bestätigung automatisch als PDF geöffnet werden.
    val ausPushZuOeffnendeBestaetigungId: String? = null
)

/**
 * Kein Google-Login mehr nötig: Die Sheets werden serverseitig über die Cloud
 * Function "sheetsProxy" gelesen (siehe SheetsRepository/README), die App
 * meldet sich dafür nur automatisch anonym bei Firebase an.
 */
class MainViewModel(private val context: Context) : ViewModel() {

    private val settingsStore = SettingsStore(context)
    private val sheetsRepository = SheetsRepository()
    private val firebaseRepository = FirebaseRepository()

    private val _zustand = MutableStateFlow(HauptZustand())
    val zustand: StateFlow<HauptZustand> = _zustand

    init {
        viewModelScope.launch {
            try {
                firebaseRepository.stelleSicherAngemeldet()
                ladeGespeicherteEinstellungen()
                ladeMonatsListe()
            } catch (e: Exception) {
                _zustand.value = _zustand.value.copy(fehler = "Anmeldung fehlgeschlagen: ${e.message}")
            } finally {
                _zustand.value = _zustand.value.copy(bereit = true)
            }
        }
    }

    private suspend fun ladeGespeicherteEinstellungen() {
        _zustand.value = _zustand.value.copy(
            trainingslisteSheetId = settingsStore.trainingslisteSheetId.first(),
            kostenSpielbetriebSheetId = settingsStore.kostenSpielbetriebSheetId.first(),
            bestaetigungSheetId = settingsStore.bestaetigungSheetId.first(),
            spaltenZuordnung = settingsStore.spaltenZuordnung.first()
        )
    }

    fun wechsleEbene(ebene: Ebene) {
        _zustand.value = _zustand.value.copy(aktiveEbene = ebene)
        if (ebene == Ebene.BESTAETIGUNGEN && _zustand.value.bestaetigungen.isEmpty()) {
            ladeBestaetigungen()
        }
        if (ebene == Ebene.STATISTIK && _zustand.value.statistikNamen.isEmpty()) {
            ladeStatistikNamen()
        }
    }

    fun ladeBestaetigungen() {
        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.leseBestaetigungen()
            ergebnis.onSuccess { liste ->
                _zustand.value = _zustand.value.copy(bestaetigungen = liste, bestaetigungenLadenFehler = null)
            }.onFailure {
                _zustand.value = _zustand.value.copy(bestaetigungenLadenFehler = "Bestätigungen konnten nicht geladen werden: ${it.message}")
            }
            setLaden(false)
        }
    }

    /** Für Ebene 4: lädt die Unterschrift-PNG-Bytes einer gespeicherten Bestätigung bei Bedarf. */
    suspend fun ladeUnterschriftBytes(unterschriftUrl: String): ByteArray? =
        firebaseRepository.leseUnterschriftBytes(unterschriftUrl).getOrNull()

    // ---------- Verträge ----------

    suspend fun ladeVertraegeFuerSpieler(spielerName: String) =
        firebaseRepository.leseVertraegeFuerSpieler(spielerName).getOrDefault(emptyList())

    suspend fun ladeVertragHoch(spielerName: String, dateiName: String, bytes: ByteArray) =
        firebaseRepository.ladeVertragHoch(spielerName, dateiName, bytes)

    suspend fun ladeVertragsformulareFuerSpieler(spielerName: String) =
        firebaseRepository.leseVertragsformulareFuerSpieler(spielerName).getOrDefault(emptyList())

    suspend fun ladeVertragsformular(id: String) =
        firebaseRepository.leseVertragsformular(id).getOrNull()

    suspend fun speichereVertragsFormular(formular: VertragsFormular) =
        firebaseRepository.speichereVertragsFormular(formular)

    suspend fun ermittleNaechsteVereinbarungsNummer(jahr: Int) =
        firebaseRepository.ermittleNaechsteVereinbarungsNummer(jahr).getOrNull()

    suspend fun speichereVereinbarungsNummer(nummer: String, jahr: Int, spielerName: String) =
        firebaseRepository.speichereVereinbarungsNummer(nummer, jahr, spielerName)

    suspend fun ladeBildHoch(pfad: String, base64Png: String) =
        firebaseRepository.ladeBildHoch(pfad, base64Png)

    suspend fun ladeDateiHoch(pfad: String, bytes: ByteArray) =
        firebaseRepository.ladeDateiHoch(pfad, bytes)

    // ---------- Verträge ----------

    suspend fun ladeVertraegeFuerSpieler(spielerName: String): Result<List<VertragsDatei>> =
        firebaseRepository.leseVertraegeFuerSpieler(spielerName)

    suspend fun ladeVertragHoch(spielerName: String, dateiName: String, bytes: ByteArray): Result<Unit> =
        firebaseRepository.ladeVertragHoch(spielerName, dateiName, bytes)

    /** Unverbindliche Vorschau der nächsten Vereinbarungs-Nummer für das aktuelle Jahr. */
    suspend fun holeVorschauVereinbarungsNummer(): String {
        val jahr = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        return firebaseRepository.ermittleNaechsteVereinbarungsNummer(jahr).getOrDefault("$jahr#001")
    }

    /** Reserviert die Nummer endgültig, lädt das PDF hoch und verknüpft es mit dem Spieler. */
    suspend fun schliesseVereinbarungAb(nummer: String, spielerName: String, pdfBytes: ByteArray): Result<Unit> {
        val jahr = nummer.substringBefore("#").toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val nummerErgebnis = firebaseRepository.speichereVereinbarungsNummer(nummer, jahr, spielerName)
        if (nummerErgebnis.isFailure) return nummerErgebnis
        val dateiName = "Vereinbarung_$nummer.pdf"
        return firebaseRepository.ladeVertragHoch(spielerName, dateiName, pdfBytes)
    }

    suspend fun schliesseZusatzAb(spielerName: String, pdfBytes: ByteArray): Result<Unit> {
        val dateiName = "Zusatz_${spielerName}_${System.currentTimeMillis()}.pdf"
        return firebaseRepository.ladeVertragHoch(spielerName, dateiName, pdfBytes)
    }

    // ---------- Statistik ----------

    fun ladeStatistikNamen() {
        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.leseAlleBekanntenNamen()
            ergebnis.onSuccess { namen ->
                _zustand.value = _zustand.value.copy(statistikNamen = namen, statistikLadenFehler = null)
            }.onFailure {
                _zustand.value = _zustand.value.copy(statistikLadenFehler = "Namen konnten nicht geladen werden: ${it.message}")
            }
            setLaden(false)
        }
    }

    fun waehleStatistikName(name: String) {
        _zustand.value = _zustand.value.copy(statistikGewaehlterName = name)
        ladeStatistikDaten()
    }

    fun waehleStatistikSaison(saison: String) {
        _zustand.value = _zustand.value.copy(statistikGewaehlteSaison = saison)
        ladeStatistikDaten()
    }

    /** Reine UI-Auswahl, keine neue Abfrage nötig - die Summierung passiert im Screen selbst. */
    fun waehleStatistikMonate(monate: Set<String>) {
        _zustand.value = _zustand.value.copy(statistikGewaehlteMonate = monate)
    }

    private fun ladeStatistikDaten() {
        val name = _zustand.value.statistikGewaehlterName ?: return
        val saison = _zustand.value.statistikGewaehlteSaison
        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.leseBestaetigungenFuerStatistik(name, saison)
            ergebnis.onSuccess { liste ->
                _zustand.value = _zustand.value.copy(statistikErgebnisse = liste, statistikLadenFehler = null)
            }.onFailure {
                _zustand.value = _zustand.value.copy(statistikLadenFehler = "Statistik konnte nicht geladen werden: ${it.message}")
            }
            setLaden(false)
        }
    }

    /** Welche Saison beim Speichern einer neuen Bestätigung gilt - abgeleitet vom aktiven Datensatz (siehe waehleDatensatz). */
    private fun aktuelleSaison(): String =
        if (_zustand.value.trainingslisteSheetId == SettingsStore.TEST_TRAININGSLISTE_ID) "TEST" else "2026-27"

    /**
     * Lädt die Tab-Listen BEIDER Tabellen und bildet die Schnittmenge: nur
     * Monate, die in beiden Tabellen als Tab existieren, sind auswählbar.
     */
    private suspend fun ladeMonatsListe() {
        setLaden(true)
        try {
            val trainingslisteSheetId = settingsStore.trainingslisteSheetId.first()
            val kostenSpielbetriebSheetId = settingsStore.kostenSpielbetriebSheetId.first()

            val trainingslisteTabs = sheetsRepository.leseTabellenblattNamen(trainingslisteSheetId)
            val kostenSpielbetriebTabs = sheetsRepository.leseTabellenblattNamen(kostenSpielbetriebSheetId)
            val kostenSpielbetriebTabsSet = kostenSpielbetriebTabs.toSet()

            // Reihenfolge der Trainingsliste beibehalten, aber nur gemeinsame Monate.
            val gemeinsameMonate = trainingslisteTabs.filter { it in kostenSpielbetriebTabsSet }
            val vorausgewaehlt = gemeinsameMonate.lastOrNull()

            _zustand.value = _zustand.value.copy(
                verfuegbareMonate = gemeinsameMonate,
                gewaehlterMonat = vorausgewaehlt,
                fehler = null
            )
            vorausgewaehlt?.let { waehleMonat(it) }
        } catch (e: Exception) {
            _zustand.value = _zustand.value.copy(fehler = "Monate konnten nicht geladen werden: ${e.message}")
        } finally {
            setLaden(false)
        }
    }

    fun waehleMonat(monat: String) {
        _zustand.value = _zustand.value.copy(gewaehlterMonat = monat)
        viewModelScope.launch {
            ladeTrainingsliste(monat)
            ladeKostenSpielbetrieb(monat)
        }
    }

    private suspend fun ladeTrainingsliste(monat: String) {
        setLaden(true)
        try {
            val sheetId = settingsStore.trainingslisteSheetId.first()
            val daten = sheetsRepository.leseTrainingsliste(sheetId, monat)
            _zustand.value = _zustand.value.copy(trainingsliste = daten, fehler = null)
        } catch (e: Exception) {
            _zustand.value = _zustand.value.copy(fehler = "Trainingsliste konnte nicht geladen werden: ${e.message}")
        } finally {
            setLaden(false)
        }
    }

    private suspend fun ladeKostenSpielbetrieb(monat: String) {
        setLaden(true)
        try {
            val sheetId = settingsStore.kostenSpielbetriebSheetId.first()
            val spalten = settingsStore.spaltenZuordnung.first()
            val daten = sheetsRepository.leseKostenSpielbetrieb(sheetId, monat, spalten)
            val ersterSpieler = daten.spieler.firstOrNull()?.name
            val ersterMasseur = daten.spieler.firstOrNull { it.istMasseur() }?.name
            val ersterTormanntrainer = daten.spieler.firstOrNull { it.istTormanntrainer() }?.name
            val ersterBetreuer = daten.spieler.firstOrNull { it.istBetreuung() }?.name
            _zustand.value = _zustand.value.copy(
                kostenSpielbetrieb = daten,
                gewaehlterSpieler = ersterSpieler,
                gewaehlterMasseur = ersterMasseur,
                gewaehlterTormanntrainer = ersterTormanntrainer,
                gewaehlterBetreuer = ersterBetreuer,
                fehler = null
            )
        } catch (e: Exception) {
            _zustand.value = _zustand.value.copy(fehler = "Kosten Spielbetrieb konnte nicht geladen werden: ${e.message}")
        } finally {
            setLaden(false)
        }
    }

    fun waehleSpieler(name: String) {
        _zustand.value = _zustand.value.copy(gewaehlterSpieler = name, speichernErfolgreich = false)
    }

    fun waehleMasseur(name: String) {
        _zustand.value = _zustand.value.copy(gewaehlterMasseur = name, speichernErfolgreich = false)
    }

    fun waehleTormanntrainer(name: String) {
        _zustand.value = _zustand.value.copy(gewaehlterTormanntrainer = name, speichernErfolgreich = false)
    }

    fun waehleBetreuer(name: String) {
        _zustand.value = _zustand.value.copy(gewaehlterBetreuer = name, speichernErfolgreich = false)
    }

    fun setSpaltenZuordnung(zuordnung: SpaltenZuordnung) {
        viewModelScope.launch {
            settingsStore.speichereSpaltenZuordnung(zuordnung)
            _zustand.value = _zustand.value.copy(spaltenZuordnung = zuordnung)
            _zustand.value.gewaehlterMonat?.let { ladeKostenSpielbetrieb(it) }
        }
    }

    fun setSheetIds(trainingsliste: String, kostenSpielbetrieb: String, bestaetigung: String) {
        viewModelScope.launch {
            settingsStore.speichereSheetIds(trainingsliste, kostenSpielbetrieb, bestaetigung)
            _zustand.value = _zustand.value.copy(
                trainingslisteSheetId = trainingsliste,
                kostenSpielbetriebSheetId = kostenSpielbetrieb,
                bestaetigungSheetId = bestaetigung
            )
            ladeMonatsListe()
        }
    }

    /** Wechselt zwischen den Produktiv-Tabellen ("2026-27") und den TEST-Tabellen. */
    fun waehleDatensatz(test: Boolean) {
        if (test) {
            setSheetIds(
                SettingsStore.TEST_TRAININGSLISTE_ID,
                SettingsStore.TEST_KOSTEN_SPIELBETRIEB_ID,
                _zustand.value.bestaetigungSheetId
            )
        } else {
            setSheetIds(
                SettingsStore.STANDARD_TRAININGSLISTE_ID,
                SettingsStore.STANDARD_KOSTEN_SPIELBETRIEB_ID,
                _zustand.value.bestaetigungSheetId
            )
        }
    }

    fun speichereAuszahlung(
        bemerkung: String,
        betragErhalten: String,
        korrektur: String,
        unterschriftPngBase64: String
    ) {
        val z = _zustand.value
        val spieler = z.kostenSpielbetrieb?.spieler?.find { it.name == z.gewaehlterSpieler } ?: return
        val zeitstempel = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())

        val bestaetigung = Auszahlungsbestaetigung(
            monat = z.gewaehlterMonat ?: "",
            saison = aktuelleSaison(),
            spielerName = spieler.name,
            fixum = spieler.fixum,
            punkte = spieler.punkte,
            abzugSonstiges = spieler.abzugSonstiges,
            abzugMasseur = spieler.abzugMasseur,
            korrektur = korrektur,
            bemerkung = bemerkung.take(250),
            betragErhalten = betragErhalten,
            unterschriftPngBase64 = unterschriftPngBase64,
            erstelltAmIso = zeitstempel
        )

        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.speichereBestaetigung(bestaetigung)
            ergebnis.onSuccess {
                _zustand.value = _zustand.value.copy(speichernErfolgreich = true, fehler = null, bestaetigungen = emptyList())
            }.onFailure {
                _zustand.value = _zustand.value.copy(fehler = "Speichern fehlgeschlagen: ${it.message}")
            }
            setLaden(false)
        }
    }

    /** Aufwandsentschädigung für Masseure = Spalte C ("AP"-Feld, hier Satz pro Anwesenheit) × Spalte F. */
    fun speichereMasseurAuszahlung(
        bemerkung: String,
        korrektur: String,
        unterschriftPngBase64: String
    ) {
        val z = _zustand.value
        val spieler = z.kostenSpielbetrieb?.spieler?.find { it.name == z.gewaehlterMasseur } ?: return
        val satz = parseDeutscheZahl(spieler.ap) ?: 0.0
        val faktor = parseDeutscheZahl(spieler.masseurFaktor) ?: 0.0
        val aufwandsentschaedigung = satz * faktor
        val korrekturZahl = parseDeutscheZahl(korrektur) ?: 0.0
        val betrag = aufwandsentschaedigung + korrekturZahl

        speichereEinfacheAuszahlung(
            spielerName = spieler.name,
            fixumFeld = formatiereDeutscheZahl(aufwandsentschaedigung),
            korrektur = korrektur,
            betragErhalten = formatiereDeutscheZahl(betrag),
            bemerkung = bemerkung,
            unterschriftPngBase64 = unterschriftPngBase64
        )
    }

    /** Aufwandsentschädigung für Tormanntrainer extra = Spalte C ("Einsätze pro Monat") × Spalte F ("€ pro Anwesenheit") - gleiche Formel wie Masseur. */
    fun speichereTormanntrainerAuszahlung(
        bemerkung: String,
        korrektur: String,
        unterschriftPngBase64: String
    ) {
        val z = _zustand.value
        val spieler = z.kostenSpielbetrieb?.spieler?.find { it.name == z.gewaehlterTormanntrainer } ?: return
        val einsaetze = parseDeutscheZahl(spieler.ap) ?: 0.0
        val satzProAnwesenheit = parseDeutscheZahl(spieler.masseurFaktor) ?: 0.0
        val aufwandsentschaedigung = einsaetze * satzProAnwesenheit
        val korrekturZahl = parseDeutscheZahl(korrektur) ?: 0.0
        val betrag = aufwandsentschaedigung + korrekturZahl

        speichereEinfacheAuszahlung(
            spielerName = spieler.name,
            fixumFeld = formatiereDeutscheZahl(aufwandsentschaedigung),
            korrektur = korrektur,
            betragErhalten = formatiereDeutscheZahl(betrag),
            bemerkung = bemerkung,
            unterschriftPngBase64 = unterschriftPngBase64
        )
    }

    /** Aufwandsentschädigung für Betreuung (Trainer/Wäsche) = Fixkosten (Spalte B), unverändert. */
    fun speichereBetreuungAuszahlung(
        bemerkung: String,
        korrektur: String,
        unterschriftPngBase64: String
    ) {
        val z = _zustand.value
        val spieler = z.kostenSpielbetrieb?.spieler?.find { it.name == z.gewaehlterBetreuer } ?: return
        val aufwandsentschaedigung = parseDeutscheZahl(spieler.fixum) ?: 0.0
        val korrekturZahl = parseDeutscheZahl(korrektur) ?: 0.0
        val betrag = aufwandsentschaedigung + korrekturZahl

        speichereEinfacheAuszahlung(
            spielerName = spieler.name,
            fixumFeld = formatiereDeutscheZahl(aufwandsentschaedigung),
            korrektur = korrektur,
            betragErhalten = formatiereDeutscheZahl(betrag),
            bemerkung = bemerkung,
            unterschriftPngBase64 = unterschriftPngBase64
        )
    }

    private fun speichereEinfacheAuszahlung(
        spielerName: String,
        fixumFeld: String,
        korrektur: String,
        betragErhalten: String,
        bemerkung: String,
        unterschriftPngBase64: String
    ) {
        val z = _zustand.value
        val zeitstempel = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())
        val bestaetigung = Auszahlungsbestaetigung(
            monat = z.gewaehlterMonat ?: "",
            saison = aktuelleSaison(),
            spielerName = spielerName,
            fixum = fixumFeld,
            punkte = "",
            abzugSonstiges = "",
            abzugMasseur = "",
            korrektur = korrektur,
            bemerkung = bemerkung.take(250),
            betragErhalten = betragErhalten,
            unterschriftPngBase64 = unterschriftPngBase64,
            erstelltAmIso = zeitstempel
        )

        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.speichereBestaetigung(bestaetigung)
            ergebnis.onSuccess {
                _zustand.value = _zustand.value.copy(speichernErfolgreich = true, fehler = null, bestaetigungen = emptyList())
            }.onFailure {
                _zustand.value = _zustand.value.copy(fehler = "Speichern fehlgeschlagen: ${it.message}")
            }
            setLaden(false)
        }
    }

    /** Verschiebt eine Bestätigung in den Papierkorb (endgültige Löschung automatisch nach 40 Tagen). */
    fun loescheBestaetigung(id: String) {
        viewModelScope.launch {
            setLaden(true)
            val ergebnis = firebaseRepository.loescheBestaetigung(id)
            ergebnis.onSuccess {
                _zustand.value = _zustand.value.copy(
                    bestaetigungen = _zustand.value.bestaetigungen.filterNot { it.id == id },
                    fehler = null
                )
            }.onFailure {
                _zustand.value = _zustand.value.copy(fehler = "Löschen fehlgeschlagen: ${it.message}")
            }
            setLaden(false)
        }
    }

    /** Von MainActivity aufgerufen, wenn die App über eine Push-Benachrichtigung geöffnet wurde. */
    fun oeffneBestaetigungAusPush(id: String) {
        _zustand.value = _zustand.value.copy(
            ausPushZuOeffnendeBestaetigungId = id,
            aktiveEbene = Ebene.BESTAETIGUNGEN
        )
    }

    /** Nachdem das PDF geöffnet/geteilt wurde, den Zustand wieder zurücksetzen. */
    fun bestaetigungAusPushGeoeffnet() {
        _zustand.value = _zustand.value.copy(ausPushZuOeffnendeBestaetigungId = null)
    }

    /** Lädt eine einzelne Bestätigung per ID (für das automatische Öffnen aus einer Push-Benachrichtigung). */
    suspend fun ladeBestaetigungFuerPush(id: String): GespeicherteBestaetigung? =
        firebaseRepository.leseBestaetigung(id).getOrNull()

    private fun setLaden(v: Boolean) {
        _zustand.value = _zustand.value.copy(ladeVorgang = v)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context.applicationContext) as T
        }
    }
}
