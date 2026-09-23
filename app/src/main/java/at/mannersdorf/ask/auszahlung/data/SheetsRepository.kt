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
 * Liest Trainingsliste und Kosten-Spielbetrieb über die Firebase Cloud Function
 * "sheetsProxy" (siehe /functions). Die App braucht dafür keinen Google-Login,
 * nur die automatische, anonyme Firebase-Anmeldung.
 */
class SheetsRepository {

    private val functions = Firebase.functions("europe-west1")

    /** Liefert die Namen aller Tabellenblätter (z.B. Monate) einer Spreadsheet-ID. */
    suspend fun leseTabellenblattNamen(spreadsheetId: String): List<String> {
        val ergebnis = rufeFunktionAuf(mapOf("aktion" to "listeTabs", "spreadsheetId" to spreadsheetId))
        @Suppress("UNCHECKED_CAST")
        return (ergebnis["tabs"] as? List<String>) ?: emptyList()
    }

    /**
     * Ebene 1: Trainingsliste für einen Monat (Tab-Namen), Spalten A:AM.
     * Führende Bannerzeilen (z.B. eine sheet-interne Titelzeile wie
     * "TRAININGSLISTE - JULI 2026" mit nur einer befüllten Zelle) werden
     * übersprungen, bis die echte Kopfzeile mit mehreren Spaltentiteln kommt.
     * Letzte Datenzeile ist die Zeile VOR der Zeile "Masseur Ersatz" in Spalte A
     * (diese Markierungszeile selbst wird nicht angezeigt).
     */
    suspend fun leseTrainingsliste(spreadsheetId: String, monat: String): TrainingslisteDaten {
        val rohWerte = leseRohWerte(spreadsheetId, "'$monat'!A1:AM1000")
        val werte = rohWerte.dropWhile { zeile -> zeile.count { it.isNotBlank() } <= 1 }
        if (werte.isEmpty()) return TrainingslisteDaten(monat, emptyList(), emptyList())

        val kopfzeile = werte[0]
        val datenZeilen = mutableListOf<TrainingslisteZeile>()
        for (i in 1 until werte.size) {
            val zeile = werte[i]
            val ersteSpalte = zeile.getOrNull(0)?.trim() ?: ""
            if (ersteSpalte.equals("Masseur Ersatz", ignoreCase = true)) break
            if (zeile.all { it.isBlank() }) continue
            datenZeilen.add(TrainingslisteZeile(zeilenNummer = i + 1, werte = zeile))
        }
        return TrainingslisteDaten(monat, kopfzeile, datenZeilen)
    }

