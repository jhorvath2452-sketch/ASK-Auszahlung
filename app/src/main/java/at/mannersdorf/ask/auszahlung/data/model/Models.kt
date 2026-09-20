package at.mannersdorf.ask.auszahlung.data.model

/**
 * Eine Zeile aus der Trainingsliste (Ebene 1), Spalte A bis AM.
 * "werte" enthält die Rohwerte je Spalte in Reihenfolge A..AM (max. 39 Einträge).
 */
data class TrainingslisteZeile(
    val zeilenNummer: Int,
    val werte: List<String>
)

data class TrainingslisteDaten(
    val monat: String,
    val kopfzeile: List<String>,
    val zeilen: List<TrainingslisteZeile>
)

/**
 * Eine Spieler-Zeile aus der Tabelle "Kosten Spielbetrieb" (Ebene 2 / Basis für Ebene 3).
 */
data class SpielerKosten(
    val zeilenNummer: Int,
    val name: String,
    val fixum: String,
    val punkte: String,
    val abzugSonstiges: String,
    val abzugMasseur: String,
    val rohWerte: List<String>
)

data class KostenSpielbetriebDaten(
    val monat: String,
    val kopfzeile: List<String>,
    val spieler: List<SpielerKosten>
)

/**
 * Eine fertig unterschriebene Auszahlungsbestätigung, wie sie an den Sync-Server
 * übertragen wird, wenn "Daten übernehmen" gedrückt wird.
 */
data class Auszahlungsbestaetigung(
    val monat: String,
    val spielerName: String,
    val fixum: String,
    val punkte: String,
    val abzugSonstiges: String,
    val abzugMasseur: String,
    val bemerkung: String,
    val betragErhalten: String,
    val unterschriftPngBase64: String,
    val erstelltAmIso: String
)

/**
 * Zuordnung der Spaltenbuchstaben in der Kosten-Spielbetrieb-Tabelle.
 * In den Einstellungen anpassbar, falls sich das Sheet-Layout ändert.
 */
data class SpaltenZuordnung(
    val nameSpalte: String = "A",
    val fixumSpalte: String = "B",
    val punkteSpalte: String = "C",
    val abzugSonstigesSpalte: String = "I",
    val abzugMasseurSpalte: String = "J"
)
