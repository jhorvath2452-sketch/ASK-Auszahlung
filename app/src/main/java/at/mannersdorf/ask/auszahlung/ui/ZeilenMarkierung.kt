package at.mannersdorf.ask.auszahlung.ui

/**
 * Namen mit "**" am Ende -> 2, mit einem "*" -> 1, sonst 0. Steuert die rote
 * (1 Stern) bzw. hell-orange (2 Sterne) Zeilenmarkierung - gemeinsam genutzt
 * von Trainingsliste und Kosten Spielbetrieb.
 */
fun sternAnzahl(text: String): Int {
    val getrimmt = text.trim()
    return when {
        getrimmt.endsWith("**") -> 2
        getrimmt.endsWith("*") -> 1
        else -> 0
    }
}

/** Zellen mit diesem Text werden hell-orange hervorgehoben (Legende zu "**"). */
fun istZahltHansFeld(text: String): Boolean = text.contains("zahlt Hans", ignoreCase = true)
