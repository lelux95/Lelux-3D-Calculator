package com.lelux.calc3d.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.calc.QuoteResult
import com.lelux.calc3d.data.AppData
import com.lelux.calc3d.data.Quote
import java.io.File

object QuotePdf {

    private const val W = 595
    private const val H = 842
    private const val M = 40f

    fun parseColor(hex: String, fallback: Int = 0xFFFF8A3D.toInt()): Int =
        runCatching { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrDefault(fallback)

    fun materialLabel(quote: Quote, index: Int): String {
        val p = quote.parts[index]
        return p.filaments.map { it.type }.distinct().joinToString(" + ").ifBlank { "PLA" }
    }

    fun create(quote: Quote, result: QuoteResult, data: AppData, outFile: File): File {
        val s = data.settings
        val biz = s.business
        val brand = parseColor(biz.brandColor)
        val brandLight = Color.argb(28, Color.red(brand), Color.green(brand), Color.blue(brand))
        val textDark = Color.rgb(28, 30, 34)
        val textMuted = Color.rgb(110, 116, 125)
        val boxBg = Color.rgb(246, 247, 249)
        val border = Color.rgb(222, 226, 231)

        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = border }
        fun tp(size: Float, color: Int = textDark, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

        fun text(t: String, x: Float, top: Float, width: Float, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
            if (t.isBlank()) return 0f
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt().coerceAtLeast(10))
                .setAlignment(align).setLineSpacing(1.5f, 1f).setIncludePad(false).build()
            c.save(); c.translate(x, top); layout.draw(c); c.restore()
            return layout.height.toFloat()
        }
        fun measure(t: String, width: Float, paint: TextPaint): Float {
            if (t.isBlank()) return 0f
            return StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt().coerceAtLeast(10))
                .setLineSpacing(1.5f, 1f).setIncludePad(false).build().height.toFloat()
        }

        fun footer() {
            fill.color = brand
            c.drawRect(M, H - 30f, W - M, H - 27f, fill)
            val name = biz.name.ifBlank { "Lelux 3D Calculator" }
            text("$name  •  ${quote.number}  •  Pagina $pageNo", M, H - 22f, W - 2 * M, tp(7.5f, textMuted), Layout.Alignment.ALIGN_CENTER)
        }

        fun newPage() {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            fill.color = brand
            c.drawRect(M, 28f, W - M, 33f, fill)
            y = 46f
        }
        fun ensure(space: Float) { if (y + space > H - 50f) newPage() }

        newPage()

        // ---- Intestazione ----
        val logo: Bitmap? = biz.logoFile?.let { f -> runCatching { BitmapFactory.decodeFile(f) }.getOrNull() }
        var headerH = 0f
        var nameX = M
        if (logo != null) {
            val maxW = 130f; val maxH = 54f
            val ratio = minOf(maxW / logo.width, maxH / logo.height)
            val lw = logo.width * ratio; val lh = logo.height * ratio
            c.drawBitmap(logo, null, RectF(M, y, M + lw, y + lh), Paint(Paint.FILTER_BITMAP_FLAG))
            nameX = M + lw + 12f
            headerH = lh
        }
        val bizName = biz.name.ifBlank { "Preventivo" }
        val nameH = text(bizName, nameX, y + 8f, 250f, tp(17f, textDark, true))
        if (biz.contact.isNotBlank()) text(biz.contact, nameX, y + 10f + nameH, 250f, tp(8.5f, textMuted))
        val details = listOf(biz.city, biz.phone, biz.email, biz.website,
            if (biz.taxCode.isNotBlank()) "C.F./P.IVA ${biz.taxCode}" else "").filter { it.isNotBlank() }.joinToString("\n")
        val detH = text(details, W - M - 200f, y + 2f, 200f, tp(8f, textMuted), Layout.Alignment.ALIGN_OPPOSITE)
        y += maxOf(headerH, nameH + 22f, detH) + 14f
        c.drawLine(M, y, W - M, y, stroke)
        y += 14f

        // ---- Cliente e dati del preventivo ----
        val boxTop = y
        val validUntil = quote.createdAt + quote.validityDays * 86_400_000L
        val right = buildString {
            append("Data: ${Fmt.date(quote.createdAt)}\n")
            append("Valido fino al ${Fmt.date(validUntil)} (${quote.validityDays} giorni)\n")
            append("Consegna: ${quote.deliveryDays} giorni lavorativi dalla conferma")
        }
        val leftH = 14f + measure(quote.customer.ifBlank { "—" }, 270f, tp(12f, textDark, true)) + 4f +
            measure(quote.title, 270f, tp(9f, textDark))
        val rightH = 18f + measure(right, 220f, tp(8.5f, textMuted))
        val boxH = maxOf(leftH, rightH) + 26f
        fill.color = boxBg
        c.drawRoundRect(RectF(M, boxTop, W - M, boxTop + boxH), 8f, 8f, fill)
        c.drawRoundRect(RectF(M, boxTop, W - M, boxTop + boxH), 8f, 8f, stroke)
        fill.color = brand
        c.drawRect(M + 12f, boxTop + 12f, M + 15f, boxTop + boxH - 12f, fill)
        var ly = boxTop + 13f
        ly += text("CLIENTE / DESTINATARIO", M + 24f, ly, 270f, tp(7.5f, textMuted, true)) + 4f
        ly += text(quote.customer.ifBlank { "—" }, M + 24f, ly, 270f, tp(12f, textDark, true)) + 3f
        text(quote.title, M + 24f, ly, 270f, tp(9f, textDark))
        text("#${quote.number}", W - M - 232f, boxTop + 12f, 220f, tp(12f, textDark, true), Layout.Alignment.ALIGN_OPPOSITE)
        text(right, W - M - 232f, boxTop + 30f, 220f, tp(8.5f, textMuted), Layout.Alignment.ALIGN_OPPOSITE)
        y = boxTop + boxH + 16f

