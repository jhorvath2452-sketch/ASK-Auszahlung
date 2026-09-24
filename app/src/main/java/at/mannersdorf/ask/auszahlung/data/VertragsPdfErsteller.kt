package at.mannersdorf.ask.auszahlung.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsTyp
import java.io.File
import java.io.FileOutputStream

/**
 * Baut aus einem ausgefüllten Vertragsformular ("Vereinbarung" oder "Zusatz
 * zur Vereinbarung") ein PDF, inhaltlich am Original-Formular orientiert
 * (Text, Reihenfolge der Klauseln), mit den eingetragenen Werten und den
 * Unterschriften eingefügt. Ist das Formular gestempelt, wird zusätzlich ein
 * Stempel-Vermerk aufgedruckt.
 */
object VertragsPdfErsteller {

    private const val SEITENBREITE = 595
    private const val SEITENHOEHE = 842
    private const val RAND_LINKS = 40f
    private const val RAND_RECHTS = 555f
    private const val TEXTBREITE = RAND_RECHTS - RAND_LINKS

    fun erstellePdf(
        context: Context,
        formular: VertragsFormular,
        unterschriften: Map<String, Bitmap?>
    ): File {
        val dokument = PdfDocument()

        if (formular.typ == VertragsTyp.VEREINBARUNG) {
            zeichneVereinbarungSeite1(dokument, formular)
            zeichneVereinbarungSeite2(dokument, formular, unterschriften)
        } else {
            zeichneZusatzSeite(dokument, formular, unterschriften)
        }

        val dateiName = "${formular.typ.name}_${formular.spielerName}_${System.currentTimeMillis()}.pdf"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val datei = File(context.cacheDir, dateiName)
        FileOutputStream(datei).use { dokument.writeTo(it) }
        dokument.close()
        return datei
    }

