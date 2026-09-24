package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.data.SICHERHEITS_PIN

/**
 * Button "Mit Stempel abschließen" - fragt per PIN nach, bevor der Stempel
 * tatsächlich gesetzt wird. Danach ist das Formular endgültig gesperrt.
 */
@Composable
fun StempelButton(onGestempelt: () -> Unit, modifier: Modifier = Modifier) {
    var zeigeDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { zeigeDialog = true },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7A3C)),
        modifier = modifier.fillMaxWidth()
    ) {
        Text("Mit Stempel abschließen")
    }

    if (zeigeDialog) {
        var eingegebenerPin by remember { mutableStateOf("") }
        val pinKorrekt = eingegebenerPin == SICHERHEITS_PIN

        AlertDialog(
            onDismissRequest = { zeigeDialog = false },
            title = { Text("Mit Stempel abschließen?") },
            text = {
                Column {
                    Text("Danach kann das Dokument nicht mehr geändert werden.")
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
                Button(
                    enabled = pinKorrekt,
                    onClick = {
                        onGestempelt()
                        zeigeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7A3C))
                ) {
                    Text("Stempeln")
                }
            },
            dismissButton = {
                TextButton(onClick = { zeigeDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}
