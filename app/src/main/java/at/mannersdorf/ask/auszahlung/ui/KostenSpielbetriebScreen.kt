package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.formatiereDeutscheZahl
import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten
import at.mannersdorf.ask.auszahlung.data.parseDeutscheZahl

private val SPALTENBREITE = 108.dp
private val NAMENSSPALTENBREITE = 168.dp
private const val SUMMENZEILEN_MARKIERUNG = "SUMME"

// Feste Spalten für die 5 Summen im Abschluss, wie im Sheet: E, F, H, I, J
// (0-basiert: A=0, B=1, ... E=4, F=5, G=6, H=7, I=8, J=9).
private const val SUMME_SPALTE_E = 4
private const val SUMME_SPALTE_F = 5
private const val SUMME_SPALTE_H = 7
private const val SUMME_SPALTE_I = 8
private const val SUMME_SPALTE_J = 9
private val EURO_SUMMEN_SPALTEN = setOf(SUMME_SPALTE_E, SUMME_SPALTE_F)
private val HERVORGEHOBENE_SUMMEN_SPALTE = SUMME_SPALTE_H

private val SummenzeilenBlau = Color(0xFF5C7C99)
private val SummenzeilenGruen = Color(0xFF6FCB4C)

/**
 * Ebene 2: zeigt "Kosten Spielbetrieb" optisch an das Google Sheet angelehnt.
 * Zeilen, deren erste Zelle "Name" und zweite Zelle "Fixkosten" enthält, werden
 * als dunkelgrüner Balken mit weißer, fetter Schrift dargestellt - ebenso die
 * jeweils direkt darauffolgende Zeile. Komplett leere Zeilen erzeugen einen
 * Leerraum, so wie im Sheet selbst. Nach der letzten Spielerzeile wird ein
 * Abschluss mit 5 Summen (Spalten E, F, H, I, J) eingefügt, farblich an den
 * echten Sheet-Abschluss angelehnt (Blau, mit einer hervorgehobenen grünen
 * Zelle bei Spalte H) - nur zur Anzeige, wird nicht ins Sheet zurückgeschrieben.
 */
@Composable
fun KostenSpielbetriebScreen(daten: KostenSpielbetriebDaten?, modifier: Modifier = Modifier) {
    if (daten == null || daten.rohZeilen.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            Text("Keine Daten für diesen Monat gefunden.")
        }
        return
    }

    val zeilenMitSumme = remember(daten) { fuegeSummenzeileEin(daten.rohZeilen) }

    val scrollZustand = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        Text(
            "Kosten Spielbetrieb – ${daten.monat}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp)
        )
        Column(Modifier.horizontalScroll(scrollZustand)) {
            LazyColumn {
                items(zeilenMitSumme.size) { index ->
                    val zeile = zeilenMitSumme[index]
                    val vorherigeZeile = zeilenMitSumme.getOrNull(index - 1)
                    when {
                        istKomplettLeer(zeile) -> Box(Modifier.height(20.dp))
                        istKopfzeile(zeile) -> KopfzeilenBalken(zeile)
                        vorherigeZeile != null && istKopfzeile(vorherigeZeile) -> KopfzeilenBalken(zeile)
                        istSummenzeile(zeile) -> Summenzeile(zeile)
                        else -> DatenZeile(zeile)
                    }
                }
            }
        }
    }
}

private fun istKomplettLeer(zeile: List<String>): Boolean = zeile.all { it.isBlank() }

private fun istKopfzeile(zeile: List<String>): Boolean {
    val erste = zeile.getOrNull(0)?.trim() ?: ""
    val zweite = zeile.getOrNull(1)?.trim() ?: ""
    return erste.equals("Name", ignoreCase = true) && zweite.contains("Fixkosten", ignoreCase = true)
}

private fun istSummenzeile(zeile: List<String>): Boolean =
    zeile.getOrNull(0)?.trim() == SUMMENZEILEN_MARKIERUNG

