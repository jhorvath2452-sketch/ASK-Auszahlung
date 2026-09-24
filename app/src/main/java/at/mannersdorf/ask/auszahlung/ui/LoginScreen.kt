package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import at.mannersdorf.ask.auszahlung.R
import at.mannersdorf.ask.auszahlung.data.UserStore
import at.mannersdorf.ask.auszahlung.data.model.AppBenutzer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(userStore: UserStore, onAngemeldet: (AppBenutzer) -> Unit) {
    var alleBenutzer by remember { mutableStateOf<List<String>>(emptyList()) }
    var gewaehlterBenutzer by remember { mutableStateOf("") }
    var dropdownOffen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laedt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Alle Benutzernamen laden (inkl. Admin)
    LaunchedEffect(Unit) {
        val dbBenutzer = userStore.alleBenutzer().map { it.benutzername }.sorted()
        alleBenutzer = listOf(UserStore.ADMIN_NAME) + dbBenutzer
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ask_wappen),
            contentDescription = "ASK Mannersdorf",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("ASK Auszahlung", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("ASK Mannersdorf", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(40.dp))

        // Benutzer-Dropdown
        ExposedDropdownMenuBox(
            expanded = dropdownOffen,
            onExpandedChange = { dropdownOffen = it }
        ) {
            OutlinedTextField(
                value = gewaehlterBenutzer.ifBlank { "Benutzer wählen" },
                onValueChange = {},
                readOnly = true,
                label = { Text("Benutzer") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOffen) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = dropdownOffen,
                onDismissRequest = { dropdownOffen = false }
            ) {
                alleBenutzer.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            gewaehlterBenutzer = name
                            pin = ""
                            fehler = null
                            dropdownOffen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; fehler = null },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        fehler?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch {
                    laedt = true
                    val benutzer = userStore.login(gewaehlterBenutzer.trim(), pin.trim())
                    if (benutzer != null) {
                        onAngemeldet(benutzer)
                    } else {
                        fehler = "PIN ungültig."
                    }
                    laedt = false
                }
            },
            enabled = gewaehlterBenutzer.isNotBlank() && pin.isNotBlank() && !laedt,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (laedt) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text("Anmelden")
        }
    }
}
