package com.qadaa.app

import android.content.Context
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DF: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val DFP: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")
val DFS: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

fun today(): Long = LocalDate.now().toEpochDay()
fun fmt(d: Long): String = LocalDate.ofEpochDay(d).format(DF)
fun fmtS(d: Long): String = LocalDate.ofEpochDay(d).format(DFS)
fun hhmm(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

fun toast(c: Context, s: String) {
    Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
}

fun streakNow(days: Set<Long>): Int {
    var d = today()
    if (d !in days) d--
    var s = 0
    while (d in days) {
        s++
        d--
    }
    return s
}

fun bestStreak(days: Set<Long>): Int {
    var best = 0
    var cur = 0
    var prev = Long.MIN_VALUE
    for (d in days.sorted()) {
        cur = if (d == prev + 1) cur + 1 else 1
        if (cur > best) best = cur
        prev = d
    }
    return best
}

fun parseDate(s: String): Long? = try {
    if (s.contains('/')) LocalDate.parse(s, DFP).toEpochDay() else LocalDate.parse(s).toEpochDay()
} catch (e: Exception) {
    null
}
