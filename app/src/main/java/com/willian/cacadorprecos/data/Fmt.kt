package com.willian.cacadorprecos.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val br = Locale("pt", "BR")

    fun money(v: Double): String =
        NumberFormat.getCurrencyInstance(br).format(v).replace(' ', ' ')

    /** Ex.: "−8,3%", "+4,3%", "0,0%". */
    fun pct(p: Double): String {
        val sinal = when {
            p > 0.05 -> "+"
            p < -0.05 -> "−"
            else -> ""
        }
        return sinal + String.format(br, "%.1f", abs(p)) + "%"
    }

    fun pctPlain(p: Double): String = String.format(br, "%.1f", abs(p)) + "%"

    fun time(t: Long): String = SimpleDateFormat("dd/MM HH:mm", br).format(Date(t))

    fun day(t: Long): String = SimpleDateFormat("dd/MM", br).format(Date(t))

    fun interval(min: Int): String = if (min < 60) "$min min" else "${min / 60} h"

    /** Períodos de guarda do histórico, em dias. */
    val KEEP: List<Int> = listOf(7, 15, 30, 60, 90, 180, 365)

    fun keep(days: Int): String = if (days >= 365) "1 ano" else "$days dias"
}