    fun teilePdf(context: Context, datei: File, betreff: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, betreff)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Vertrag teilen"))
    }

    // ---------- Vereinbarung, Seite 1 ----------

    private fun zeichneVereinbarungSeite1(dokument: PdfDocument, f: VertragsFormular) {
        val seite = dokument.startPage(PdfDocument.PageInfo.Builder(SEITENBREITE, SEITENHOEHE, 1).create())
        val c = seite.canvas

        val titel = Paint().apply { textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val unterTitel = Paint().apply { textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val fett = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val normal = Paint().apply { textSize = 12f }
        val klein = Paint().apply { textSize = 9f; color = Color.DKGRAY }

        var y = 50f
        c.drawText("VEREINBARUNG", SEITENBREITE / 2f, y, titel)
        y += 24f
        c.drawText("Nr. ${f.nummer}", SEITENBREITE / 2f, y, unterTitel)

        y += 40f
        c.drawText("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler", RAND_LINKS, y, normal)

        y += 36f
        y = feld(c, "NAME:", f.name, RAND_LINKS, y, fett, normal)
        y = feld(c, "ADRESSE:", f.adresse, RAND_LINKS, y, fett, normal)
        y = feld(c, "MAIL:", f.mail, RAND_LINKS, y, fett, normal)

        y += 20f
        y = umbrochenerText(
            c,
            "Der ASK Mannersdorf entschädigt den Spieler mit einem FIXUM als (Fahrtkosten-Spesenersatz) pro Anwesenheit beim Training und geleisteten Spielen.",
            RAND_LINKS, y, TEXTBREITE, normal, 15f
        )

        y += 16f
        c.drawText("1. Fixum:", RAND_LINKS, y, fett)
        y = umbrochenerText(
            c,
            "Sowohl der Zeitraum, als auch die Höhe wird mit jedem einzelnen Spieler frei vereinbart. Gleiches gilt für die Auszahlung.",
            RAND_LINKS + 70f, y, TEXTBREITE - 70f, normal, 15f
        )
        y += 10f
        c.drawText("Fixum: € ${f.fixum}", RAND_LINKS + 70f, y, fett)

        y += 26f
        c.drawText("Voraussetzungen:", RAND_LINKS + 70f, y, fett)
        y += 18f
        y = nummeriertePunkt(c, "a)", "Mindestens 90%ige Anwesenheitspflicht beim Training (lt. Anwesenheitsliste). Anwesenheitspflicht bei Verletzung bzw. Krankheit in Absprache mit dem Trainer.", RAND_LINKS + 90f, y, normal)
        y = nummeriertePunkt(c, "b)", "Mindestens 50%ige Anwesenheit / Verfügbarkeit als Spieler in der Kampfmannschaft (aller Meisterspiele).", RAND_LINKS + 90f, y, normal)
        y = nummeriertePunkt(c, "c)", "Bei Langzeitverletzungen bekommt der Spieler die pauschalierte AWE max. 1 Monat ab dem Zeitpunkt der Verletzung. In der Zeit werden KEINE Punkteprämien ausbezahlt! Die pauschalierte AWE wird erst wieder ab dem Zeitpunkt der Einsatzfähigkeit (Match-Fit!) des Spielers ausbezahlt.", RAND_LINKS + 90f, y, normal)
        y = nummeriertePunkt(c, "d)", "Bei Verletzung während der Freizeit*, wird nur der \u201eangefangene Monat\u201c fertig ausbezahlt! (*Dazu zählen auch Verletzungen aufgrund von Einsätzen bei Futsal- oder Hallenfußballveranstaltungen! Diese sind außerdem mit dem Trainer abzustimmen!!)", RAND_LINKS + 90f, y, normal)

        y += 12f
        c.drawText("2.", RAND_LINKS, y, fett)
        umbrochenerText(
            c,
            "Der Verein ist berechtigt, bei besonders gravierenden Verstößen durch den Spieler (nachweisliches Selbstverschulden,...), wobei dem Verein ein sogenannter Folgeschaden entsteht (z.B. anschließende Sperre,...), eine Geldstrafe (z.B. prozentuelle Reduzierung oder komplette Streichung des Fixums), in Abzug zu bringen.",
            RAND_LINKS + 30f, y, TEXTBREITE - 30f, normal, 15f
        )

        c.drawText("Vereinbarung ASK Mannersdorf – Seite 1 von 2", RAND_LINKS, SEITENHOEHE - 30f, klein)
        dokument.finishPage(seite)
    }

    // ---------- Vereinbarung, Seite 2 ----------

    private fun zeichneVereinbarungSeite2(dokument: PdfDocument, f: VertragsFormular, unterschriften: Map<String, Bitmap?>) {
        val seite = dokument.startPage(PdfDocument.PageInfo.Builder(SEITENBREITE, SEITENHOEHE, 2).create())
        val c = seite.canvas

        val fett = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val normal = Paint().apply { textSize = 12f }
        val klein = Paint().apply { textSize = 9f; color = Color.DKGRAY }

        var y = 50f
        val klauseln = listOf(
            "3." to "Die Entscheidung, ob der Spieler am Spielbericht der Kampfmannschaft aufscheint, obliegt alleine dem Trainer der KM des ASK Mannersdorf.",
            "4." to "Der Trainer legt die Trainingstage fest und führt eine Anwesenheitsliste. Diese dient als Basis für die Auszahlung. Bei einer Trainingsbeteiligung unter 90% behält sich der Verein vor, entsprechende Abzüge bei der Auszahlung zu tätigen.",
            "5." to "Für eine eventuelle Steuerliche Veranlagung muss der Spieler selbst sorgen.",
            "6." to "Der Erhalt der Aufwandsentschädigung stellt für den Spieler weder einen Hauptberuf noch eine Haupteinnahmequelle dar.",
            "7." to "Die Auszahlung erfolgt (sofern nicht anderes vereinbart) monatlich 10x im Jahr.",
            "8." to "Jeder angemeldete Spieler hat einen Jahresmitgliedsbeitrag von € 50,- zu entrichten.",
            "9." to "Jeder angemeldete Spieler hat einen monatlichen Wäsche- & Masseur-Beitrag von mindestens € 30,- bis maximal 50,- zu entrichten! (richtet sich nach dem Aufwand!)",
            "10." to "Über die vereinbarte Summe ist Stillschweigen zu bewahren!",
            "11." to "Sollte unter Punkt 1-10 anderes/zusätzliches vereinbart werden, ist dies hier im Vertrag unter Punkt 11 (ANMERKUNGEN) zu ergänzen und schriftlich festzuhalten. Nicht im Vertrag festgehaltene Vereinbarungen gelten als NICHT GETROFFEN!",
            "12." to "Laufzeit der Spielervereinbarung: 30.06.2027",
            "13." to "Utensilien, die vom Verein zur Verfügung gestellt werden, können jederzeit zurückgefordert werden. Sobald der Spieler den Verein verlässt, obliegt es dem Verein, ob die Utensilien wieder abzugeben sind. Werden diese nicht zurückgegeben, wird dem Spieler ein Betrag bis zu € 200,- abgezogen oder in Rechnung gestellt."
        )
        for ((nr, text) in klauseln) {
            c.drawText(nr, RAND_LINKS, y, fett)
            y = umbrochenerText(c, text, RAND_LINKS + 30f, y, TEXTBREITE - 30f, normal, 15f)
            y += 10f
        }

        y += 6f
        c.drawText("Anmerkungen:", RAND_LINKS, y, fett)
        val boxOben = y + 8f
        val boxHoehe = 70f
        c.drawRect(RectF(RAND_LINKS, boxOben, RAND_RECHTS, boxOben + boxHoehe), Paint().apply { style = Paint.Style.STROKE })
        umbrochenerText(c, f.anmerkungen, RAND_LINKS + 6f, boxOben + 16f, TEXTBREITE - 12f, normal, 15f)
        y = boxOben + boxHoehe + 30f

        c.drawText("Datum: ${f.datum}", RAND_LINKS, y, fett)
        y += 50f

        val spaltenBreite = TEXTBREITE / 2f
        unterschriftsFeld(c, "Obmann", unterschriften["obmann"], RAND_LINKS, y, spaltenBreite - 10f)
        unterschriftsFeld(c, "Spieler", unterschriften["spieler"], RAND_LINKS + spaltenBreite + 10f, y, spaltenBreite - 10f)
        y += 110f
        unterschriftsFeld(c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], RAND_LINKS, y, spaltenBreite - 10f)
        unterschriftsFeld(c, "Kassier", unterschriften["kassier"], RAND_LINKS + spaltenBreite + 10f, y, spaltenBreite - 10f)

        if (f.gestempelt) {
            zeichneStempel(c, 420f, y + 70f)
        }

        c.drawText("Vereinbarung ASK Mannersdorf – Seite 2 von 2", RAND_LINKS, SEITENHOEHE - 30f, klein)
        dokument.finishPage(seite)
    }

    // ---------- Zusatz zur Vereinbarung ----------

    private fun zeichneZusatzSeite(dokument: PdfDocument, f: VertragsFormular, unterschriften: Map<String, Bitmap?>) {
        val seite = dokument.startPage(PdfDocument.PageInfo.Builder(SEITENBREITE, SEITENHOEHE, 1).create())
        val c = seite.canvas

        val titel = Paint().apply { textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val fett = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val normal = Paint().apply { textSize = 12f }
        val klein = Paint().apply { textSize = 9f; color = Color.DKGRAY }

        var y = 50f
        c.drawText("ZUSATZ zur VEREINBARUNG", SEITENBREITE / 2f, y, titel)
        y += 22f
        c.drawText("Nr./Ref.: ${f.nummer}", SEITENBREITE / 2f, y, Paint().apply { textSize = 12f; textAlign = Paint.Align.CENTER })

        y += 40f
        c.drawText("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler:", RAND_LINKS, y, normal)
        y += 20f
        c.drawText(f.name, RAND_LINKS, y, fett)

        y += 30f
        y = umbrochenerText(
            c,
            "Der ASK Mannersdorf entschädigt den Spieler zusätzlich zum vereinbarten Fixum mit einem monatlichen BONUS (1) und/oder einer PUNKTEPRÄMIE (2):",
            RAND_LINKS, y, TEXTBREITE, normal, 15f
        )

        y += 16f
        c.drawText("1. Bonus: € ${f.bonus}", RAND_LINKS, y, fett)

        y += 26f
        c.drawText("2. Punkteprämie als (Fahrtkosten und Spesenersatz):", RAND_LINKS, y, fett)
        y += 18f
        y = umbrochenerText(
            c,
            "Die Prämien werden nach tatsächlicher Spielzeit pro Meisterschaftsbegegnung berechnet. Ist der Spieler bei einem Meisterschaftsspiel ab Beginn im Einsatz, besteht Anspruch auf vollen Ersatz. Ist der Spieler ab der 45. bis zur 90. Minute im Einsatz, erhält er 75%. Ist dieser ab der 76. Minute im Einsatz, erhält er 50%.",
            RAND_LINKS + 20f, y, TEXTBREITE - 20f, normal, 15f
        )
        y += 14f
        c.drawText("Sieg pro Punkt (3 Punkte)  € ${f.siegProPunkt}", RAND_LINKS + 40f, y, fett)
        y += 20f
        c.drawText("Unentschieden (1 Punkt)  € ${f.unentschieden}", RAND_LINKS + 40f, y, fett)

        y += 26f
        val klauseln = listOf(
            "3." to "Die Auszahlung des Bonus erfolgt monatlich 10x im Jahr.",
            "4." to "Für die Auszahlung gelten dieselben Bedingungen (Trainingsbeteiligung, etc.) wie in der Vereinbarung festgehalten!",
            "5." to "Über die vereinbarten Summen (Fixum, Punkteprämie) ist Stillschweigen zu bewahren!"
        )
        for ((nr, text) in klauseln) {
            c.drawText(nr, RAND_LINKS, y, fett)
            y = umbrochenerText(c, text, RAND_LINKS + 30f, y, TEXTBREITE - 30f, normal, 15f)
            y += 8f
        }

        y += 6f
        c.drawText("6. Anmerkungen:", RAND_LINKS, y, fett)
        val boxOben = y + 8f
        val boxHoehe = 60f
        c.drawRect(RectF(RAND_LINKS, boxOben, RAND_RECHTS, boxOben + boxHoehe), Paint().apply { style = Paint.Style.STROKE })
        umbrochenerText(c, f.anmerkungen, RAND_LINKS + 6f, boxOben + 16f, TEXTBREITE - 12f, normal, 15f)
        y = boxOben + boxHoehe + 26f

        c.drawText("Datum: ${f.datum}", RAND_LINKS, y, fett)
        y += 46f

        val spaltenBreite = TEXTBREITE / 3f
        unterschriftsFeld(c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], RAND_LINKS, y, spaltenBreite - 8f)
        unterschriftsFeld(c, "Obmann", unterschriften["obmann"], RAND_LINKS + spaltenBreite, y, spaltenBreite - 8f)
        unterschriftsFeld(c, "Spieler", unterschriften["spieler"], RAND_LINKS + 2 * spaltenBreite, y, spaltenBreite - 8f)

        if (f.gestempelt) {
            zeichneStempel(c, 420f, y + 60f)
        }

        c.drawText("Zusatz zur Vereinbarung ASK Mannersdorf", RAND_LINKS, SEITENHOEHE - 30f, klein)
        dokument.finishPage(seite)
    }

    // ---------- Hilfsfunktionen ----------

    private fun feld(c: Canvas, label: String, wert: String, x: Float, y: Float, labelPaint: Paint, wertPaint: Paint): Float {
        c.drawText(label, x, y, labelPaint)
        c.drawText(wert, x + 100f, y, wertPaint)
        return y + 22f
    }

    private fun nummeriertePunkt(c: Canvas, marke: String, text: String, x: Float, y: Float, paint: Paint): Float {
        c.drawText(marke, x - 22f, y, paint)
        val neuesY = umbrochenerText(c, text, x, y, RAND_RECHTS - x, paint, 15f)
        return neuesY + 6f
    }

    /** Zeichnet Text mit einfachem Wortumbruch und liefert die Y-Position NACH der letzten Zeile. */
    private fun umbrochenerText(c: Canvas, text: String, x: Float, startY: Float, maxBreite: Float, paint: Paint, zeilenHoehe: Float): Float {
        var y = startY
        for (absatz in text.split("\n")) {
            if (absatz.isBlank()) {
                y += zeilenHoehe
                continue
            }
            var zeile = StringBuilder()
            for (wort in absatz.split(" ")) {
                val testZeile = if (zeile.isEmpty()) wort else "${zeile} $wort"
                if (paint.measureText(testZeile) > maxBreite && zeile.isNotEmpty()) {
                    c.drawText(zeile.toString(), x, y, paint)
                    zeile = StringBuilder(wort)
                    y += zeilenHoehe
                } else {
                    zeile = StringBuilder(testZeile)
                }
            }
            if (zeile.isNotEmpty()) {
                c.drawText(zeile.toString(), x, y, paint)
                y += zeilenHoehe
            }
        }
        return y
    }

    private fun unterschriftsFeld(c: Canvas, label: String, bitmap: Bitmap?, x: Float, y: Float, breite: Float) {
        val labelPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val hoehe = 70f
        c.drawText(label, x, y, labelPaint)
        c.drawRect(RectF(x, y + 8f, x + breite, y + 8f + hoehe), Paint().apply { style = Paint.Style.STROKE })
        if (bitmap != null) {
            val zielBreite = breite - 10f
            val zielHoehe = (zielBreite * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtMost(hoehe - 10f)
            c.drawBitmap(
                bitmap, null,
                RectF(x + 5f, y + 13f, x + 5f + zielBreite, y + 13f + zielHoehe),
                null
            )
        }
    }

    /** Runder, halbtransparenter Stempel-Vermerk - zeigt an, dass das Dokument abgeschlossen/gesperrt ist. */
    private fun zeichneStempel(c: Canvas, cx: Float, cy: Float) {
        val kreisPaint = Paint().apply {
            color = Color.rgb(0, 110, 60)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 200
        }
        c.save()
        c.rotate(-12f, cx, cy)
        c.drawCircle(cx, cy, 55f, kreisPaint)
        c.drawCircle(cx, cy, 47f, kreisPaint)
        val textPaint = Paint().apply {
            color = Color.rgb(0, 110, 60)
            textSize = 13f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            alpha = 220
        }
        c.drawText("ASK", cx, cy - 6f, textPaint)
        c.drawText("MANNERSDORF", cx, cy + 10f, Paint().apply {
            color = Color.rgb(0, 110, 60); textSize = 8f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; alpha = 220
        })
        c.drawText("GESTEMPELT", cx, cy + 24f, Paint().apply {
            color = Color.rgb(0, 110, 60); textSize = 7f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; alpha = 220
        })
        c.restore()
    }
}
