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
 * einsaetze = Spalte N ("Einsätze pro Monat", nur für AP-Spieler relevant),
 * trainingsgeldFaktor = Spalte L (für AP-Spieler: Trainingsgeld = fixum * trainingsgeldFaktor).
 * Ein Spieler gilt als "AP-Spieler", wenn sein Name "(AP)" enthält - dann gelten andere
 * Bezeichnungen/Berechnungen (Trainingsgeld statt Fixum, Auflaufprämie statt Punkte-Bonus).
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
    val einsaetze: String,
    val trainingsgeldFaktor: String,
    val masseurFaktor: String,
    val masseurEinsaetze: String,
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
    val korrektur: String,
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
    val abzugMasseurSpalte: String = "J",
    val einsaetzeSpalte: String = "N",
    val trainingsgeldFaktorSpalte: String = "L",
    val masseurFaktorSpalte: String = "F",
    val masseurEinsaetzeSpalte: String = "S"
)

/**
 * Ebene 4 ("Bestätigungen"): eine aus Firestore geladene, bereits gespeicherte
 * Auszahlungsbestätigung, zum Ansehen/Exportieren als PDF bzw. Teilen per Mail.
 */data class GespeicherteBestaetigung(
    val id: String,
    val monat: String,
    val spielerName: String,
    val fixum: String,
    val punkte: String,
    val abzugSonstiges: String,
    val abzugMasseur: String,
    val korrektur: String,
    val bemerkung: String,
    val betragErhalten: String,
    val unterschriftUrl: String,
    val erstelltAm: String,
    val geloeschtAm: Long? = null
)

/** Ein Spieler gilt als AP-Spieler ("Auflaufprämie"), wenn sein Name "(AP)" enthält. */
fun SpielerKosten.istApSpieler(): Boolean = name.contains("(AP)", ignoreCase = true)

/** Ebene "Masseur": betrifft alle Zeilen, deren Name "Masseur" enthält. */
fun SpielerKosten.istMasseur(): Boolean = name.contains("Masseur", ignoreCase = true)

/** Ebene "Tormanntrainer extra": betrifft alle Zeilen, deren Name "Tormanntrainer" enthält. */
fun SpielerKosten.istTormanntrainer(): Boolean = name.contains("Tormanntrainer", ignoreCase = true)

/**
 * Ebene "Betreuung": betrifft alle Zeilen, deren Name "Trainer" oder "Wäsche" enthält -
 * aber nicht "Tormanntrainer" (der hat trotz "Trainer" im Namen seinen eigenen Reiter).
 */
fun SpielerKosten.istBetreuung(): Boolean =
    !istTormanntrainer() && (name.contains("Trainer", ignoreCase = true) || name.contains("Wäsche", ignoreCase = true))

/** Zeilen mit einem "*" am Namensende sind farblich hervorzuheben ("KEINE AUSZAHLUNG"). */
fun SpielerKosten.hatKeineAuszahlungMarkierung(): Boolean = name.trim().endsWith("*")
