package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** PIN fürs Sperren (Stempeln) eines Vertragsformulars - dieselbe PIN wie beim Löschen von Bestätigungen. */
const val VERTRAG_STEMPEL_PIN = "24521919"

/**
 * Allgemeiner PIN-Bestätigungsdialog: der Bestätigen-Button wird erst aktiv,
 * wenn der eingegebene PIN exakt passt.
 */
@Composable
fun PinBestaetigungsDialog(
    titel: String,
    hinweistext: String,
    erwarteterPin: String,
    bestaetigenBeschriftung: String = "Bestätigen",
    onAbbrechen: () -> Unit,
    onBestaetigt: () -> Unit
) {
    var eingegebenerPin by remember { mutableStateOf("") }
    val pinKorrekt = eingegebenerPin == erwarteterPin

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column {
                Text(hinweistext)
                OutlinedTextField(
                    value = eingegebenerPin,
                    onValueChange = { if (it.length <= 20) eingegebenerPin = it },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            Button(enabled = pinKorrekt, onClick = onBestaetigt) { Text(bestaetigenBeschriftung) }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        }
    )
}
