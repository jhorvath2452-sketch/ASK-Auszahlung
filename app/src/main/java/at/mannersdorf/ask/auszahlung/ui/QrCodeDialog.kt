package at.mannersdorf.ask.auszahlung.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Zeigt einen QR-Code zur Firebase-Storage-Download-URL des Vertrags-PDFs.
 * Der Spieler scannt den Code mit seinem Handy und öffnet das PDF direkt –
 * keine App, kein WhatsApp nötig.
 */
@Composable
fun QrCodeDialog(url: String, onSchliessen: () -> Unit) {
    val qrBitmap = remember(url) { erstelleQrCode(url, 512) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("QR-Code zum PDF") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Spieler scannt diesen Code mit der Handy-Kamera und öffnet das PDF direkt.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR-Code",
                        modifier = Modifier.size(260.dp).fillMaxWidth()
                    )
                } else {
                    Text("QR-Code konnte nicht erstellt werden.",
                        color = androidx.compose.ui.graphics.Color(0xFFB3261E))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Link ist 7 Tage gültig (Firebase Storage Standard).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSchliessen) { Text("Schließen") }
        }
    )
}

private fun erstelleQrCode(inhalt: String, groesse: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(inhalt, BarcodeFormat.QR_CODE, groesse, groesse, hints)
        val bitmap = Bitmap.createBitmap(groesse, groesse, Bitmap.Config.RGB_565)
        for (x in 0 until groesse) {
            for (y in 0 until groesse) {
                bitmap.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