        // ---- Tabella ----
        val showThumb = s.showThumbnailsInPdf && quote.parts.any { it.thumbnailFile != null }
        val showWeight = s.showWeightInPdf
        val colNum = M + 8f
        val colThumb = M + 28f
        val colName = if (showThumb) M + 72f else M + 30f
        val colQty = W - M - 175f
        val colUnit = W - M - 135f
        val colTot = W - M - 8f
        val colWeight = W - M - 215f
        val colMat = if (showWeight) W - M - 300f else W - M - 255f
        val nameWidth = colMat - colName - 8f

        fun tableHeader() {
            fill.color = brand
            c.drawRect(M, y, W - M, y + 24f, fill)
            val hp = tp(7.5f, Color.WHITE, true)
            text("#", colNum, y + 8f, 20f, hp)
            text("PRODOTTO", colName, y + 8f, nameWidth, hp)
            text("MATERIALE", colMat, y + 8f, 80f, hp)
            if (showWeight) text("PESO", colWeight - 10f, y + 8f, 45f, hp, Layout.Alignment.ALIGN_OPPOSITE)
            text("QTÀ", colQty - 20f, y + 8f, 30f, hp, Layout.Alignment.ALIGN_OPPOSITE)
            text("PREZZO UNIT.", colUnit - 10f, y + 8f, 70f, hp, Layout.Alignment.ALIGN_OPPOSITE)
            text("TOTALE", colTot - 60f, y + 8f, 60f, hp, Layout.Alignment.ALIGN_OPPOSITE)
            y += 24f
        }
        ensure(60f)
        tableHeader()
        quote.parts.forEachIndexed { i, part ->
            val namePaint = tp(9.5f, textDark, true)
            val sub = buildString {
                if (part.sizeX > 0) append("${Fmt.num1(part.sizeX)} × ${Fmt.num1(part.sizeY)} × ${Fmt.num1(part.sizeZ)} mm")
            }
            val rowH = maxOf(if (showThumb) 48f else 30f, measure(part.name, nameWidth, namePaint) + measure(sub, nameWidth, tp(7.5f)) + 18f)
            if (y + rowH > H - 50f) { newPage(); tableHeader() }
            if (i % 2 == 1) { fill.color = boxBg; c.drawRect(M, y, W - M, y + rowH, fill) }
            text((i + 1).toString().padStart(2, '0'), colNum, y + 10f, 20f, tp(8.5f, textMuted, true))
            if (showThumb) {
                part.thumbnailFile?.let { f ->
                    runCatching { BitmapFactory.decodeFile(f) }.getOrNull()?.let { bmp ->
                        val r = RectF(colThumb, y + 5f, colThumb + 38f, y + 43f)
                        fill.color = Color.WHITE
                        c.drawRoundRect(r, 4f, 4f, fill)
                        c.drawRoundRect(r, 4f, 4f, stroke)
                        val ratio = minOf(34f / bmp.width, 34f / bmp.height)
                        val bw = bmp.width * ratio; val bh = bmp.height * ratio
                        c.drawBitmap(bmp, null, RectF(r.centerX() - bw / 2, r.centerY() - bh / 2, r.centerX() + bw / 2, r.centerY() + bh / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                }
            }
            val nh = text(part.name, colName, y + 9f, nameWidth, namePaint)
            text(sub, colName, y + 11f + nh, nameWidth, tp(7.5f, textMuted))
            text(materialLabel(quote, i), colMat, y + 10f, 80f, tp(8.5f, brand, true))
            if (showWeight) text(Fmt.grams(part.totalGrams / maxOf(1, part.piecesPerPlate)), colWeight - 10f, y + 10f, 45f, tp(8.5f, textDark), Layout.Alignment.ALIGN_OPPOSITE)
            text(part.quantity.toString(), colQty - 20f, y + 10f, 30f, tp(9f, textDark), Layout.Alignment.ALIGN_OPPOSITE)
            text(Fmt.euro(result.unitPrices.getOrElse(i) { 0.0 }), colUnit - 10f, y + 10f, 70f, tp(9f, textDark), Layout.Alignment.ALIGN_OPPOSITE)
            text(Fmt.euro(result.lineTotals.getOrElse(i) { 0.0 }), colTot - 70f, y + 10f, 70f, tp(9f, textDark, true), Layout.Alignment.ALIGN_OPPOSITE)
            y += rowH
            c.drawLine(M, y, W - M, y, stroke)
        }
        y += 16f

        // ---- Pagamento e totali ----
        ensure(110f)
        val half = (W - 2 * M - 16f) / 2
        val payH = 30f + measure(s.paymentTerms, half - 24f, tp(8.5f))
        val totLines = if (result.vat > 0.004) 2 else 1
        val totH = 20f + totLines * 16f + 40f
        val blockH = maxOf(payH, totH)
        fill.color = boxBg
        c.drawRoundRect(RectF(M, y, M + half, y + payH), 8f, 8f, fill)
        c.drawRoundRect(RectF(M, y, M + half, y + payH), 8f, 8f, stroke)
        text("ISTRUZIONI DI PAGAMENTO", M + 12f, y + 11f, half - 24f, tp(7.5f, brand, true))
        text(s.paymentTerms, M + 12f, y + 25f, half - 24f, tp(8.5f))
        val tx = M + half + 16f
        c.drawRoundRect(RectF(tx, y, W - M, y + totH), 8f, 8f, stroke)
        var ty = y + 12f
        val sub = if (result.vat > 0.004) result.price - result.vat else result.price
        text("Subtotale", tx + 12f, ty, 120f, tp(9f, textMuted)); text(Fmt.euro(sub), W - M - 132f, ty, 120f, tp(9f, textDark, true), Layout.Alignment.ALIGN_OPPOSITE)
        ty += 16f
        if (result.vat > 0.004) {
            text("IVA ${Fmt.num1(s.vatPercent)}%", tx + 12f, ty, 120f, tp(9f, textMuted)); text(Fmt.euro(result.vat), W - M - 132f, ty, 120f, tp(9f, textDark, true), Layout.Alignment.ALIGN_OPPOSITE)
            ty += 16f
        }
        fill.color = brand
        c.drawRoundRect(RectF(tx + 8f, ty + 2f, W - M - 8f, ty + 34f), 6f, 6f, fill)
        text("TOTALE", tx + 20f, ty + 12f, 100f, tp(9f, Color.WHITE, true))
        text(Fmt.euro(result.price), W - M - 180f, ty + 9f, 160f, tp(14f, Color.WHITE, true), Layout.Alignment.ALIGN_OPPOSITE)
        y += blockH + 18f

        // ---- Condizioni ----
        val conditions = buildList {
            add("Pagamento: " to s.paymentTerms)
            add("Tempi e consegna: " to "${quote.deliveryDays} giorni lavorativi dalla conferma dell'ordine")
            if (s.warrantyText.isNotBlank()) add("Garanzia: " to s.warrantyText)
            if (s.termsText.isNotBlank()) add("Termini: " to s.termsText)
            if (quote.notes.isNotBlank()) add("Note: " to quote.notes)
        }
        val condPaint = tp(8.3f)
        val condW = W - 2 * M - 24f
        val condH = 30f + conditions.sumOf { (k, v) -> (measure(k + v, condW, condPaint) + 4f).toDouble() }.toFloat()
        ensure(condH + 10f)
        fill.color = boxBg
        c.drawRoundRect(RectF(M, y, W - M, y + condH), 8f, 8f, fill)
        c.drawRoundRect(RectF(M, y, W - M, y + condH), 8f, 8f, stroke)
        var cy = y + 11f
        cy += text("CONDIZIONI COMMERCIALI", M + 12f, cy, condW, tp(7.5f, textMuted, true)) + 6f
        conditions.forEach { (k, v) ->
            val sp = android.text.SpannableString(k + v)
            sp.setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, k.length, 0)
            val layout = StaticLayout.Builder.obtain(sp, 0, sp.length, condPaint, condW.toInt()).setLineSpacing(1.5f, 1f).setIncludePad(false).build()
            c.save(); c.translate(M + 12f, cy); layout.draw(c); c.restore()
            cy += layout.height + 4f
        }
        y += condH + 26f

        // ---- Firma ----
        ensure(60f)
        val sx = W - M - 200f
        fill.color = textMuted
        c.drawRect(sx, y, W - M, y + 1.5f, fill)
        text(biz.name.ifBlank { " " }, sx, y + 8f, 200f, tp(9f, textDark, true), Layout.Alignment.ALIGN_CENTER)
        text("Firma", sx, y + 22f, 200f, tp(8f, textMuted), Layout.Alignment.ALIGN_CENTER)
        text(Fmt.date(quote.createdAt), sx, y + 34f, 200f, tp(7.5f, textMuted), Layout.Alignment.ALIGN_CENTER)

        footer()
        doc.finishPage(page)
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
