package com.lelux.calc3d.calc

import com.lelux.calc3d.data.Part
import com.lelux.calc3d.data.Printer
import com.lelux.calc3d.data.Settings
import kotlin.math.min

/**
 * Stima di peso e tempo partendo dalla sola geometria (STL o 3MF non elaborato).
 * Guscio = superficie x spessore pareti; interno = (volume - guscio) x riempimento.
 * Il risultato e' indicativo: per dati esatti si usa il file elaborato dallo slicer.
 */
object Estimator {
    fun estimate(part: Part, printer: Printer, settings: Settings, density: Double): Part {
        if (part.volumeMm3 <= 0) return part
        val shell = min(part.volumeMm3, part.areaMm2 * settings.estimateWallMm)
        val inner = (part.volumeMm3 - shell).coerceAtLeast(0.0) * (part.infillPercent / 100.0)
        val usedMm3 = shell + inner
        val grams = usedMm3 / 1000.0 * density
        val speed = printer.avgVolumetricSpeed.coerceAtLeast(0.5)
        val seconds = (usedMm3 / speed * 1.15 + 180).toLong()
        val fils = part.filaments.ifEmpty { listOf(com.lelux.calc3d.data.FilamentUsage()) }
        // Il peso stimato va al primo filamento; gli altri restano come inseriti a mano
        val newFils = fils.mapIndexed { i, f -> if (i == 0) f.copy(grams = grams) else f }
        return part.copy(filaments = newFils, printSeconds = seconds)
    }
}