    /**
     * Ebene 2 ("Kosten") + Basis für Ebene 3-5 ("Spieler"/"Masseur"/"Betreuung"):
     * liefert sowohl die kompletten Rohzeilen (für eine an das Google Sheet
     * angelehnte Anzeige) als auch die daraus geparste Spielerliste (feste
     * Spaltenbuchstaben, siehe SpaltenZuordnung). Führende Bannerzeilen (z.B.
     * eine Titelzeile "KOSTEN SPIELBETRIEB" mit nur einer befüllten Zelle)
     * werden übersprungen. Die Spielerzeilen werden ab der Kopfzeile erkannt,
     * deren erste beiden Zellen "Name" und "Fixkosten" enthalten und die
     * mindestens 5 befüllte Spalten hat (unterscheidet die große Detailtabelle
     * von einem kleineren "Name/Fixkosten/Bemerkung"-Block weiter oben im Sheet).
     */
    suspend fun leseKostenSpielbetrieb(
        spreadsheetId: String,
        monat: String,
        spalten: SpaltenZuordnung
    ): KostenSpielbetriebDaten {
        val rohWerte = leseRohWerte(spreadsheetId, "'$monat'!A1:W500")
        val werte = rohWerte.dropWhile { zeile -> zeile.count { it.isNotBlank() } <= 1 }
        if (werte.isEmpty()) return KostenSpielbetriebDaten(monat, emptyList(), emptyList())

        val nameIdx = spaltenBuchstabeZuIndex(spalten.nameSpalte)
        val fixumIdx = spaltenBuchstabeZuIndex(spalten.fixumSpalte)
        val apIdx = spaltenBuchstabeZuIndex(spalten.apSpalte)
        val punkteIdx = spaltenBuchstabeZuIndex(spalten.punkteSpalte)
        val punkteMultIdx = spaltenBuchstabeZuIndex(spalten.punkteMultiplikatorSpalte)
        val sonstigesIdx = spaltenBuchstabeZuIndex(spalten.abzugSonstigesSpalte)
        val masseurIdx = spaltenBuchstabeZuIndex(spalten.abzugMasseurSpalte)
        val einsaetzeIdx = spaltenBuchstabeZuIndex(spalten.einsaetzeSpalte)
        val trainingsgeldFaktorIdx = spaltenBuchstabeZuIndex(spalten.trainingsgeldFaktorSpalte)
        val masseurFaktorIdx = spaltenBuchstabeZuIndex(spalten.masseurFaktorSpalte)
        val masseurEinsaetzeIdx = spaltenBuchstabeZuIndex(spalten.masseurEinsaetzeSpalte)

        // Start der Detail-Spielertabelle finden (zweite "Name/Fixkosten"-Kopfzeile).
        var startZeile = -1
        for (i in werte.indices) {
            val zeile = werte[i]
            val ersteZelle = zeile.getOrNull(0)?.trim() ?: ""
            val zweiteZelle = zeile.getOrNull(1)?.trim() ?: ""
            val befuellteSpalten = zeile.count { it.isNotBlank() }
            if (ersteZelle.equals("Name", ignoreCase = true) &&
                zweiteZelle.contains("Fixkosten", ignoreCase = true) &&
                befuellteSpalten >= 5
            ) {
                startZeile = i + 1
                break
            }
        }

        val spielerListe = mutableListOf<SpielerKosten>()
        val bereitsErfassteZeilen = mutableSetOf<Int>()

        fun baueSpieler(i: Int, name: String): SpielerKosten {
            val zeile = werte[i]
            return SpielerKosten(
                zeilenNummer = i + 1,
                name = name,
                fixum = zeile.getOrNull(fixumIdx) ?: "",
                ap = zeile.getOrNull(apIdx) ?: "",
                punkte = zeile.getOrNull(punkteIdx) ?: "",
                punkteMultiplikator = zeile.getOrNull(punkteMultIdx) ?: "",
                abzugSonstiges = zeile.getOrNull(sonstigesIdx) ?: "",
                abzugMasseur = zeile.getOrNull(masseurIdx) ?: "",
                einsaetze = zeile.getOrNull(einsaetzeIdx) ?: "",
                trainingsgeldFaktor = zeile.getOrNull(trainingsgeldFaktorIdx) ?: "",
                masseurFaktor = zeile.getOrNull(masseurFaktorIdx) ?: "",
                masseurEinsaetze = zeile.getOrNull(masseurEinsaetzeIdx) ?: "",
                rohWerte = zeile
            )
        }

        // 1. Reguläre Spieler ab der großen Detailtabelle.
        if (startZeile >= 0) {
            for (i in startZeile until werte.size) {
                val zeile = werte[i]
                val name = zeile.getOrNull(nameIdx)?.trim() ?: ""
                if (name.equals("ENDE", ignoreCase = true)) break
                if (name.isBlank()) continue
                spielerListe.add(baueSpieler(i, name))
                bereitsErfassteZeilen.add(i)
            }
        }

        // 2. Masseur/Betreuung (Trainer, Wäsche) stehen oft VOR der großen
        // Detailtabelle (z.B. in einem eigenen kleinen Block weiter oben im
        // Sheet) und würden von Schritt 1 allein nicht erfasst. Deshalb wird
        // zusätzlich die komplette Tabelle nach passenden Namen durchsucht.
        for (i in werte.indices) {
            if (i in bereitsErfassteZeilen) continue
            val zeile = werte[i]
            val name = zeile.getOrNull(nameIdx)?.trim() ?: ""
            if (name.isBlank()) continue
            val istSonderZeile = name.contains("Masseur", ignoreCase = true) ||
                name.contains("Trainer", ignoreCase = true) ||
                name.contains("Wäsche", ignoreCase = true)
            if (istSonderZeile) {
                spielerListe.add(baueSpieler(i, name))
                bereitsErfassteZeilen.add(i)
            }
        }

        return KostenSpielbetriebDaten(monat, werte, spielerListe)
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
