package at.mannersdorf.ask.auszahlung.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * Modales Unterschriftenfeld: Nutzer zeichnet mit Finger oder Stift, "Übernehmen"
 * liefert die Zeichnung als Base64-kodiertes PNG (für den Sync-Server).
 */
@Composable
fun SignaturePad(
    onUebernehmen: (base64Png: String) -> Unit,
    onAbbrechen: () -> Unit
) {
    val striche = remember { mutableStateListOf<MutableList<Offset>>() }
    var istLeer by remember { mutableStateOf(true) }
    var canvasGroesse by remember { mutableStateOf(IntSize(720, 360)) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Unterschrift") },
        text = {
            Column {
                Text(
                    "Bitte mit Finger oder Stift unterschreiben:",
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .onSizeChanged { canvasGroesse = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        striche.add(mutableStateListOf(start))
                                        istLeer = false
                                    },
                                    onDrag = { change, _ ->
                                        val letzterStrich = striche.lastOrNull()
                                        letzterStrich?.add(change.position)
                                        change.consume()
                                    }
                                )
                            }
                    ) {
                        striche.forEach { punkte ->
                            if (punkte.size > 1) {
                                val pfad = Path()
                                pfad.moveTo(punkte[0].x, punkte[0].y)
                                for (i in 1 until punkte.size) {
                                    pfad.lineTo(punkte[i].x, punkte[i].y)
                                }
                                drawPath(
                                    path = pfad,
                                    color = Color.Black,
                                    style = Stroke(width = 5f)
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        striche.clear()
                        istLeer = true
                    }) {
                        Text("Löschen")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !istLeer,
                onClick = {
                    val base64 = strichlisteAlsBase64Png(striche, canvasGroesse)
                    onUebernehmen(base64)
                }
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onAbbrechen) {
                Text("Abbrechen")
            }
        }
    )
}

private fun strichlisteAlsBase64Png(striche: List<List<Offset>>, groesse: IntSize): String {
    val bitmap = Bitmap.createBitmap(
        groesse.width.coerceAtLeast(1),
        groesse.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    bitmap.eraseColor(AndroidColor.WHITE)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 5f
        style = android.graphics.Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    striche.forEach { punkte ->
        if (punkte.size > 1) {
            val pfad = android.graphics.Path()
            pfad.moveTo(punkte[0].x, punkte[0].y)
            for (i in 1 until punkte.size) {
                pfad.lineTo(punkte[i].x, punkte[i].y)
            }
            canvas.drawPath(pfad, paint)
        }
    }

    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
