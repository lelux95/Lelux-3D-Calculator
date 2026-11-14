package com.lelux.calc3d.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
enum class Rounding(val label: String) {
    NONE("Nessuno"),
    CENT_10("Ai 10 centesimi"),
    HALF("Ai 50 centesimi"),
    EURO("All'euro"),
    NINETY("Finale ,90")
}

@Serializable
enum class QuoteStatus(val label: String) {
    DRAFT("Bozza"),
    SENT("Inviato"),
    ACCEPTED("Accettato"),
    REJECTED("Rifiutato")
}

@Serializable
enum class PartSource(val label: String) {
    SLICER("Dati esatti dallo slicer"),
    ESTIMATE("Stima dalla geometria"),
    MANUAL("Inserimento manuale")
}

@Serializable
data class Printer(
    val id: String = newId(),
    val name: String = "Stampante",
    val purchasePrice: Double = 250.0,
    val lifetimeHours: Double = 5000.0,
    val maintenancePerHour: Double = 0.05,
    val powerWatts: Double = 80.0,
    /** Velocita' volumetrica media effettiva (mm³/s), usata solo per stimare il tempo dagli STL */
    val avgVolumetricSpeed: Double = 8.0,
)

@Serializable
data class Filament(
    val id: String = newId(),
    val brand: String = "",
    val type: String = "PLA",
    val colorName: String = "",
    val colorHex: String = "#FFFFFF",
    val pricePerKg: Double = 20.0,
    val density: Double = 1.24,
) {
    val label: String
        get() = listOf(type, brand, colorName).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class SalesChannel(
    val id: String = newId(),
    val name: String = "Vendita diretta",
    val percentFee: Double = 0.0,
    val fixedFee: Double = 0.0,
)

@Serializable
data class FilamentUsage(
    val filamentId: String? = null,
    /** Tipo e colore letti dal file, usati per abbinare il filamento del magazzino */
    val type: String = "PLA",
    val colorHex: String = "#FFFFFF",
    val grams: Double = 0.0,
)

@Serializable
data class Part(
    val id: String = newId(),
    val name: String = "Pezzo",
    val printerId: String? = null,
    val filaments: List<FilamentUsage> = listOf(FilamentUsage()),
    val purgeGrams: Double = 0.0,
    /** Tempo di stampa del piatto, in secondi */
    val printSeconds: Long = 0,
    /** Pezzi prodotti con un piatto: peso e tempo sono riferiti al piatto intero */
    val piecesPerPlate: Int = 1,
    val quantity: Int = 1,
    val source: PartSource = PartSource.MANUAL,
    /** File del modello copiato nella memoria dell'app (per il visualizzatore 3D) */
    val modelFile: String? = null,
    val thumbnailFile: String? = null,
    val sizeX: Double = 0.0,
    val sizeY: Double = 0.0,
    val sizeZ: Double = 0.0,
    /** Solo per le stime: volume e superficie del modello in mm³ / mm² */
    val volumeMm3: Double = 0.0,
    val areaMm2: Double = 0.0,
    val infillPercent: Double = 15.0,
) {
    val totalGrams: Double get() = filaments.sumOf { it.grams } + purgeGrams
    val printHours: Double get() = printSeconds / 3600.0
}

@Serializable
data class CatalogItem(
    val id: String = newId(),
    val part: Part,
    val savedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Quote(
    val id: String = newId(),
    val number: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "",
    val customer: String = "",
    val parts: List<Part> = emptyList(),
    val channelId: String? = null,
    val markupPercent: Double = 50.0,
    /** Se impostato, il ricarico viene calcolato per ottenere esattamente questo utile netto */
    val targetProfit: Double? = null,
    val deliveryDays: Int = 3,
    val validityDays: Int = 15,
    val status: QuoteStatus = QuoteStatus.DRAFT,
    val notes: String = "",
    // Valori salvati al momento del salvataggio, per lo storico
    val savedPrice: Double = 0.0,
    val savedCost: Double = 0.0,
    val savedProfit: Double = 0.0,
)

@Serializable
data class Business(
    val name: String = "",
    val contact: String = "",
    val taxCode: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val city: String = "",
    val logoFile: String? = null,
    val brandColor: String = "#FF8A3D",
)

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val business: Business = Business(),
    // Costi
    val electricityPrice: Double = 0.40,
    val laborRate: Double = 10.0,
    val setupMinutesPerPlate: Double = 5.0,
    val postMinutesPerPiece: Double = 2.0,
    val fixedCostPerHour: Double = 0.05,
    val failureRatePercent: Double = 5.0,
    val packagingPerOrder: Double = 1.0,
    val otherCostsPerOrder: Double = 0.0,
    val vatPercent: Double = 0.0,
    // Ricarico
    val defaultMarkup: Double = 50.0,
    val sliderMax: Double = 200.0,
    val b2cMin: Double = 50.0,
    val b2cMax: Double = 150.0,
    val b2bMin: Double = 25.0,
    val b2bMax: Double = 45.0,
    val minSafeMarkup: Double = 20.0,
    val profitProtection: Boolean = false,
    val rounding: Rounding = Rounding.NONE,
    val defaultChannelId: String? = null,
    val defaultPrinterId: String? = null,
    // Preventivi
    val quotePrefix: String = "LX",
    val nextQuoteNumber: Int = 1,
    val validityDays: Int = 15,
    val deliveryDays: Int = 3,
    val paymentTerms: String = "50% di acconto all'ordine, saldo alla consegna o come concordato.",
    val warrantyText: String = "Garanzia di 30 giorni contro difetti di stampa e delaminazione. Non copre uso improprio o esposizione a temperature elevate.",
    val termsText: String = "I tempi di consegna decorrono dalla conferma del modello e dell'acconto. Tolleranze dimensionali secondo gli standard della stampa 3D.",
    val showThumbnailsInPdf: Boolean = true,
    val showWeightInPdf: Boolean = true,
    // Stima da STL
    val estimateInfill: Double = 15.0,
    val estimateWallMm: Double = 1.2,
)
