package com.lelux.calc3d.pdf

import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.calc.QuoteResult
import com.lelux.calc3d.data.AppData
import com.lelux.calc3d.data.Quote

/** Messaggio con la formattazione di WhatsApp/Telegram: *grassetto* e _corsivo_. */
object ShareText {
    fun build(quote: Quote, result: QuoteResult, data: AppData): String {
        val s = data.settings
        val title = quote.title.ifBlank { quote.parts.joinToString(" + ") { it.name } }
        return buildString {
            append("📋 *PREVENTIVO ${quote.number}*\n")
            if (title.isNotBlank()) append("*$title*\n")
            if (quote.customer.isNotBlank()) append("👤 *Cliente:* ${quote.customer}\n")
            append("\n*ARTICOLI:*\n")
            quote.parts.forEachIndexed { i, p ->
                val material = p.filaments.map { it.type }.distinct().joinToString(" + ")
                append("• ${p.name} (x${p.quantity})")
                if (material.isNotBlank()) append(" – $material")
                append("\n")
                append("   Unitario: ${Fmt.euro(result.unitPrices.getOrElse(i) { 0.0 })} | Totale: ${Fmt.euro(result.lineTotals.getOrElse(i) { 0.0 })}\n")
            }
            append("\n━━━━━━━━━━━━━━━━\n")
            if (result.vat > 0.004) {
                append("*Imponibile:* ${Fmt.euro(result.price - result.vat)}\n")
                append("*IVA ${Fmt.num1(s.vatPercent)}%:* ${Fmt.euro(result.vat)}\n")
            }
            append("💶 *TOTALE: ${Fmt.euro(result.price)}*\n")
            append("⏱️ *Tempi di produzione:* ${quote.deliveryDays} giorni lavorativi\n")
            if (s.paymentTerms.isNotBlank()) append("💳 *Pagamento:* ${s.paymentTerms}\n")
            append("📅 *Valido fino al:* ${Fmt.date(quote.createdAt + quote.validityDays * 86_400_000L)}\n")
            if (quote.notes.isNotBlank()) append("\n📝 ${quote.notes}\n")
            append("\n_Domande o pronto a confermare? Rispondi pure a questo messaggio!_")
            val biz = s.business.name
            if (biz.isNotBlank()) append("\n\n— $biz")
        }
    }
}
