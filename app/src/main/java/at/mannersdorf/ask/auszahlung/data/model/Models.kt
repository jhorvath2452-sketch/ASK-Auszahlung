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
 * Eine Spieler-Zeile aus der Tabelle "Kosten Spielbetrieb" (Basis für Ebene 3 "Spieler").
 * fixum = Spalte B ("Fixkosten"), ap = Spalte C ("AP"), punkte = Spalte D ("Punkte", roh -
 * für die Kopfzeilen-Anzeige), punkteMultiplikator = Spalte O (für die Punkte-Berechnung
 * in Ebene 3: Punkte-Betrag = punkte * punkteMultiplikator).
 */
data class SpielerKosten(
    val zeilenNummer: Int,
    val name: String,
    val fixum: String,
    val ap: String,
    val punkte: String,
    val punkteMultiplikator: String,
    val abzugSonstiges: String,
    val abzugMasseur: String,
    val rohWerte: List<String>
)

/**
 * rohZeilen enthält die komplette, unveränderte Rohtabelle (für die optisch an das
 * Google Sheet angelehnte Anzeige in Ebene 2), spieler die daraus geparste Spielerliste
 * (für Ebene 3). Beides stammt aus demselben Tabellenblatt.
 */
data class KostenSpielbetriebDaten(
    val monat: String,
    val rohZeilen: List<List<String>>,
    val spieler: List<SpielerKosten>
)

/**
 * Eine fertig unterschriebene Auszahlungsbestätigung, wie sie in Firebase gespeichert wird,
 * wenn "Daten übernehmen" gedrückt wird.
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
    val apSpalte: String = "C",
    val punkteSpalte: String = "D",
    val punkteMultiplikatorSpalte: String = "O",
    val abzugSonstigesSpalte: String = "I",
    val abzugMasseurSpalte: String = "J"
)
