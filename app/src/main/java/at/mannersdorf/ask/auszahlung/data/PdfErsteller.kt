package at.mannersdorf.ask.auszahlung.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung
import java.io.File
import java.io.FileOutputStream

/**
 * Baut aus einer gespeicherten Bestätigung ein einfaches PDF (eine A4-Seite)
 * und bietet den Android-Teilen-Dialog dafür an (Mail, Drive, Speichern, …).
 * Es wird NICHTS in die Google Sheets zurückgeschrieben - das PDF ist rein
 * eine Export-Ansicht der in Firebase gespeicherten Daten.
 */
object PdfErsteller {

    fun erstellePdf(context: Context, bestaetigung: GespeicherteBestaetigung, unterschrift: Bitmap?): File {
        val dokument = PdfDocument()
        val seiteInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 bei 72dpi
        val seite = dokument.startPage(seiteInfo)
        val canvas = seite.canvas

        val titelPaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val labelPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val textPaint = Paint().apply { textSize = 13f }

        canvas.drawText("ASK Mannersdorf – Auszahlungsbestätigung", 40f, 50f, titelPaint)

        var y = 90f
        fun zeile(label: String, wert: String) {
            canvas.drawText(label, 40f, y, labelPaint)
            canvas.drawText(wert, 220f, y, textPaint)
            y += 26f
        }

        zeile("Spieler:", bestaetigung.spielerName)
        zeile("Monat:", bestaetigung.monat)
        zeile("Fixum:", bestaetigung.fixum)
        zeile("Punkte:", bestaetigung.punkte)
        zeile("Abzug Masseur:", bestaetigung.abzugMasseur)
        zeile("Abzug Sonstiges:", bestaetigung.abzugSonstiges)
        zeile("Korrektur:", bestaetigung.korrektur)
        zeile("Ausbezahlter Betrag:", "€ ${bestaetigung.betragErhalten}")
        zeile("Bemerkung:", bestaetigung.bemerkung.ifBlank { "-" })
        zeile("Erstellt am:", bestaetigung.erstelltAm)

        y += 20f
        canvas.drawText("Unterschrift (Betrag erhalten):", 40f, y, labelPaint)
        y += 10f
        if (unterschrift != null) {
            val zielBreite = 260f
            val zielHoehe = zielBreite * unterschrift.height / unterschrift.width.coerceAtLeast(1)
            canvas.drawBitmap(unterschrift, null, RectF(40f, y, 40f + zielBreite, y + zielHoehe), null)
        } else {
            canvas.drawText("(keine Unterschrift geladen)", 40f, y + 16f, textPaint)
        }

        dokument.finishPage(seite)

        val dateiName = "Auszahlung_${bestaetigung.spielerName}_${bestaetigung.monat}.pdf"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val datei = File(context.cacheDir, dateiName)
        FileOutputStream(datei).use { dokument.writeTo(it) }
        dokument.close()
        return datei
    }

    /** Öffnet den Android-Teilen-Dialog (Mail, Drive, Speichern, …) für das PDF. */
    fun teilePdf(context: Context, datei: File, betreff: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, betreff)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bestätigung teilen"))
    }
}