/**
 * Sucht die große Detail-Kopfzeile (Name/Fixkosten/…) und summiert die Spalten
 * E, F, H, I, J über alle folgenden Spielerzeilen (bis zur ersten leeren
 * Namenszelle oder "ENDE"). Rein rechnerische Anzeige, wird nicht ins Sheet
 * geschrieben.
 */
private fun fuegeSummenzeileEin(rohZeilen: List<List<String>>): List<List<String>> {
    val headerIndex = rohZeilen.indexOfFirst { zeile ->
        val erste = zeile.getOrNull(0)?.trim() ?: ""
        val zweite = zeile.getOrNull(1)?.trim() ?: ""
        erste.equals("Name", ignoreCase = true) &&
            zweite.contains("Fixkosten", ignoreCase = true) &&
            zeile.count { it.isNotBlank() } >= 5
    }
    if (headerIndex < 0) return rohZeilen

    val kopfzeile = rohZeilen[headerIndex]
    val summenSpalten = listOf(SUMME_SPALTE_E, SUMME_SPALTE_F, SUMME_SPALTE_H, SUMME_SPALTE_I, SUMME_SPALTE_J)
    val summen = mutableMapOf<Int, Double>().withDefault { 0.0 }

    var letzteSpielerZeile = headerIndex

    for (i in headerIndex + 1 until rohZeilen.size) {
        val zeile = rohZeilen[i]
        val name = zeile.getOrNull(0)?.trim() ?: ""
        if (name.isBlank() || name.equals("ENDE", ignoreCase = true)) break
        letzteSpielerZeile = i
        for (spalte in summenSpalten) {
            summen[spalte] = summen.getValue(spalte) + (parseDeutscheZahl(zeile.getOrNull(spalte)) ?: 0.0)
        }
    }

    if (letzteSpielerZeile == headerIndex) return rohZeilen // keine Spielerzeilen gefunden

    val spaltenAnzahl = maxOf(kopfzeile.size, SUMME_SPALTE_J + 1)
    val summenzeile = MutableList(spaltenAnzahl) { "" }
    summenzeile[0] = SUMMENZEILEN_MARKIERUNG
    for (spalte in summenSpalten) {
        summenzeile[spalte] = formatiereDeutscheZahl(summen.getValue(spalte))
    }

    val ergebnis = rohZeilen.toMutableList()
    ergebnis.add(letzteSpielerZeile + 1, summenzeile)
    return ergebnis
}

@Composable
private fun KopfzeilenBalken(zeile: List<String>) {
    val letzteBefuellteSpalte = zeile.indexOfLast { it.isNotBlank() }
    val sichtbareZeile = if (letzteBefuellteSpalte >= 0) zeile.take(letzteBefuellteSpalte + 1) else zeile

    Row(Modifier.background(MaterialTheme.colorScheme.primary)) {
        sichtbareZeile.forEachIndexed { index, wert ->
            Box(
                Modifier.width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE).padding(6.dp)
            ) {
                Text(
                    wert,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DatenZeile(zeile: List<String>) {
    Row {
        zeile.forEachIndexed { index, wert ->
            Box(
                Modifier
                    .width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(6.dp)
            ) {
                Text(wert, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Abschlusszeile: blauer Balken, mit hervorgehobener grüner Zelle bei Spalte H. */
@Composable
private fun Summenzeile(zeile: List<String>) {
    Row(Modifier.background(SummenzeilenBlau)) {
        zeile.forEachIndexed { index, wert ->
            val istHervorgehoben = index == HERVORGEHOBENE_SUMMEN_SPALTE
            val anzeigeText = when {
                wert.isBlank() -> ""
                index in EURO_SUMMEN_SPALTEN -> "$wert €"
                else -> wert
            }
            Box(
                Modifier
                    .width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE)
                    .background(if (istHervorgehoben) SummenzeilenGruen else Color.Transparent)
                    .padding(6.dp)
            ) {
                Text(
                    anzeigeText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (istHervorgehoben) Color.Black else Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
