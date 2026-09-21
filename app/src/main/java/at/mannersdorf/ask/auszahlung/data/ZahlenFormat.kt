package at.mannersdorf.ask.auszahlung.data

/**
 * Deutsch/österreichische Zahlenformatierung: Tausenderpunkt, Dezimalkomma
 * (z.B. "1.000,50" oder "1.000" oder "0,00"). Ein einfaches
 * text.replace(",", ".").toDoubleOrNull() liest "1.000" fälschlich als 1,0
 * statt 1000 - genau das war der Grund für die falsche Auszahlungs-Berechnung.
 */

/** Wandelt einen Sheet-Wert wie "1.000", "1.000,50" oder "€ 670" in eine Double um. */
fun parseDeutscheZahl(text: String?): Double? {
    if (text == null) return null
    val bereinigt = text.trim()
        .replace("€", "")
        .replace(" ", "")
        .replace(".", "")
        .replace(",", ".")
        .trim()
    return bereinigt.toDoubleOrNull()
}

/** Formatiert eine Zahl deutsch/österreichisch, z.B. 1234.5 -> "1.234,50". */
fun formatiereDeutscheZahl(wert: Double): String {
    val vorzeichen = if (wert < 0) "-" else ""
    val absolut = kotlin.math.abs(wert)
    val centsGerundet = Math.round(absolut * 100)
    val ganzzahl = centsGerundet / 100
    val nachkomma = centsGerundet % 100
    val ganzzahlGruppiert = ganzzahl.toString()
        .reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
    return "$vorzeichen$ganzzahlGruppiert,${nachkomma.toString().padStart(2, '0')}"
}
