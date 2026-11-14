package com.lelux.calc3d.data

/**
 * Valori consigliati usati nella configurazione guidata.
 * Consumi medi in stampa PLA dal wiki Bambu Lab (A1 mini 80 W, A1 95 W, P1S 105 W).
 * Prezzo corrente: tariffa di riferimento ARERA per il IV trimestre 2026 (43,43 c€/kWh) e
 * media domestica 2025 (35,1 c€/kWh) -> proposta 0,40 €/kWh, da sostituire con quella in bolletta.
 */
object Defaults {

    data class PrinterPreset(
        val name: String,
        val powerWatts: Double,
        val suggestedPrice: Double,
        val volumetricSpeed: Double,
    )

    val printerPresets = listOf(
        PrinterPreset("Bambu Lab A1 mini", 80.0, 249.0, 8.0),
        PrinterPreset("Bambu Lab A1", 95.0, 349.0, 9.0),
        PrinterPreset("Bambu Lab P1S", 105.0, 599.0, 11.0),
        PrinterPreset("Bambu Lab X1 Carbon", 105.0, 1199.0, 11.0),
        PrinterPreset("Prusa MK4S", 90.0, 899.0, 8.0),
        PrinterPreset("Creality K1", 120.0, 399.0, 10.0),
        PrinterPreset("Altra stampante", 120.0, 300.0, 7.0),
    )

    data class FilamentPreset(val type: String, val pricePerKg: Double, val density: Double)

    val filamentPresets = listOf(
        FilamentPreset("PLA", 20.0, 1.24),
        FilamentPreset("PLA Matte", 22.0, 1.24),
        FilamentPreset("PLA Silk", 25.0, 1.24),
        FilamentPreset("PETG", 22.0, 1.27),
        FilamentPreset("ABS", 22.0, 1.04),
        FilamentPreset("ASA", 25.0, 1.07),
        FilamentPreset("TPU", 30.0, 1.21),
        FilamentPreset("PA (Nylon)", 45.0, 1.14),
        FilamentPreset("PC", 35.0, 1.20),
        FilamentPreset("PLA-CF", 30.0, 1.29),
        FilamentPreset("PETG-CF", 35.0, 1.30),
        FilamentPreset("PVA", 50.0, 1.23),
    )

    fun densityFor(type: String): Double {
        val t = type.uppercase()
        return filamentPresets.firstOrNull { t.startsWith(it.type.uppercase()) }?.density
            ?: when {
                t.contains("PETG") -> 1.27
                t.contains("ABS") -> 1.04
                t.contains("ASA") -> 1.07
                t.contains("TPU") -> 1.21
                t.contains("PA") -> 1.14
                t.contains("PC") -> 1.20
                else -> 1.24
            }
    }

    fun defaultChannels() = listOf(
        SalesChannel(name = "Vendita diretta", percentFee = 0.0, fixedFee = 0.0),
        SalesChannel(name = "Vinted (privato)", percentFee = 0.0, fixedFee = 0.0),
        SalesChannel(name = "eBay (privato)", percentFee = 0.0, fixedFee = 0.0),
    )

    val namedColors = listOf(
        "Bianco" to "#FFFFFF", "Nero" to "#1A1A1A", "Grigio" to "#8A8D91", "Rosso" to "#D32F2F",
        "Arancione" to "#FF8A3D", "Giallo" to "#FBC02D", "Verde" to "#43A047", "Blu" to "#1E63D6",
        "Azzurro" to "#4FC3F7", "Viola" to "#7E57C2", "Rosa" to "#F06292", "Marrone" to "#6D4C41",
        "Oro" to "#C9A227", "Argento" to "#BFC4C9", "Trasparente" to "#E0F2F1",
    )
}
