package at.mannersdorf.ask.auszahlung.data

import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten
import at.mannersdorf.ask.auszahlung.data.model.SpaltenZuordnung
import at.mannersdorf.ask.auszahlung.data.model.SpielerKosten
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteDaten
import at.mannersdorf.ask.auszahlung.data.model.TrainingslisteZeile
import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await

/**
 * Liest Trainingsliste und Kosten-Spielbetrieb NICHT mehr direkt per Google-OAuth
 * aus der App, sondern über die Firebase Cloud Function "sheetsProxy" (siehe
 * /functions). Die Function greift serverseitig mit einem Service Account auf
 * die Sheets zu - die App selbst braucht dafür keinen Google-Login mehr, nur
 * die ohnehin vorhandene (automatische, anonyme) Firebase-Anmeldung.
 */
class SheetsRepository {

    private val functions = Firebase.functions

    /** Liefert die Namen aller Tabellenblätter (z.B. Monate) einer Spreadsheet-ID. */
    suspend fun leseTabellenblattNamen(spreadsheetId: String): List<String> {
        val ergebnis = rufeFunktionAuf(mapOf("aktion" to "listeTabs", "spreadsheetId" to spreadsheetId))
        @Suppress("UNCHECKED_CAST")
        return (ergebnis["tabs"] as? List<String>) ?: emptyList()
    }

    /** Ebene 1: Trainingsliste für einen Monat (Tab-Namen), Spalten A:AM bis zur "ENDE"-Zeile. */
    suspend fun leseTrainingsliste(spreadsheetId: String, monat: String): TrainingslisteDaten {
        val werte = leseRohWerte(spreadsheetId, "'$monat'!A1:AM1000")
        if (werte.isEmpty()) return TrainingslisteDaten(monat, emptyList(), emptyList())

        val kopfzeile = werte[0]
        val datenZeilen = mutableListOf<TrainingslisteZeile>()
        for (i in 1 until werte.size) {
            val zeile = werte[i]
            val ersteSpalte = zeile.getOrNull(0)?.trim() ?: ""
            if (ersteSpalte.equals("ENDE", ignoreCase = true)) break
            if (zeile.all { it.isBlank() }) continue
            datenZeilen.add(TrainingslisteZeile(zeilenNummer = i + 1, werte = zeile))
        }
        return TrainingslisteDaten(monat, kopfzeile, datenZeilen)
    }

    /** Ebene 2 / Basis für Ebene 3: Kosten-Spielbetrieb-Tabelle für einen Monat. */
    suspend fun leseKostenSpielbetrieb(
        spreadsheetId: String,
        monat: String,
        spalten: SpaltenZuordnung
    ): KostenSpielbetriebDaten {
        val werte = leseRohWerte(spreadsheetId, "'$monat'!A1:Z500")
        if (werte.isEmpty()) return KostenSpielbetriebDaten(monat, emptyList(), emptyList())

        val kopfzeile = werte[0]
        val nameIdx = spaltenBuchstabeZuIndex(spalten.nameSpalte)
        val fixumIdx = spaltenBuchstabeZuIndex(spalten.fixumSpalte)
        val punkteIdx = spaltenBuchstabeZuIndex(spalten.punkteSpalte)
        val sonstigesIdx = spaltenBuchstabeZuIndex(spalten.abzugSonstigesSpalte)
        val masseurIdx = spaltenBuchstabeZuIndex(spalten.abzugMasseurSpalte)

        val spielerListe = mutableListOf<SpielerKosten>()
        for (i in 1 until werte.size) {
            val zeile = werte[i]
            val name = zeile.getOrNull(nameIdx)?.trim() ?: ""
            if (name.equals("ENDE", ignoreCase = true)) break
            if (name.isBlank()) continue
            spielerListe.add(
                SpielerKosten(
                    zeilenNummer = i + 1,
                    name = name,
                    fixum = zeile.getOrNull(fixumIdx) ?: "",
                    punkte = zeile.getOrNull(punkteIdx) ?: "",
                    abzugSonstiges = zeile.getOrNull(sonstigesIdx) ?: "",
                    abzugMasseur = zeile.getOrNull(masseurIdx) ?: "",
                    rohWerte = zeile
                )
            )
        }
        return KostenSpielbetriebDaten(monat, kopfzeile, spielerListe)
    }

    private suspend fun leseRohWerte(spreadsheetId: String, range: String): List<List<String>> {
        val ergebnis = rufeFunktionAuf(
            mapOf("aktion" to "leseRange", "spreadsheetId" to spreadsheetId, "range" to range)
        )
        @Suppress("UNCHECKED_CAST")
        val rohZeilen = ergebnis["werte"] as? List<List<Any?>> ?: emptyList()
        return rohZeilen.map { zeile -> zeile.map { it?.toString() ?: "" } }
    }

    private suspend fun rufeFunktionAuf(daten: Map<String, Any?>): Map<String, Any?> {
        val ergebnis = functions.getHttpsCallable("sheetsProxy").call(daten).await()
        @Suppress("UNCHECKED_CAST")
        return ergebnis.data as? Map<String, Any?> ?: emptyMap()
    }

    private fun spaltenBuchstabeZuIndex(buchstabe: String): Int {
        var index = 0
        for (c in buchstabe.trim().uppercase()) {
            index = index * 26 + (c - 'A' + 1)
        }
        return index - 1
    }
}
