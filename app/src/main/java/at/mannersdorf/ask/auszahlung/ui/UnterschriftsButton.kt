package at.mannersdorf.ask.auszahlung.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Button für ein Unterschriftsfeld in einem Vertragsformular: öffnet beim
 * Antippen das Unterschriften-Pad (SignaturePad) als Pop-Up. [istErledigt]
 * zeigt an, ob schon eine Unterschrift vorliegt (neu erfasst ODER schon
 * gespeichert/hochgeladen).
 */
@Composable
fun UnterschriftsButton(
    label: String,
    istErledigt: Boolean,
    aenderbar: Boolean,
    onNeueUnterschrift: (base64Png: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var zeigeSignaturePad by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { if (aenderbar) zeigeSignaturePad = true },
        enabled = aenderbar,
        modifier = modifier
    ) {
        Text(if (istErledigt) "$label ✓" else "$label – unterschreiben")
    }

    if (zeigeSignaturePad) {
        SignaturePad(
            onUebernehmen = { base64 ->
                onNeueUnterschrift(base64)
                zeigeSignaturePad = false
            },
            onAbbrechen = { zeigeSignaturePad = false }
        )
    }
}
