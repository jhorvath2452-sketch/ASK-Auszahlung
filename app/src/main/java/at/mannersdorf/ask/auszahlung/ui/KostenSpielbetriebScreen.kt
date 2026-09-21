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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.model.KostenSpielbetriebDaten

private val SPALTENBREITE = 108.dp
private val NAMENSSPALTENBREITE = 168.dp
private const val SUMMENZEILEN_MARKIERUNG = "SUMME"

/**
 * Ebene 2: zeigt "Kosten Spielbetrieb" optisch an das Google Sheet angelehnt.
 * Zeilen, deren erste Zelle "Name" und zweite Zelle "Fixkosten" enthält, werden
 * als dunkelgrüner Balken mit weißer, fetter Schrift dargestellt - ebenso die
 * jeweils direkt darauffolgende Zeile. Komplett leere Zeilen erzeugen einen
 * Leerraum, so wie im Sheet selbst. Nach der letzten Spielerzeile wird eine
 * berechnete, fett dargestellte Summenzeile für "Auszahlung", "ABZ fix" und
 * "ABZ man" eingefügt (nur zur Anzeige - wird nicht ins Sheet zurückgeschrieben).
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
                        istSummenzeile(zeile) -> DatenZeile(zeile, fett = true)
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
 * Sucht die große Detail-Kopfzeile (Name/Fixkosten/…/Auszahlung/ABZ fix/ABZ man/…),
 * summiert "Auszahlung", "ABZ fix" und "ABZ man" über alle folgenden Spielerzeilen
 * (bis zur ersten leeren Namenszelle) und fügt danach eine fett dargestellte
 * Summenzeile ein. Rein rechnerische Anzeige, wird nicht ins Sheet geschrieben.
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
    fun spaltenIndex(titelStartetMit: String): Int =
        kopfzeile.indexOfFirst { it.trim().startsWith(titelStartetMit, ignoreCase = true) }

    val auszahlungIdx = spaltenIndex("Auszahlung")
    val abzFixIdx = spaltenIndex("ABZ fix")
    val abzManIdx = spaltenIndex("ABZ man")

    var letzteSpielerZeile = headerIndex
    var summeAuszahlung = 0.0
    var summeAbzFix = 0.0
    var summeAbzMan = 0.0

    fun zuZahl(text: String?): Double = text?.trim()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

    for (i in headerIndex + 1 until rohZeilen.size) {
        val zeile = rohZeilen[i]
        val name = zeile.getOrNull(0)?.trim() ?: ""
        if (name.isBlank() || name.equals("ENDE", ignoreCase = true)) break
        letzteSpielerZeile = i
        if (auszahlungIdx >= 0) summeAuszahlung += zuZahl(zeile.getOrNull(auszahlungIdx))
        if (abzFixIdx >= 0) summeAbzFix += zuZahl(zeile.getOrNull(abzFixIdx))
        if (abzManIdx >= 0) summeAbzMan += zuZahl(zeile.getOrNull(abzManIdx))
    }

    if (letzteSpielerZeile == headerIndex) return rohZeilen // keine Spielerzeilen gefunden

    val spaltenAnzahl = kopfzeile.size
    val summenzeile = MutableList(spaltenAnzahl) { "" }
    summenzeile[0] = SUMMENZEILEN_MARKIERUNG
    if (auszahlungIdx in summenzeile.indices) summenzeile[auszahlungIdx] = "%.2f".format(summeAuszahlung)
    if (abzFixIdx in summenzeile.indices) summenzeile[abzFixIdx] = "%.2f".format(summeAbzFix)
    if (abzManIdx in summenzeile.indices) summenzeile[abzManIdx] = "%.2f".format(summeAbzMan)

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
private fun DatenZeile(zeile: List<String>, fett: Boolean = false) {
    Row {
        zeile.forEachIndexed { index, wert ->
            Box(
                Modifier
                    .width(if (index == 0) NAMENSSPALTENBREITE else SPALTENBREITE)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(6.dp)
            ) {
                Text(
                    wert,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (fett) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
