package com.lelux.calc3d.calc

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val sym = DecimalFormatSymbols(Locale.ITALY)
    private val money = DecimalFormat("#,##0.00", sym)
    private val dec1 = DecimalFormat("#,##0.#", sym)
    private val dec2 = DecimalFormat("#,##0.##", sym)
    private val int = DecimalFormat("#,##0", sym)
    private val date = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

    fun euro(v: Double): String = "${money.format(v)} €"
    fun num(v: Double): String = dec2.format(v)
    fun num1(v: Double): String = dec1.format(v)
    fun grams(v: Double): String = if (v >= 100) "${int.format(v)} g" else "${dec1.format(v)} g"
    fun percent(v: Double): String = "${dec1.format(v)}%"
    fun date(ms: Long): String = date.format(Date(ms))

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun hours(h: Double): String = duration((h * 3600).toLong())

    /** Legge numeri scritti all'italiana o all'inglese ("1.234,5", "12,5", "12.5"). */
    fun parse(text: String): Double? {
        var t = text.trim().replace(" ", "").replace("€", "").replace("%", "")
        if (t.isEmpty()) return null
        if (t.contains(',') && t.contains('.')) t = t.replace(".", "")
        return t.replace(',', '.').toDoubleOrNull()
    }
}
