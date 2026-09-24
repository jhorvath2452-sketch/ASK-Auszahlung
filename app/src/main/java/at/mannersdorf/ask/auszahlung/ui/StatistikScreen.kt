package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.formatiereDeutscheZahl
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung
import at.mannersdorf.ask.auszahlung.data.parseDeutscheZahl
import at.mannersdorf.ask.auszahlung.viewmodel.SAISON_MONATE
import at.mannersdorf.ask.auszahlung.viewmodel.VERFUEGBARE_SAISONEN

/**
 * Ebene "Statistik": Name + Saison + Monatsauswahl (Mehrfachauswahl) ergeben
 * eine tabellarische Zusammenfassung der Monatsbezüge dieser Person, mit
 * Gesamtsumme am Ende. Kein Unterschied zwischen Spieler/Masseur/
 * Tormanntrainer/Betreuung - alle Namen stehen gemeinsam in einer Liste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistikScreen(
    namen: List<String>,
    gewaehlterName: String?,
    gewaehlteSaison: String,
    gewaehlteMonate: Set<String>,
    ergebnisse: List<GespeicherteBestaetigung>,
    fehler: String?,
    onNameGewaehlt: (String) -> Unit,
    onSaisonGewaehlt: (String) -> Unit,
    onMonateGewaehlt: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameDropdownOffen by remember { mutableStateOf(false) }
    var saisonDropdownOffen by remember { mutableStateOf(false) }
    var monateDropdownOffen by remember { mutableStateOf(false) }

    // Pro Monat aufsummieren (falls es zu einem Monat mehrere Bestätigungen
    // gibt, z.B. eine Korrektur als zusätzlicher Eintrag).
    val summenProMonat = remember(ergebnisse) {
        ergebnisse
            .groupBy { it.monat.trim() }
            .mapValues { (_, eintraege) -> eintraege.sumOf { parseDeutscheZahl(it.betragErhalten) ?: 0.0 } }
    }
    val sichtbareMonate = SAISON_MONATE.filter { it in gewaehlteMonate }
    val gesamtsumme = sichtbareMonate.sumOf { monat ->
        // robuster Match: auch Beträge zu Monaten mit leichten Abweichungen finden
        summenProMonat[monat] ?: summenProMonat.entries.firstOrNull {
            it.key.equals(monat, ignoreCase = true)
        }?.value ?: 0.0
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Statistik", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(expanded = nameDropdownOffen, onExpandedChange = { nameDropdownOffen = it }) {
            OutlinedTextField(
                value = gewaehlterName ?: "Name wählen",
                onValueChange = {},
                readOnly = true,
                label = { Text("Name") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nameDropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = nameDropdownOffen, onDismissRequest = { nameDropdownOffen = false }) {
                namen.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onNameGewaehlt(name)
                            nameDropdownOffen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(expanded = saisonDropdownOffen, onExpandedChange = { saisonDropdownOffen = it }) {
            OutlinedTextField(
                value = gewaehlteSaison,
                onValueChange = {},
                readOnly = true,
                label = { Text("Saison") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = saisonDropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = saisonDropdownOffen, onDismissRequest = { saisonDropdownOffen = false }) {
                VERFUEGBARE_SAISONEN.forEach { saison ->
                    DropdownMenuItem(
                        text = { Text(saison) },
                        onClick = {
                            onSaisonGewaehlt(saison)
                            saisonDropdownOffen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Monat: Mehrfachauswahl über ein aufklappbares Kästchen-Menü, das
        // beim Antippen offen bleibt (im Gegensatz zum normalen Dropdown).
        ExposedDropdownMenuBox(expanded = monateDropdownOffen, onExpandedChange = { monateDropdownOffen = it }) {
            OutlinedTextField(
                value = if (gewaehlteMonate.size == SAISON_MONATE.size) "Alle Monate"
                    else if (gewaehlteMonate.isEmpty()) "Kein Monat gewählt"
                    else "${gewaehlteMonate.size} Monate gewählt",
                onValueChange = {},
                readOnly = true,
                label = { Text("Monate") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monateDropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = monateDropdownOffen, onDismissRequest = { monateDropdownOffen = false }) {
                DropdownMenuItem(
                    text = { Text("Alle Monate", fontWeight = FontWeight.Bold) },
                    onClick = { onMonateGewaehlt(SAISON_MONATE.toSet()) }
                )
                DropdownMenuItem(
                    text = { Text("Keinen Monat", fontWeight = FontWeight.Bold) },
                    onClick = { onMonateGewaehlt(emptySet()) }
                )
                HorizontalDivider()
                SAISON_MONATE.forEach { monat ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = monat in gewaehlteMonate,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(monat)
                            }
                        },
                        onClick = {
                            onMonateGewaehlt(
                                if (monat in gewaehlteMonate) gewaehlteMonate - monat else gewaehlteMonate + monat
                            )
                        }
                    )
                }
            }
        }

        fehler?.let {
            Spacer(Modifier.height(8.dp))
            Snackbar { Text(it) }
        }

        Spacer(Modifier.height(16.dp))

        if (gewaehlterName == null) {
            Text("Bitte einen Namen auswählen.")
            return@Column
        }

        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Monat", fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp))
                Text("Betrag", fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp))
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(sichtbareMonate) { monat ->
                    val betrag = summenProMonat[monat]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(monat, modifier = Modifier.width(140.dp))
                        Text(
                            if (betrag != null) "€ " + formatiereDeutscheZahl(betrag) else "–",
                            modifier = Modifier.width(120.dp)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Gesamtsumme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "€ " + formatiereDeutscheZahl(gesamtsumme),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

