package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Locale

/** Textfeld, das per Tippen auf das Kalender-Symbol einen Datums-Auswahldialog öffnet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumsFeld(
    label: String,
    wert: String,
    aenderbar: Boolean,
    onWertGeaendert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogOffen by remember { mutableStateOf(false) }
    val datePickerZustand = rememberDatePickerState()

    OutlinedTextField(
        value = wert,
        onValueChange = {},
        readOnly = true,
        enabled = aenderbar,
        label = { Text(label) },
        trailingIcon = {
            if (aenderbar) {
                IconButton(onClick = { dialogOffen = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Datum wählen")
                }
            }
        },
        modifier = modifier
    )

    if (dialogOffen) {
        DatePickerDialog(
            onDismissRequest = { dialogOffen = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerZustand.selectedDateMillis
                    if (millis != null) {
                        val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                        onWertGeaendert(format.format(java.util.Date(millis)))
                    }
                    dialogOffen = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { dialogOffen = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerZustand)
        }
    }
}
