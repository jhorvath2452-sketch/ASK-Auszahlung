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

/**
 * Erzeugt PDFs für Vereinbarung und Zusatz zur Vereinbarung.
 * Automatischer Seitenumbruch: Inhalte fließen so lange auf eine Seite bis
 * sie voll ist (H - FUSSZEILE_H - RAND_UNTEN), dann wird eine neue Seite
 * gestartet. Damit bleibt keine Seite halb leer.
 */
object VertragsPdfErsteller {

    private const val W = 595
    private const val H = 842
    private const val ML = 50f
    private const val MR = 545f
    private const val TW = MR - ML
    private const val FUSSZEILE_Y = H - 28f
    private const val SEITENRAND_UNTEN = H - 50f  // ab hier neue Seite

    // ── Paint-Hilfsfunktionen ────────────────────────────────────────────────
    private fun p(
        size: Float = 11f, bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT, color: Int = Color.BLACK
    ) = Paint().apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = align
        this.color = color
        isAntiAlias = true
    }
    private fun liniePaint() = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = Color.BLACK }
    private fun boxPaint() = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.BLACK }

    // ── Seitenverwaltung mit automatischem Umbruch ───────────────────────────
    private inner class Seiten(private val dokument: PdfDocument, private val context: Context,
                               private val dateiTitel: String) {
        private var seitenNummer = 0
        var canvas: Canvas private set
        var y: Float = 0f
        private var aktiveSeite: PdfDocument.Page? = null

        init { neueSeite() }

        fun neueSeite() {
            aktiveSeite?.let { dokument.finishPage(it) }
            seitenNummer++
            val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, seitenNummer).create())
            aktiveSeite = pg
            canvas = pg.canvas
            y = 38f
            fusszeile()
        }

        fun finalisieren() { aktiveSeite?.let { dokument.finishPage(it); aktiveSeite = null } }

        /** Prüft ob noch mindestens [benoetigt] Punkte Platz sind, sonst neue Seite. */
        fun sicherstellenPlatz(benoetigt: Float) {
            if (y + benoetigt > SEITENRAND_UNTEN) neueSeite()
        }

        private fun fusszeile() {
            val fg = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
            canvas.drawRect(RectF(ML - 10f, FUSSZEILE_Y, MR + 10f, FUSSZEILE_Y + 8f), fg)
            canvas.drawText(dateiTitel, ML, FUSSZEILE_Y + 6f, p(8f, color = Color.WHITE))
            canvas.drawText("Seite $seitenNummer", MR, FUSSZEILE_Y + 6f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))
        }
    }

    // ── TextSpan ─────────────────────────────────────────────────────────────
    data class TextSpan(val text: String, val bold: Boolean = false,
                        val underline: Boolean = false, val highlight: String? = null)

    private fun text(t: String) = TextSpan(t)
    private fun fett(t: String) = TextSpan(t, bold = true)
    private fun ul(t: String) = TextSpan(t, underline = true)
    private fun gelbFett(t: String) = TextSpan(t, bold = true, highlight = "#FFFF00")

    /** Zeichnet Spans mit Zeilenumbruch. Gibt neues Y zurück. */
    private fun zeichneSpans(c: Canvas, spans: List<TextSpan>, x: Float, startY: Float,
                              maxW: Float, zeilenH: Float = 14f): Float {
        var y = startY
        val worte = mutableListOf<Pair<String, TextSpan>>()
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
                    c.drawRect(RectF(xOff, y - 10f, xOff + paint.measureText("$w "), y + 2f), bgPaint)
                }
                c.drawText("$w ", xOff, y, paint)
                if (s.underline) c.drawLine(xOff, y + 1.5f, xOff + paint.measureText("$w "), y + 1.5f, liniePaint())
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

    private fun abs(c: Canvas, spans: List<TextSpan>, x: Float, y: Float, maxW: Float = TW) =
        zeichneSpans(c, spans, x, y, maxW) + 2f

    // ── Hilfsfunktion: Betrag formatieren ───────────────────────────────────
    private fun formatiereBetrag(wert: String): String {
        val zahl = wert.trim().replace(",", ".").toDoubleOrNull() ?: return wert.ifBlank { "–" }
        val ganzzahl = zahl.toLong()
        val formatted = String.format("%,d", ganzzahl).replace(",", ".")
        return "$formatted,-"
    }

    // ── Wappen ───────────────────────────────────────────────────────────────
    private fun zeichneWappen(c: Canvas, context: Context): Float {
        val bm = BitmapFactory.decodeResource(context.resources, R.drawable.ask_wappen_vertrag)
        val zielH = 70f
        val zielW = zielH * bm.width / bm.height.toFloat()
        val x = (W - zielW) / 2f
        c.drawBitmap(bm, null, RectF(x, 12f, x + zielW, 12f + zielH), null)
        return 12f + zielH + 6f
    }

    // ── Stempel ──────────────────────────────────────────────────────────────
    private fun zeichneStempel(c: Canvas, context: Context, cx: Float, cy: Float, radius: Float = 38f) {
        val bm = BitmapFactory.decodeResource(context.resources, R.drawable.ask_stempel)
        c.drawBitmap(bm, null, RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            Paint().apply { alpha = 200 })
    }

    // ── Eingabefeld (hellblau) ───────────────────────────────────────────────
    private fun eingabeFeld(c: Canvas, label: String, wert: String, y: Float): Float {
        c.drawText(label, ML, y, p(11f, bold = true))
        val feldX = ML + 75f
        val feldBreite = MR - feldX
        val bgPaint = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        c.drawRect(RectF(feldX, y - 10f, feldX + feldBreite, y + 2f), bgPaint)
        c.drawText(wert, feldX + 2f, y, p(11f))
        c.drawLine(feldX, y + 2f, feldX + feldBreite, y + 2f, liniePaint())
        return y + 16f
    }

    // ── Unterschriftsfeld ───────────────────────────────────────────────────
    private fun unterschriftsFeld(c: Canvas, label: String, bm: Bitmap?,
                                   x: Float, y: Float, breite: Float, hoehe: Float = 55f) {
        c.drawText(label, x, y, p(11f, bold = true))
        c.drawRect(RectF(x, y + 4f, x + breite, y + 4f + hoehe), boxPaint())
        if (bm != null) {
            val scale = minOf((breite - 6f) / bm.width, (hoehe - 6f) / bm.height)
            c.drawBitmap(bm, null,
                RectF(x + 3f, y + 7f, x + 3f + bm.width * scale, y + 7f + bm.height * scale), null)
        }
        c.drawLine(x, y + 4f + hoehe + 4f, x + breite, y + 4f + hoehe + 4f, liniePaint())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VEREINBARUNG – fließender Inhalt mit automatischem Seitenumbruch
    // ══════════════════════════════════════════════════════════════════════════
    private fun zeichneVereinbarung(dokument: PdfDocument, f: VertragsFormular,
                                     unterschriften: Map<String, Bitmap?>, context: Context) {
        // Seite 1 startet mit Wappen – danach fließend
        val s = object {
            var seitenNummer = 1
            var aktiveSeite: PdfDocument.Page? = null
            lateinit var c: Canvas
            var y = 0f

            fun start() {
                val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, seitenNummer).create())
                aktiveSeite = pg; c = pg.canvas
                y = zeichneWappen(c, context)
                fusszeile()
            }

            fun neueSeite() {
                aktiveSeite?.let { dokument.finishPage(it) }
                seitenNummer++
                val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, seitenNummer).create())
                aktiveSeite = pg; c = pg.canvas
                y = 38f
                fusszeile()
            }

            fun check(benoetigt: Float = 40f) { if (y + benoetigt > SEITENRAND_UNTEN) neueSeite() }

            fun finish() { aktiveSeite?.let { dokument.finishPage(it) } }

            private fun fusszeile() {
                val fg = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
                c.drawRect(RectF(ML - 10f, FUSSZEILE_Y, MR + 10f, FUSSZEILE_Y + 8f), fg)
                c.drawText("Vereinbarung ASK Mannersdorf", ML, FUSSZEILE_Y + 6f, p(8f, color = Color.WHITE))
                c.drawText("Seite $seitenNummer", MR, FUSSZEILE_Y + 6f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))
            }
        }
        s.start()

        // ── Titel + Nummer ────────────────────────────────────────────────────
        s.c.drawText("VEREINBARUNG", W / 2f, s.y + 18f, p(18f, bold = true, align = Paint.Align.CENTER))
        s.y += 28f
        val nrPaint = p(14f, bold = true, align = Paint.Align.CENTER)
        val nrText = "Nr. ${f.nummer}"
        val nrBreite = nrPaint.measureText(nrText) + 12f
        s.c.drawRect(RectF(W / 2f - nrBreite / 2f, s.y, W / 2f + nrBreite / 2f, s.y + 16f),
            Paint().apply { color = Color.YELLOW; style = Paint.Style.FILL })
        s.c.drawText(nrText, W / 2f, s.y + 12f, nrPaint)
        s.y += 26f

        s.y = abs(s.c, listOf(text("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler")), ML, s.y) + 6f

        // ── Name / Adresse / Mail ─────────────────────────────────────────────
        s.y = eingabeFeld(s.c, "NAME:", f.name, s.y)
        s.y = eingabeFeld(s.c, "ADRESSE:", f.adresse, s.y)
        s.y = eingabeFeld(s.c, "MAIL:", f.mail, s.y) + 8f

        // ── Einleitung Fixum ──────────────────────────────────────────────────
        s.check(25f)
        s.y = abs(s.c, listOf(
            text("Der ASK Mannersdorf entschädigt den Spieler mit einem "),
            fett("FIXUM als (Fahrtkosten-Spesenersatz)"),
            text(" pro Anwesenheit beim Training und geleisteten Spielen.")
        ), ML, s.y) + 4f

        // ── Punkt 1 ───────────────────────────────────────────────────────────
        s.check(60f)
        s.c.drawText("1.", ML, s.y, p(11f, bold = true))
        s.c.drawText("Fixum:", ML + 18f, s.y, p(11f, bold = true).also { it.isUnderlineText = true })
        s.y = abs(s.c, listOf(text("Sowohl der Zeitraum, als auch die Höhe wird mit jedem einzelnen Spieler frei vereinbart. Gleiches gilt für die Auszahlung.")),
            ML + 90f, s.y, MR - ML - 90f) + 4f

        s.c.drawText("Fixum:", ML + 70f, s.y, p(11f, bold = true))
        s.c.drawText("€", ML + 110f, s.y, p(11f))
        s.c.drawRect(RectF(ML + 120f, s.y - 10f, ML + 200f, s.y + 2f),
            Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
        s.c.drawText(formatiereBetrag(f.fixum), ML + 122f, s.y, p(11f, bold = true))
        s.c.drawLine(ML + 120f, s.y + 2f, ML + 200f, s.y + 2f, liniePaint())
        s.y += 16f

        s.c.drawText("Voraussetzungen:", ML + 60f, s.y, p(11f, bold = true))
        s.y += 14f

        // ── a) b) c) d) ───────────────────────────────────────────────────────
        data class ABCPunkt(val marke: String, val spans: List<TextSpan>)
        val abcPunkte = listOf(
            ABCPunkt("a)", listOf(text("Mindestens 90%ige Anwesenheitspflicht beim Training (lt. Anwesenheitsliste). Anwesenheitspflicht bei Verletzung bzw. Krankheit in Absprache mit dem Trainer."))),
            ABCPunkt("b)", listOf(text("Mindestens 50%ige Anwesenheit / Verfügbarkeit als Spieler in der Kampfmannschaft (aller Meisterspiele)."))),
            ABCPunkt("c)", listOf(text("Bei Langzeitverletzungen bekommt der Spieler die pauschalierte AWE max. 1 Monat ab dem Zeitpunkt der Verletzung. In der Zeit werden KEINE Punkteprämien ausbezahlt! Die pauschalierte AWE wird erst wieder ab dem Zeitpunkt der Einsatzfähigkeit (Match-Fit!) des Spielers ausbezahlt."))),
            ABCPunkt("d)", listOf(
                text("Bei Verletzung während der Freizeit*, wird nur der \u201eangefangene Monat\u201c fertig ausbezahlt! (*Dazu zählen auch Verletzungen aufgrund von Einsätzen bei "),
                ul("Futsal- oder Hallenfußballveranstaltungen!"),
                text(" Diese sind außerdem mit dem Trainer abzustimmen!!)")
            ))
        )
        val abcX = ML + 80f
        for (pk in abcPunkte) {
            s.check(30f)
            s.c.drawText(pk.marke, abcX - 20f, s.y, p(11f))
            s.y = abs(s.c, pk.spans, abcX, s.y, MR - abcX) + 2f
        }
        s.y += 4f

        // ── Punkt 2 ───────────────────────────────────────────────────────────
        s.check(40f)
        s.c.drawText("2.", ML, s.y, p(11f, bold = true))
        s.y = abs(s.c, listOf(
            text("Der Verein ist berechtigt, bei besonders gravierenden Verstößen durch den Spieler (nachweisliches Selbstverschulden,...), wobei dem Verein ein sogenannter Folgeschaden entsteht (z.B. anschließende Sperre,...), "),
            fett("eine Geldstrafe"),
            text(" (z.B. prozentuelle Reduzierung oder komplette Streichung des Fixums), in Abzug zu bringen.")
        ), ML + 18f, s.y, MR - ML - 18f) + 4f

        // ── Punkte 3–11 ───────────────────────────────────────────────────────
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
        for ((nr, spans) in klauseln1) {
            s.check(30f)
            s.c.drawText(nr, ML, s.y, p(11f, bold = true))
            s.y = abs(s.c, spans, ML + 22f, s.y, MR - ML - 22f) + 3f
        }

        // ── Anmerkungen-Box (nach Punkt 11, vor Punkt 12) ────────────────────
        s.check(80f)
        s.y += 4f
        s.c.drawText("Anmerkungen:", ML, s.y, p(11f, bold = true))
        val boxTop = s.y + 5f
        val boxH = 60f
        s.c.drawRect(RectF(ML + 80f, boxTop, MR, boxTop + boxH),
            Paint().apply { color = Color.parseColor("#EDF4FB"); style = Paint.Style.FILL })
        s.c.drawRect(RectF(ML + 80f, boxTop, MR, boxTop + boxH), boxPaint())
        if (f.anmerkungen.isNotBlank()) {
            zeichneSpans(s.c, listOf(text(f.anmerkungen)), ML + 83f, boxTop + 12f, MR - ML - 86f)
        }
        s.y = boxTop + boxH + 10f

        // ── Punkte 12–13 ──────────────────────────────────────────────────────
        val klauseln2 = listOf(
            "12." to listOf(text("Laufzeit der Spielervereinbarung: "), TextSpan("30.06.2027", bold = true, underline = true)),
            "13." to listOf(
                text("Utensilien, die vom Verein zur Verfügung gestellt werden, können jederzeit zurückgefordert werden. Sobald der Spieler den Verein verlässt, obliegt es dem Verein, ob die Utensilien wieder abzugeben sind. Werden diese nicht zurückgegeben, wird dem Spieler ein Betrag bis zu "),
                fett("€ 200,-"),
                text(" abgezogen oder in Rechnung gestellt.")
            )
        )
        for ((nr, spans) in klauseln2) {
            s.check(30f)
            s.c.drawText(nr, ML, s.y, p(11f, bold = true))
            s.y = abs(s.c, spans, ML + 22f, s.y, MR - ML - 22f) + 3f
        }

        // ── Datum ─────────────────────────────────────────────────────────────
        s.check(30f)
        s.y += 10f
        s.c.drawText("Datum:", ML, s.y, p(11f, bold = true))
        val datBg = Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL }
        s.c.drawRect(RectF(ML + 45f, s.y - 10f, ML + 200f, s.y + 2f), datBg)
        if (f.datum.isNotBlank()) s.c.drawText(f.datum, ML + 47f, s.y, p(11f))
        s.c.drawLine(ML + 45f, s.y + 2f, ML + 200f, s.y + 2f, liniePaint())
        s.y += 26f

        // ── Unterschriften ────────────────────────────────────────────────────
        val feldH = 55f
        val feldB = (TW - 30f) / 2f
        s.check(feldH * 2 + 60f)
        val xL = ML; val xR = ML + feldB + 30f
        val yOben = s.y
        unterschriftsFeld(s.c, "Obmann", unterschriften["obmann"], xL, yOben, feldB, feldH)
        unterschriftsFeld(s.c, "Spieler", unterschriften["spieler"], xR, yOben, feldB, feldH)
        val yUnten = yOben + feldH + 35f
        unterschriftsFeld(s.c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], xL, yUnten, feldB, feldH)
        unterschriftsFeld(s.c, "Kassier", unterschriften["kassier"], xR, yUnten, feldB, feldH)
        // Stempel mittig zwischen den 4 Feldern
        zeichneStempel(s.c, context, W / 2f, yOben + feldH + 17f, 38f)

        s.finish()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ZUSATZ ZUR VEREINBARUNG
    // ══════════════════════════════════════════════════════════════════════════
    private fun zeichneZusatz(dokument: PdfDocument, f: VertragsFormular,
                               unterschriften: Map<String, Bitmap?>, context: Context) {
        val pg = dokument.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = pg.canvas
        var y = zeichneWappen(c, context)

        // Fußzeile
        val fg = Paint().apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
        c.drawRect(RectF(ML - 10f, FUSSZEILE_Y, MR + 10f, FUSSZEILE_Y + 8f), fg)
        c.drawText("Zusatz zur Vereinbarung ASK Mannersdorf", ML, FUSSZEILE_Y + 6f, p(8f, color = Color.WHITE))
        c.drawText("Seite 3", MR, FUSSZEILE_Y + 6f, p(8f, align = Paint.Align.RIGHT, color = Color.WHITE))

        c.drawText("ZUSATZ", W / 2f, y + 14f, p(18f, bold = true, align = Paint.Align.CENTER))
        y += 20f
        c.drawText("zur", W / 2f, y + 12f, p(12f, align = Paint.Align.CENTER))
        y += 18f
        c.drawText("VEREINBARUNG", W / 2f, y + 14f, p(18f, bold = true, align = Paint.Align.CENTER))
        y += 20f

        // Nummer-Feld
        val nrFeldB = 140f
        val nrFeldX = (W - nrFeldB) / 2f
        c.drawRect(RectF(nrFeldX, y, nrFeldX + nrFeldB, y + 16f),
            Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
        c.drawRect(RectF(nrFeldX, y, nrFeldX + nrFeldB, y + 16f), boxPaint())
        c.drawText(f.nummer, W / 2f, y + 12f, p(11f, bold = true, align = Paint.Align.CENTER))
        y += 28f

        y = abs(c, listOf(text("Abgeschlossen zwischen dem ASK Mannersdorf und dem Spieler:")), ML, y) + 4f
        c.drawRect(RectF(ML, y, MR, y + 16f),
            Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
        c.drawRect(RectF(ML, y, MR, y + 16f), boxPaint())
        c.drawText(f.name, ML + 3f, y + 12f, p(11f, bold = true))
        y += 28f

        y = abs(c, listOf(
            text("Der ASK Mannersdorf entschädigt den Spieler zusätzlich zum vereinbarten Fixum mit einem monatlichen "),
            fett("BONUS (1)"), text(" und/oder einer "), fett("PUNKTEPRÄMIE (2):")
        ), ML, y) + 8f

        // 1. Bonus
        c.drawText("1.", ML, y, p(11f, bold = true))
        c.drawText("Bonus:", ML + 14f, y, p(11f, bold = true).also { it.isUnderlineText = true })
        c.drawText("€", ML + 70f, y, p(11f))
        c.drawRect(RectF(ML + 80f, y - 10f, ML + 160f, y + 2f),
            Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
        c.drawText(formatiereBetrag(f.bonus), ML + 82f, y, p(11f, bold = true))
        c.drawLine(ML + 80f, y + 2f, ML + 160f, y + 2f, liniePaint())
        y += 20f

        // 2. Punkteprämie
        c.drawText("2.", ML, y, p(11f, bold = true))
        y = abs(c, listOf(TextSpan("Punkteprämie als (Fahrtkosten und Spesenersatz):", bold = true, underline = true)),
            ML + 14f, y, MR - ML - 14f) + 4f

        y = abs(c, listOf(text("Die Prämien werden nach tatsächlicher Spielzeit pro Meisterschaftsbegegnung berechnet.")),
            ML + 20f, y, MR - ML - 20f)
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
            c.drawRect(RectF(ML + 212f, yPos - 10f, ML + 300f, yPos + 2f),
                Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
            c.drawText(formatiereBetrag(wert), ML + 214f, yPos, p(11f, bold = true))
            c.drawLine(ML + 212f, yPos + 2f, ML + 300f, yPos + 2f, liniePaint())
            return yPos + 16f
        }
        y = zahlenFeld("Sieg (€)", f.siegProPunkt, y)
        y = zahlenFeld("Unentschieden (€)", f.unentschieden, y) + 8f

        val klauseln = listOf(
            "3." to listOf(text("Die Auszahlung des Bonus erfolgt monatlich 10x im Jahr.")),
            "4." to listOf(text("Für die Auszahlung gelten dieselben Bedingungen (Trainingsbeteiligung, etc.) wie in der Vereinbarung festgehalten!")),
            "5." to listOf(text("Über die vereinbarten Summen (Fixum, Punkteprämie) ist Stillschweigen zu bewahren!"))
        )
        for ((nr, spans) in klauseln) {
            c.drawText(nr, ML, y, p(11f, bold = true))
            y = abs(c, spans, ML + 18f, y, MR - ML - 18f) + 2f
        }
        y += 4f

        // Anmerkungen
        c.drawText("6.", ML, y, p(11f, bold = true))
        c.drawText("Anmerkungen:", ML + 14f, y, p(11f))
        y += 5f
        val anmBoxH = 52f
        c.drawRect(RectF(ML, y, MR, y + anmBoxH),
            Paint().apply { color = Color.parseColor("#EDF4FB"); style = Paint.Style.FILL })
        c.drawRect(RectF(ML, y, MR, y + anmBoxH), boxPaint())
        if (f.anmerkungen.isNotBlank()) zeichneSpans(c, listOf(text(f.anmerkungen)), ML + 3f, y + 12f, TW - 6f)
        y += anmBoxH + 14f

        // Datum
        c.drawText("Datum:", ML, y, p(11f, bold = true))
        c.drawRect(RectF(ML + 45f, y - 10f, ML + 160f, y + 2f),
            Paint().apply { color = Color.parseColor("#D6E4F0"); style = Paint.Style.FILL })
        if (f.datum.isNotBlank()) c.drawText(f.datum, ML + 47f, y, p(11f))
        c.drawLine(ML + 45f, y + 2f, ML + 160f, y + 2f, liniePaint())
        y += 20f

        // Unterschriften: 3 Spalten
        val dreiB = (TW - 20f) / 3f
        val x1 = ML; val x2 = ML + dreiB + 10f; val x3 = ML + 2f * dreiB + 20f
        val feldH = 55f
        unterschriftsFeld(c, "Sportlicher Leiter", unterschriften["sportlicherLeiter"], x1, y, dreiB, feldH)
        unterschriftsFeld(c, "Obmann", unterschriften["obmann"], x2, y, dreiB, feldH)
        unterschriftsFeld(c, "Spieler", unterschriften["spieler"], x3, y, dreiB, feldH)
        // Stempel zwischen Sportlicher Leiter und Obmann
        zeichneStempel(c, context, x2, y + feldH + 4f, 32f)

        // Trennlinie
        y += feldH + 20f
        c.drawLine(x1, y, x1 + dreiB, y, liniePaint())
        c.drawLine(x2, y, x2 + dreiB, y, liniePaint())
        c.drawLine(x3, y, x3 + dreiB, y, liniePaint())

        dokument.finishPage(pg)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Öffentliche Schnittstelle
    // ══════════════════════════════════════════════════════════════════════════
    fun erstellePdf(context: Context, formular: VertragsFormular, unterschriften: Map<String, Bitmap?>): File {
        val dok = PdfDocument()
        if (formular.typ == VertragsTyp.VEREINBARUNG) {
            zeichneVereinbarung(dok, formular, unterschriften, context)
        } else {
            zeichneZusatz(dok, formular, unterschriften, context)
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
