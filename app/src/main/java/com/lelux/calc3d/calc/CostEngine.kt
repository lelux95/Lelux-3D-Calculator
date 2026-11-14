package com.lelux.calc3d.calc

import com.lelux.calc3d.data.AppData
import com.lelux.calc3d.data.Filament
import com.lelux.calc3d.data.Part
import com.lelux.calc3d.data.Printer
import com.lelux.calc3d.data.Quote
import com.lelux.calc3d.data.Rounding
import com.lelux.calc3d.data.SalesChannel
import com.lelux.calc3d.data.Settings
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

data class PartCost(
    val part: Part,
    val material: Double,
    val energy: Double,
    val depreciation: Double,
    val labor: Double,
    val fixed: Double,
    val failure: Double,
    /** Costo di un singolo pezzo */
    val perPiece: Double,
    /** Costo per la quantita' richiesta */
    val total: Double,
    val totalHours: Double,
    val totalGrams: Double,
)

data class QuoteResult(
    val parts: List<PartCost>,
    val material: Double,
    val energy: Double,
    val depreciation: Double,
    val labor: Double,
    val fixed: Double,
    val failure: Double,
    val packaging: Double,
    val other: Double,
    val productionCost: Double,
    val markupPercent: Double,
    val profit: Double,
    val channelFees: Double,
    val vat: Double,
    val price: Double,
    val totalHours: Double,
    val totalGrams: Double,
    /** Prezzo unitario e totale di ciascun pezzo, ripartito in proporzione al costo */
    val unitPrices: List<Double>,
    val lineTotals: List<Double>,
    val markupClamped: Boolean,
) {
    val marginPercent: Double get() = if (price > 0) profit / price * 100 else 0.0
    val profitPerHour: Double get() = if (totalHours > 0) profit / totalHours else 0.0
    val isEmpty: Boolean get() = parts.isEmpty()
}

object CostEngine {

    fun filamentFor(usageId: String?, type: String, data: AppData): Filament? =
        data.filaments.firstOrNull { it.id == usageId }
            ?: data.filaments.firstOrNull { it.type.equals(type, ignoreCase = true) }
            ?: data.filaments.firstOrNull { type.isNotBlank() && it.type.uppercase().startsWith(type.uppercase().take(3)) }

    fun printerFor(part: Part, data: AppData): Printer? =
        data.printers.firstOrNull { it.id == part.printerId }
            ?: data.printers.firstOrNull { it.id == data.settings.defaultPrinterId }
            ?: data.printers.firstOrNull()

    fun partCost(part: Part, data: AppData): PartCost {
        val s = data.settings
        val printer = printerFor(part, data) ?: Printer()
        val hours = part.printHours
        var material = 0.0
        part.filaments.forEach { u ->
            val f = filamentFor(u.filamentId, u.type, data)
            material += u.grams * (f?.pricePerKg ?: 20.0) / 1000.0
        }
        val purgePrice = part.filaments.firstOrNull()?.let { filamentFor(it.filamentId, it.type, data)?.pricePerKg } ?: 20.0
        material += part.purgeGrams * purgePrice / 1000.0

        val energy = hours * printer.powerWatts / 1000.0 * s.electricityPrice
        val perHourMachine = (if (printer.lifetimeHours > 0) printer.purchasePrice / printer.lifetimeHours else 0.0) + printer.maintenancePerHour
        val depreciation = hours * perHourMachine
        val fixed = hours * s.fixedCostPerHour
        val setupLabor = s.setupMinutesPerPlate / 60.0 * s.laborRate
        val failure = s.failureRatePercent / 100.0 * (material + energy)

        val ppp = max(1, part.piecesPerPlate).toDouble()
        val qty = max(1, part.quantity)
        val postLaborPiece = s.postMinutesPerPiece / 60.0 * s.laborRate
        val plateCost = material + energy + depreciation + fixed + setupLabor + failure
        val perPiece = plateCost / ppp + postLaborPiece
        val factor = qty / ppp
        return PartCost(
            part = part,
            material = material * factor,
            energy = energy * factor,
            depreciation = depreciation * factor,
            labor = setupLabor * factor + postLaborPiece * qty,
            fixed = fixed * factor,
            failure = failure * factor,
            perPiece = perPiece,
            total = perPiece * qty,
            totalHours = hours * factor,
            totalGrams = part.totalGrams * factor,
        )
    }

