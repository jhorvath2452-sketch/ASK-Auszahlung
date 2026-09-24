package at.mannersdorf.ask.auszahlung.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import at.mannersdorf.ask.auszahlung.R
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsTyp
import java.io.File
import java.io.FileOutputStream

object VertragsPdfErsteller {

    private const val W = 595
    private const val H = 842
    private const val ML = 50f
    private const val MR = 545f
    private const val TW = MR - ML

    // Paint-Hilfsfunktionen
    private fun p(size: Float = 11f, bold: Boolean = false, italic: Boolean = false,
                  align: Paint.Align = Paint.Align.LEFT, color: Int = Color.BLACK) = Paint().apply {
        textSize = size
        typeface = when {
            bold && italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        textAlign = align
        this.color = color
        isAntiAlias = true
    }
    private fun liniePaint() = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.BLACK }
    private fun boxPaint() = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.BLACK }

    // ─── Wappen zentriert oben ───────────────────────────────────────────────
    private fun zeichneWappen(c: Canvas, context: Context): Float {
        val bm = BitmapFactory.decodeResource(context.resources, R.drawable.ask_wappen_vertrag)
        val zielH = 70f
        val zielW = zielH * bm.width / bm.height.toFloat()
        val x = (W - zielW) / 2f
        c.drawBitmap(bm, null, RectF(x, 12f, x + zielW, 12f + zielH), null)
        return 12f + zielH + 6f
    }

    // ─── Stempel-Bitmap ──────────────────────────────────────────────────────
    private fun stempelBitmap(context: Context): Bitmap =
        BitmapFactory.decodeResource(context.resources, R.drawable.ask_stempel)

    /** Formatiert einen Betrag im deutschen Format: "500" → "500,-", "1000" → "1.000,-" */
    private fun formatiereBetrag(wert: String): String {
        val zahl = wert.trim().replace(",", ".").toDoubleOrNull() ?: return wert
        val ganzzahl = zahl.toLong()
        val formatted = String.format("%,d", ganzzahl).replace(",", ".")
        return "$formatted,-"
    }

    // ─── Zentrierter Stempel ─────────────────────────────────────────────────
    private fun zeichneStempelZentriert(c: Canvas, context: Context, cx: Float, cy: Float, radius: Float = 42f) {
        val bm = stempelBitmap(context)
        val seite = radius * 2
        c.drawBitmap(bm, null, RectF(cx - radius, cy - radius, cx + radius, cy + radius), Paint().apply { alpha = 200 })
    }

    // ─── Textfluss ───────────────────────────────────────────────────────────
    data class TextSpan(val text: String, val bold: Boolean = false, val underline: Boolean = false,
                        val highlight: String? = null)

    /** Zeichnet einen oder mehrere Spans fließend in eine Zeile, mit Zeilenumbruch. Gibt neues Y zurück. */
    private fun zeichneSpans(c: Canvas, spans: List<TextSpan>, x: Float, startY: Float,
                              maxW: Float, zeilenH: Float = 14f): Float {
        var y = startY
        val worte = mutableListOf<Pair<String, TextSpan>>() // wort zu span
        for (span in spans) {
            for (wort in span.text.split(" ")) {
                if (wort.isNotEmpty()) worte.add(wort to span)
            }
        }
        var zeile = mutableListOf<Pair<String, TextSpan>>()
        var zeileBreite = 0f

        fun flushZeile() {
            var xOff = x
            for ((w, s) in zeile) {
                val paint = p(11f, s.bold)
                if (s.highlight != null) {
                    val bgPaint = Paint().apply { color = Color.parseColor(s.highlight); style = Paint.Style.FILL }
                    val wBreite = paint.measureText("$w ")
                    c.drawRect(RectF(xOff, y - 10f, xOff + wBreite, y + 2f), bgPaint)
                }
                c.drawText("$w ", xOff, y, paint)
                if (s.underline) {
                    val wBreite = paint.measureText("$w ")
                    c.drawLine(xOff, y + 1.5f, xOff + wBreite, y + 1.5f, liniePaint())
                }
                xOff += paint.measureText("$w ")
            }
            y += zeilenH
            zeile = mutableListOf()
            zeileBreite = 0f
        }

        for ((wort, span) in worte) {
            val paint = p(11f, span.bold)
            val wBreite = paint.measureText("$wort ")
            if (zeileBreite + wBreite > maxW && zeile.isNotEmpty()) flushZeile()
            zeile.add(wort to span)
            zeileBreite += wBreite
        }
        if (zeile.isNotEmpty()) flushZeile()
        return y
    }

    private fun text(t: String) = TextSpan(t)
    private fun fett(t: String) = TextSpan(t, bold = true)
    private fun ul(t: String) = TextSpan(t, underline = true)
    private fun gelbFett(t: String) = TextSpan(t, bold = true, highlight = "#FFFF00")

    // ─── Einzeiliger Absatz ──────────────────────────────────────────────────
    private fun zeile(c: Canvas, spans: List<TextSpan>, x: Float, y: Float): Float =
        zeichneSpans(c, spans, x, y, MR - x)

    private fun abs(c: Canvas, spans: List<TextSpan>, x: Float, y: Float, maxW: Float = TW): Float =
        zeichneSpans(c, spans, x, y, maxW) + 2f

    // ─── Unterschriftsfeld ───────────────────────────────────────────────────
    private fun unterschriftsFeld(c: Canvas, label: String, bm: Bitmap?, x: Float, y: Float, breite: Float, hoehe: Float = 55f) {
        c.drawText(label, x, y, p(11f, bold = true))
        c.drawRect(RectF(x, y + 4f, x + breite, y + 4f + hoehe), boxPaint())
        if (bm != null) {
            val maxH = hoehe - 6f
            val maxWImg = breite - 6f
            val scale = minOf(maxWImg / bm.width, maxH / bm.height)
            val iW = bm.width * scale
            val iH = bm.height * scale
            c.drawBitmap(bm, null, RectF(x + 3f, y + 7f, x + 3f + iW, y + 7f + iH), null)
        }
        c.drawLine(x, y + 4f + hoehe + 4f, x + breite, y + 4f + hoehe + 4f, liniePaint())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VEREINBARUNG – Seite 1
    // ══════════════════════════════════════════════════════════════════════════
    private fun seite1(dokument: PdfDocument, f: VertragsFormular, context: Context) {
        val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = pg.canvas
        var y = zeichneWappen(c, context)

        // Titel
        val titelPaint = p(18f, bold = true, align = Paint.Align.CENTER)
        c.drawText("VEREINBARUNG", W / 2f, y + 18f, titelPaint)
        y += 28f
        val nrPaint = p(14f, bold = true, align = Paint.Align.CENTER)
        val nrBgPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL }
        val nrText = "Nr. ${f.nummer}"
        val nrBreite = nrPaint.measureText(nrText) + 12f
        c.drawRect(RectF(W / 2f - nrBreite / 2f, y, W / 2f + nrBreite / 2f, y + 16f), nrBgPaint)
        c.drawText(nrText, W / 2f, y + 12f, nrPaint)
        y += 26f

        y = abs(c, listOf(text("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler")), ML, y) + 6f

        // Felder NAME / ADRESSE / MAIL
        fun eingabeFeld(label: String, wert: String, yPos: Float): Float {
            c.drawText(label, ML, yPos, p(11f, bold = true))
            val feldX = ML + 75f
            val feldBreite = MR - feldX
            val bgPaint = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
            c.drawRect(RectF(feldX, yPos - 10f, feldX + feldBreite, yPos + 2f), bgPaint)
            c.drawText(wert, feldX + 2f, yPos, p(11f))
            c.drawLine(feldX, yPos + 2f, feldX + feldBreite, yPos + 2f, liniePaint())
            return yPos + 16f
        }
        y = eingabeFeld("NAME:", f.name, y)
        y = eingabeFeld("ADRESSE:", f.adresse, y)
        y = eingabeFeld("MAIL:", f.mail, y) + 8f

        y = abs(c, listOf(
            text("Der ASK Mannersdorf entschädigt den Spieler mit einem "),
            fett("FIXUM als (Fahrtkosten-Spesenersatz)"),
            text(" pro Anwesenheit beim Training und geleisteten Spielen.")
        ), ML, y) + 4f

        // Punkt 1
        c.drawText("1.", ML, y, p(11f, bold = true))
        c.drawText("Fixum:", ML + 18f, y, p(11f, bold = true).also {
            it.isUnderlineText = true
        })
        val p1x = ML + 90f
        y = abs(c, listOf(
            text("Sowohl der Zeitraum, als auch die Höhe wird mit jedem einzelnen Spieler frei vereinbart. Gleiches gilt für die Auszahlung.")
        ), p1x, y, MR - p1x) + 4f

        c.drawText("Fixum:", ML + 70f, y, p(11f, bold = true))
        c.drawText("€", ML + 110f, y, p(11f))
        val fixBgPaint = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML + 120f, y - 10f, ML + 200f, y + 2f), fixBgPaint)
        c.drawText(formatiereBetrag(f.fixum), ML + 122f, y, p(11f, bold = true))
        c.drawLine(ML + 120f, y + 2f, ML + 200f, y + 2f, liniePaint())
        y += 16f

        c.drawText("Voraussetzungen:", ML + 60f, y, p(11f, bold = true))
        y += 14f

        val abcX = ML + 80f
        val abcW = MR - abcX
        data class Punkt(val marke: String, val spans: List<TextSpan>)
        val punkte = listOf(
            Punkt("a)", listOf(text("Mindestens 90%ige Anwesenheitspflicht beim Training (lt. Anwesenheitsliste). Anwesenheitspflicht bei Verletzung bzw. Krankheit in Absprache mit dem Trainer."))),
            Punkt("b)", listOf(text("Mindestens 50%ige Anwesenheit / Verfügbarkeit als Spieler in der Kampfmannschaft (aller Meisterspiele)."))),
            Punkt("c)", listOf(text("Bei Langzeitverletzungen bekommt der Spieler die pauschalierte AWE max. 1 Monat ab dem Zeitpunkt der Verletzung. In der Zeit werden KEINE Punkteprämien ausbezahlt! Die pauschalierte AWE wird erst wieder ab dem Zeitpunkt der Einsatzfähigkeit (Match-Fit!) des Spielers ausbezahlt."))),
            Punkt("d)", listOf(
                text("Bei Verletzung während der Freizeit*, wird nur der \u201eangefangene Monat\u201c fertig ausbezahlt! (*Dazu zählen auch Verletzungen aufgrund von Einsätzen bei "),
                ul("Futsal- oder Hallenfußballveranstaltungen!"),
                text(" Diese sind außerdem mit dem Trainer abzustimmen!!)")
            ))
        )
        for (pk in punkte) {
            c.drawText(pk.marke, abcX - 20f, y, p(11f))
            y = abs(c, pk.spans, abcX, y, abcW) + 2f
        }
        y += 4f

        c.drawText("2.", ML, y, p(11f, bold = true))
        y = abs(c, listOf(
            text("Der Verein ist berechtigt, bei besonders gravierenden Verstößen durch den Spieler (nachweisliches Selbstverschulden,...), wobei dem Verein ein sogenannter Folgeschaden entsteht (z.B. anschließende Sperre,...), "),
            fett("eine Geldstrafe"),
            text(" (z.B. prozentuelle Reduzierung oder komplette Streichung des Fixums), in Abzug zu bringen.")
        ), ML + 18f, y, MR - ML - 18f)

        // Fußzeile
        c.drawRect(RectF(ML - 10f, H - 28f, MR + 10f, H - 20f), Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL })
        c.drawText("Vereinbarung ASK Mannersdorf", ML, H - 22f, p(8f, color = Color.WHITE))
        c.drawText("Seite 1 von 2", MR, H - 22f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))

        dokument.finishPage(pg)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VEREINBARUNG – Seite 2
    // ══════════════════════════════════════════════════════════════════════════
    private fun seite2(dokument: PdfDocument, f: VertragsFormular, unterschriften: Map<String, Bitmap?>, context: Context) {
        val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, 2).create())
        val c = pg.canvas
        var y = 38f

        val klauseln1 = listOf(
            "3." to listOf(text("Die Entscheidung, ob der Spieler am Spielbericht der Kampfmannschaft aufscheint, obliegt alleine dem Trainer der KM des ASK Mannersdorf.")),
            "4." to listOf(text("Der Trainer legt die Trainingstage fest und führt eine Anwesenheitsliste. Diese dient als Basis für die Auszahlung. Bei einer Trainingsbeteiligung unter "), fett("90%"), text(" behält sich der Verein vor, entsprechende Abzüge bei der Auszahlung zu tätigen.")),
            "5." to listOf(text("Für eine eventuelle Steuerliche Veranlagung muss der Spieler selbst sorgen.")),
            "6." to listOf(text("Der Erhalt der Aufwandsentschädigung stellt für den Spieler weder einen Hauptberuf noch eine Haupteinnahmequelle dar.")),
            "7." to listOf(text("Die Auszahlung erfolgt (sofern nicht anderes vereinbart) monatlich 10x im Jahr.")),
            "8." to listOf(text("Jeder angemeldete Spieler hat einen Jahresmitgliedsbeitrag von € 50,- zu entrichten.")),
            "9." to listOf(text("Jeder angemeldete Spieler hat einen monatlichen Wäsche- & Masseur-Beitrag von mindestens € 30,- bis maximal 50,- zu entrichten! (richtet sich nach dem Aufwand!)")),
            "10." to listOf(text("Über die vereinbarte Summe ist Stillschweigen zu bewahren!")),
            "11." to listOf(
                text("Sollte unter Punkt 1-10 anderes/zusätzliches vereinbart werden, ist dies hier im Vertrag unter Punkt 11 (ANMERKUNGEN) zu ergänzen und schriftlich festzuhalten. Nicht im Vertrag festgehaltene Vereinbarungen gelten als "),
                gelbFett("NICHT GETROFFEN!")
            )
        )
        val klauseln2 = listOf(
            "12." to listOf(text("Laufzeit der Spielervereinbarung: "), TextSpan("30.06.2027", bold = true, underline = true)),
            "13." to listOf(
                text("Utensilien, die vom Verein zur Verfügung gestellt werden, können jederzeit zurückgefordert werden. Sobald der Spieler den Verein verlässt, obliegt es dem Verein, ob die Utensilien wieder abzugeben sind. Werden diese nicht zurückgegeben, wird dem Spieler ein Betrag bis zu "),
                fett("€ 200,-"),
                text(" abgezogen oder in Rechnung gestellt.")
            )
        )

        for ((nr, spans) in klauseln1) {
            c.drawText(nr, ML, y, p(11f, bold = true))
            y = abs(c, spans, ML + 22f, y, MR - ML - 22f) + 3f
        }

        // Anmerkungen-Box direkt nach Punkt 11 (NICHT GETROFFEN!), vor Punkt 12
        y += 4f
        c.drawText("Anmerkungen:", ML, y, p(11f, bold = true))
        val boxTop = y + 5f
        val boxH = 60f
        val bgAnm = Paint().apply { color = Color.parseColor("#EDF4FB"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML + 80f, boxTop, MR, boxTop + boxH), bgAnm)
        c.drawRect(RectF(ML + 80f, boxTop, MR, boxTop + boxH), boxPaint())
        if (f.anmerkungen.isNotBlank()) {
            zeichneSpans(c, listOf(text(f.anmerkungen)), ML + 83f, boxTop + 12f, MR - ML - 86f)
        }
        y = boxTop + boxH + 10f

        for ((nr, spans) in klauseln2) {
            c.drawText(nr, ML, y, p(11f, bold = true))
            y = abs(c, spans, ML + 22f, y, MR - ML - 22f) + 3f
        }

        // Datum
        c.drawText("Datum:", ML, y, p(11f, bold = true))
        c.drawLine(ML + 45f, y + 2f, ML + 180f, y + 2f, liniePaint())
        c.drawText(f.datum, ML + 47f, y, p(11f))
        y += 22f

        // Unterschriften: 2×2 Raster mit Stempel in der Mitte
        val feldB = (TW - 30f) / 2f
        val feldH = 55f
        val xL = ML
        val xR = ML + feldB + 30f
        val yOben = y
        val yUnten = y + feldH + 35f

        unterschriftsFeld(c, "Obmann", unterschriften["obmann"], xL, yOben, feldB, feldH)
        unterschriftsFeld(c, "Spieler", unterschriften["spieler"], xR, yOben, feldB, feldH)
        unterschriftsFeld(c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], xL, yUnten, feldB, feldH)
        unterschriftsFeld(c, "Kassier", unterschriften["kassier"], xR, yUnten, feldB, feldH)

        // Stempel exakt in der Mitte der 4 Felder
        val stempelCX = W / 2f
        val stempelCY = yOben + feldH + 17f
        zeichneStempelZentriert(c, context, stempelCX, stempelCY, 38f)

        // Fußzeile
        c.drawRect(RectF(ML - 10f, H - 28f, MR + 10f, H - 20f), Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL })
        c.drawText("Vereinbarung ASK Mannersdorf", ML, H - 22f, p(8f, color = Color.WHITE))
        c.drawText("Seite 2 von 2", MR, H - 22f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))

        dokument.finishPage(pg)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ZUSATZ ZUR VEREINBARUNG
    // ══════════════════════════════════════════════════════════════════════════
    private fun zusatzSeite(dokument: PdfDocument, f: VertragsFormular, unterschriften: Map<String, Bitmap?>, context: Context) {
        val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = pg.canvas
        var y = zeichneWappen(c, context)

        c.drawText("ZUSATZ", W / 2f, y + 14f, p(18f, bold = true, align = Paint.Align.CENTER))
        y += 20f
        c.drawText("zur", W / 2f, y + 12f, p(12f, align = Paint.Align.CENTER))
        y += 18f
        c.drawText("VEREINBARUNG", W / 2f, y + 14f, p(18f, bold = true, align = Paint.Align.CENTER))
        y += 20f

        // Nummer-Feld (hellblau)
        val nrFeldB = 120f
        val nrFeldX = (W - nrFeldB) / 2f
        val bgBlau = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(nrFeldX, y, nrFeldX + nrFeldB, y + 16f), bgBlau)
        c.drawRect(RectF(nrFeldX, y, nrFeldX + nrFeldB, y + 16f), boxPaint())
        c.drawText(f.nummer, (W / 2f), y + 12f, p(11f, bold = true, align = Paint.Align.CENTER))
        y += 28f

        y = abs(c, listOf(text("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler:")), ML, y) + 4f

        // Spieler-Feld
        val spBg = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML, y, MR, y + 16f), spBg)
        c.drawRect(RectF(ML, y, MR, y + 16f), boxPaint())
        c.drawText(f.name, ML + 3f, y + 12f, p(11f, bold = true))
        y += 28f

        y = abs(c, listOf(
            text("Der ASK Mannersdorf entschädigt den Spieler zusätzlich zum vereinbarten Fixum mit einem monatlichen "),
            fett("BONUS (1)"),
            text(" und/oder einer "),
            fett("PUNKTEPRÄMIE (2):")
        ), ML, y) + 8f

        // 1. Bonus
        c.drawText("1.", ML, y, p(11f, bold = true))
        c.drawText("Bonus:", ML + 14f, y, p(11f, bold = true).also { it.isUnderlineText = true })
        c.drawText("€", ML + 70f, y, p(11f))
        val bonusBg = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML + 80f, y - 10f, ML + 150f, y + 2f), bonusBg)
        c.drawText(formatiereBetrag(f.bonus), ML + 82f, y, p(11f, bold = true))
        c.drawLine(ML + 80f, y + 2f, ML + 150f, y + 2f, liniePaint())
        y += 20f

        // 2. Punkteprämie
        c.drawText("2.", ML, y, p(11f, bold = true))
        y = abs(c, listOf(TextSpan("Punkteprämie als (Fahrtkosten und Spesenersatz):", bold = true, underline = true)), ML + 14f, y, MR - ML - 14f) + 4f

        y = abs(c, listOf(text("Die Prämien werden nach tatsächlicher Spielzeit pro Meisterschaftsbegegnung berechnet.")), ML + 20f, y, MR - ML - 20f)
        val bullets = listOf(
            "Ist der Spieler bei einem Meisterschaftsspiel ab Beginn im Einsatz, besteht Anspruch auf vollen Ersatz.",
            "Ist der Spieler ab der 45. bis zur 90. Minute im Einsatz, erhält er 75%.",
            "Ist dieser ab der 76. Minute im Einsatz erhält er 50%"
        )
        for (b in bullets) {
            c.drawText("•", ML + 28f, y, p(11f))
            y = abs(c, listOf(text(b)), ML + 38f, y, MR - ML - 38f) + 1f
        }
        y += 6f

        fun zahlenFeld(label: String, wert: String, yPos: Float): Float {
            c.drawText(label, ML + 40f, yPos, p(11f, bold = true))
            c.drawText("€", ML + 200f, yPos, p(11f))
            val zFeldBg = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
            c.drawRect(RectF(ML + 212f, yPos - 10f, ML + 300f, yPos + 2f), zFeldBg)
            c.drawText(formatiereBetrag(wert), ML + 214f, yPos, p(11f, bold = true))
            c.drawLine(ML + 212f, yPos + 2f, ML + 300f, yPos + 2f, liniePaint())
            return yPos + 16f
        }
        y = zahlenFeld("Sieg (€)", f.siegProPunkt, y)
        y = zahlenFeld("Unentschieden (€)", f.unentschieden, y) + 8f

        val klauseln3bis5 = listOf(
            "3." to listOf(text("Die Auszahlung des Bonus erfolgt monatlich 10x im Jahr.")),
            "4." to listOf(text("Für die Auszahlung gelten dieselben Bedingungen (Trainingsbeteiligung, etc.) wie in der Vereinbarung festgehalten!")),
            "5." to listOf(text("Über die vereinbarten Summen (Fixum, Punkteprämie) ist Stillschweigen zu bewahren!"))
        )
        for ((nr, spans) in klauseln3bis5) {
            c.drawText(nr, ML, y, p(11f, bold = true))
            y = abs(c, spans, ML + 18f, y, MR - ML - 18f) + 2f
        }
        y += 4f

        // Anmerkungen
        c.drawText("6.", ML, y, p(11f, bold = true))
        c.drawText("Anmerkungen:", ML + 14f, y, p(11f))
        y += 5f
        val anmBoxH = 52f
        val anmBg = Paint().apply { color = Color.parseColor("#EDF4FB"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML, y, MR, y + anmBoxH), anmBg)
        c.drawRect(RectF(ML, y, MR, y + anmBoxH), boxPaint())
        if (f.anmerkungen.isNotBlank()) {
            zeichneSpans(c, listOf(text(f.anmerkungen)), ML + 3f, y + 12f, TW - 6f)
        }
        y += anmBoxH + 14f

        // Datum
        c.drawText("Datum:", ML, y, p(11f, bold = true))
        val datBg = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML + 45f, y - 10f, ML + 160f, y + 2f), datBg)
        c.drawText(f.datum, ML + 47f, y, p(11f))
        c.drawLine(ML + 45f, y + 2f, ML + 160f, y + 2f, liniePaint())
        y += 20f

        // Unterschriften: 3 Spalten (Sportlicher Leiter | Obmann | Spieler)
        // Stempel ragt in die rechte untere Ecke von SportlicherLeiter und linke untere Ecke von Obmann
        val dreiB = (TW - 20f) / 3f
        val x1 = ML
        val x2 = ML + dreiB + 10f
        val x3 = ML + 2f * dreiB + 20f
        val feldH = 55f

        unterschriftsFeld(c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], x1, y, dreiB, feldH)
        unterschriftsFeld(c, "Obmann", unterschriften["obmann"], x2, y, dreiB, feldH)
        unterschriftsFeld(c, "Spieler", unterschriften["spieler"], x3, y, dreiB, feldH)

        // Stempel zwischen Sportlicher Leiter (rechts unten) und Obmann (links unten)
        val stempelCX = x2 // genau auf der Grenze zwischen den beiden
        val stempelCY = y + feldH + 4f // unterer Rand der Felder
        zeichneStempelZentriert(c, context, stempelCX, stempelCY, 32f)

        // Trennlinie unten
        y += feldH + 20f
        c.drawLine(x1, y, x1 + dreiB, y, liniePaint())
        c.drawLine(x2, y, x2 + dreiB, y, liniePaint())
        c.drawLine(x3, y, x3 + dreiB, y, liniePaint())

        // Fußzeile
        c.drawRect(RectF(ML - 10f, H - 28f, MR + 10f, H - 20f), Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL })
        c.drawText("Zusatz zur Vereinbarung ASK Mannersdorf", ML, H - 22f, p(8f, color = Color.WHITE))
        c.drawText("Seite 3", MR, H - 22f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))

        dokument.finishPage(pg)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Öffentliche Schnittstelle
    // ══════════════════════════════════════════════════════════════════════════
    fun erstellePdf(context: Context, formular: VertragsFormular, unterschriften: Map<String, Bitmap?>): File {
        val dok = PdfDocument()
        if (formular.typ == VertragsTyp.VEREINBARUNG) {
            seite1(dok, formular, context)
            seite2(dok, formular, unterschriften, context)
        } else {
            zusatzSeite(dok, formular, unterschriften, context)
        }
        val name = "${formular.typ.name}_${formular.spielerName}_${System.currentTimeMillis()}.pdf"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val datei = File(context.cacheDir, name)
        FileOutputStream(datei).use { dok.writeTo(it) }
        dok.close()
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
}
