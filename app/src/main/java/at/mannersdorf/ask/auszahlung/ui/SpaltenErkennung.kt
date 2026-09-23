package at.mannersdorf.ask.auszahlung.ui

// "Gegner 1"-"Gegner 4" stehen (falls vorhanden) in Spalte P, R, T, V; die
// zugehörige "x"-Markierung in der jeweils direkt folgenden Zeile bei
// Spalte Q, S, U, W (0-basiert: P=15, Q=16, R=17, S=18, T=19, U=20, V=21, W=22).
// Wird sowohl von der Trainingsliste als auch von Kosten Spielbetrieb für den
// Schalter "Spalten ausblenden" verwendet.
private val GEGNER_SPALTEN = listOf(15 to "Gegner 1", 17 to "Gegner 2", 19 to "Gegner 3", 21 to "Gegner 4")
private val X_SPALTEN = listOf(16, 18, 20, 22)

/**
 * Sucht die Zeile, in der Spalte P ("Gegner 1") steht, prüft dort auch
 * R/T/V auf "Gegner 2"-"Gegner 4", und in der direkt darauffolgenden Zeile
 * Q/S/U/W auf "x". Liefert die Spaltenindizes, die beim Ausblenden
 * verschwinden sollen. Inhaltsbasiert (nicht über feste Zeilennummern), damit
 * das auch nach dem Überspringen von Bannerzeilen zuverlässig funktioniert.
 */
fun ermittleAuszublendendeSpalten(zeilen: List<List<String>>): Set<Int> {
    val gegnerIndex = zeilen.indexOfFirst { zeile ->
        zeile.getOrNull(15)?.trim()?.equals("Gegner 1", ignoreCase = true) == true
    }
    if (gegnerIndex < 0) return emptySet()

    val ergebnis = mutableSetOf<Int>()
    val gegnerZeile = zeilen[gegnerIndex]
    for ((spaltenIndex, erwarteterText) in GEGNER_SPALTEN) {
        if (gegnerZeile.getOrNull(spaltenIndex)?.trim()?.equals(erwarteterText, ignoreCase = true) == true) {
            ergebnis.add(spaltenIndex)
        }
    }

    val xIndex = gegnerIndex + 1
    if (xIndex < zeilen.size) {
        val xZeile = zeilen[xIndex]
        for (spaltenIndex in X_SPALTEN) {
            if (xZeile.getOrNull(spaltenIndex)?.trim()?.equals("x", ignoreCase = true) == true) {
                ergebnis.add(spaltenIndex)
            }
        }
    }
    return ergebnis
}

/** Blendet die ermittelten Spalten aus, falls gewünscht. */
fun sichtbareSpaltenIndiziert(
    zeile: List<String>,
    ausblenden: Boolean,
    auszublendendeSpalten: Set<Int>
): List<IndexedValue<String>> {
    val indiziert = zeile.withIndex().toList()
    return if (ausblenden) indiziert.filter { it.index !in auszublendendeSpalten } else indiziert
}
