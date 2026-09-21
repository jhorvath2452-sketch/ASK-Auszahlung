package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wiederverwendbarer Tab-/Monats-Dropdown. Trainingsliste und Kosten
 * Spielbetrieb haben jeweils eine eigene Instanz davon, da ihre Tabellenblätter
 * (Tabs) nicht zwingend dieselben Namen tragen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonatsDropdown(
    label: String,
    monate: List<String>,
    gewaehlterMonat: String?,
    onMonatGewaehlt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var offen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = offen,
        onExpandedChange = { offen = it },
        modifier = modifier.padding(bottom = 4.dp)
    ) {
        OutlinedTextField(
            value = gewaehlterMonat ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = offen) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            monate.forEach { monat ->
                DropdownMenuItem(
                    text = { Text(monat) },
                    onClick = {
                        onMonatGewaehlt(monat)
                        offen = false
                    }
                )
            }
        }
    }
}