    fun round(value: Double, r: Rounding): Double = when (r) {
        Rounding.NONE -> (value * 100).roundToLong() / 100.0
        Rounding.CENT_10 -> ceil(value * 10 - 1e-9) / 10.0
        Rounding.HALF -> ceil(value * 2 - 1e-9) / 2.0
        Rounding.EURO -> ceil(value - 1e-9)
        Rounding.NINETY -> {
            val base = floor(value) + 0.90
            if (base + 1e-9 >= value) base else base + 1.0
        }
    }

    fun channelFor(quote: Quote, data: AppData): SalesChannel? =
        data.channels.firstOrNull { it.id == quote.channelId }
            ?: data.channels.firstOrNull { it.id == data.settings.defaultChannelId }
            ?: data.channels.firstOrNull()

    fun compute(quote: Quote, data: AppData): QuoteResult {
        val s: Settings = data.settings
        val costs = quote.parts.map { partCost(it, data) }
        val hasParts = costs.isNotEmpty()
        val packaging = if (hasParts) s.packagingPerOrder else 0.0
        val other = if (hasParts) s.otherCostsPerOrder else 0.0
        val production = costs.sumOf { it.total } + packaging + other

        var markup = if (quote.targetProfit != null && production > 0) quote.targetProfit / production * 100 else quote.markupPercent
        var clamped = false
        if (s.profitProtection && markup < s.minSafeMarkup) {
            markup = s.minSafeMarkup
            clamped = true
        }
        val wantedProfit = production * markup / 100.0
        val ch = channelFor(quote, data)
        val pct = (ch?.percentFee ?: 0.0).coerceIn(0.0, 95.0) / 100.0
        val fixedFee = if (hasParts) ch?.fixedFee ?: 0.0 else 0.0
        val vatRate = s.vatPercent / 100.0

        val net = production + wantedProfit
        val preVat = (net + fixedFee) / (1 - pct)
        val gross = if (hasParts) round(preVat * (1 + vatRate), s.rounding) else 0.0

        val priceNoVat = gross / (1 + vatRate)
        val fees = if (hasParts) priceNoVat * pct + fixedFee else 0.0
        val vat = gross - priceNoVat
        val profit = priceNoVat - fees - production

        // Ripartizione del prezzo sui singoli pezzi
        val sumCost = costs.sumOf { it.total }
        val lineTotals = costs.map { if (sumCost > 0) gross * it.total / sumCost else 0.0 }
            .map { (it * 100).roundToLong() / 100.0 }
            .toMutableList()
        if (lineTotals.isNotEmpty()) {
            val diff = ((gross - lineTotals.sum()) * 100).roundToLong() / 100.0
            lineTotals[lineTotals.lastIndex] = lineTotals.last() + diff
        }
        val unitPrices = costs.mapIndexed { i, c -> lineTotals[i] / max(1, c.part.quantity) }

        return QuoteResult(
            parts = costs,
            material = costs.sumOf { it.material },
            energy = costs.sumOf { it.energy },
            depreciation = costs.sumOf { it.depreciation },
            labor = costs.sumOf { it.labor },
            fixed = costs.sumOf { it.fixed },
            failure = costs.sumOf { it.failure },
            packaging = packaging,
            other = other,
            productionCost = production,
            markupPercent = if (production > 0) profit / production * 100 else markup,
            profit = profit,
            channelFees = fees,
            vat = vat,
            price = gross,
            totalHours = costs.sumOf { it.totalHours },
            totalGrams = costs.sumOf { it.totalGrams },
            unitPrices = unitPrices,
            lineTotals = lineTotals,
            markupClamped = clamped,
        )
    }
}
