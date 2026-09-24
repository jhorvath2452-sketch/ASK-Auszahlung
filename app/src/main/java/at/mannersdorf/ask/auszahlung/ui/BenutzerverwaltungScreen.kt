package at.mannersdorf.ask.auszahlung.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.UserStore
import at.mannersdorf.ask.auszahlung.data.model.AppBenutzer
import at.mannersdorf.ask.auszahlung.data.model.Benutzerrolle
import kotlinx.coroutines.launch

/**
 * Benutzerverwaltung – nur für Admins. Neue Benutzer anlegen, bestehende
 * bearbeiten oder löschen. Admin selbst kann nicht gelöscht werden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenutzerverwaltungScreen(userStore: UserStore, onZurueck: () -> Unit) {
    var benutzer by remember { mutableStateOf<List<AppBenutzer>>(emptyList()) }
    var bearbeiten by remember { mutableStateOf<AppBenutzer?>(null) }
    var loeschen by remember { mutableStateOf<AppBenutzer?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun laden() { benutzer = userStore.alleBenutzer() }
    LaunchedEffect(Unit) { laden() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                bearbeiten = AppBenutzer()
            }) { Icon(Icons.Filled.Add, contentDescription = "Neuer Benutzer") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onZurueck) { Text("← Zurück") }
                Text("Benutzerverwaltung", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Admin (admin / PIN: 24521919) ist immer vorhanden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            fehler?.let { Text(it, color = Color(0xFFB3261E)) }

            LazyColumn(Modifier.fillMaxSize()) {
                items(benutzer, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(b.benutzername, fontWeight = FontWeight.Bold)
                                Text("Rolle: ${b.rolle.name} · PIN: ${"•".repeat(b.pin.length)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                IconButton(onClick = { bearbeiten = b }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten")
                                }
                                IconButton(onClick = { loeschen = b }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Löschen",
                                        tint = Color(0xFFB3261E))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Benutzer anlegen / bearbeiten ─────────────────────────────────────────
    bearbeiten?.let { b ->
        var name by remember(b.id) { mutableStateOf(b.benutzername) }
        var pin by remember(b.id) { mutableStateOf(b.pin) }
        var rolle by remember(b.id) { mutableStateOf(b.rolle) }
        var rolleDropdownOffen by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { bearbeiten = null },
            title = { Text(if (b.id.isBlank()) "Neuer Benutzer" else "Benutzer bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Benutzername") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it },
                        label = { Text("PIN") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = rolleDropdownOffen,
                        onExpandedChange = { rolleDropdownOffen = it }) {
                        OutlinedTextField(value = rolle.name, onValueChange = {},
                            readOnly = true, label = { Text("Rolle") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rolleDropdownOffen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor())
                        ExposedDropdownMenu(expanded = rolleDropdownOffen,
                            onDismissRequest = { rolleDropdownOffen = false }) {
                            Benutzerrolle.entries.forEach { r ->
                                DropdownMenuItem(text = { Text(r.name) },
                                    onClick = { rolle = r; rolleDropdownOffen = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val neu = b.copy(benutzername = name.trim(), pin = pin.trim(), rolle = rolle)
                        val ergebnis = userStore.speichereBenutzer(neu)
                        ergebnis.onSuccess { laden(); bearbeiten = null }
                            .onFailure { fehler = "Fehler: ${it.message}" }
                    }
                }, enabled = name.isNotBlank() && pin.isNotBlank()) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { bearbeiten = null }) { Text("Abbrechen") } }
        )
    }

    // ── Löschen bestätigen ────────────────────────────────────────────────────
    loeschen?.let { b ->
        AlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text("Benutzer löschen?") },
            text = { Text("${b.benutzername} (${b.rolle.name}) wirklich löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            userStore.loescheBenutzer(b.id)
                                .onSuccess { laden(); loeschen = null }
                                .onFailure { fehler = "Fehler: ${it.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { loeschen = null }) { Text("Abbrechen") } }
        )
    }
}
