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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Ebene { TRAININGSLISTE, KOSTEN_SPIELBETRIEB, SPIELER, BESTAETIGUNGEN }

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
    val speichernErfolgreich: Boolean = false,
    val trainingslisteSheetId: String = "",
    val kostenSpielbetriebSheetId: String = "",
    val bestaetigungSheetId: String = "",
    val spaltenZuordnung: SpaltenZuordnung = SpaltenZuordnung(),

    // Ebene 4: bereits gespeicherte Bestätigungen (aus Firestore).
    val bestaetigungen: List<GespeicherteBestaetigung> = emptyList(),
    val bestaetigungenLadenFehler: String? = null
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
            _zustand.value = _zustand.value.copy(
                kostenSpielbetrieb = daten,
                gewaehlterSpieler = ersterSpieler,
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
