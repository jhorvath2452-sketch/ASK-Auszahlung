package at.mannersdorf.ask.auszahlung.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * Ein Unterschriftsfeld für Vertragsformulare: Label + Fläche, die beim
 * Antippen das (bestehende) SignaturePad aufpoppen lässt. Zeigt danach die
 * Unterschrift als Vorschau.
 */
@Composable
fun UnterschriftsFeld(
    label: String,
    unterschriftBase64: String?,
    onUnterschriftGeaendert: (String) -> Unit,
    aktiv: Boolean = true,
    modifier: Modifier = Modifier
) {
    var zeigeSignaturPad by remember { mutableStateOf(false) }

    Box(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .then(if (aktiv) Modifier.clickable { zeigeSignaturPad = true } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (unterschriftBase64 == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Draw, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (aktiv) "Zum Unterschreiben antippen" else "Nicht unterschrieben")
                    }
                } else {
                    val bytes = Base64.decode(unterschriftBase64, Base64.NO_WRAP)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Unterschrift $label")
                }
            }
        }
    }

    if (zeigeSignaturPad) {
        SignaturePad(
            onUebernehmen = { base64 ->
                onUnterschriftGeaendert(base64)
                zeigeSignaturPad = false
            },
            onAbbrechen = { zeigeSignaturPad = false }
        )
    }
}
