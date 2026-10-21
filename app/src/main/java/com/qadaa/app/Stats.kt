@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
fun StatsScreen(vm: Vm) {
    val ar = Settings.arabic
    val f = vm.statF
    val all by vm.all.collectAsStateWithLifecycle()
    var chart by remember { mutableStateOf(0) }
    val lo = f.lo()
    val hi = f.hi()
    val td = today()

    val donePeriod = remember(all, f.kind, f.from, f.to) {
        all.filter { q -> q.done && (q.doneDay ?: Long.MIN_VALUE) in lo..hi }
    }
    val done = if (f.prayer == -1) donePeriod else donePeriod.filter { it.prayer == f.prayer }
    val perDay = done.groupBy { it.doneDay ?: 0L }.mapValues { it.value.size }
    val total = done.size
    val span = if (perDay.isEmpty()) 0L else perDay.keys.max() - perDay.keys.min() + 1
    val avg = if (span > 0) total.toDouble() / span else 0.0
    val best = perDay.maxByOrNull { it.value }
    val allDoneDays = remember(all) { all.filter { it.done }.mapNotNull { it.doneDay }.toSet() }
    val match: (Qada) -> Boolean = { f.prayer == -1 || it.prayer == f.prayer }
    val pending = all.count { !it.done && match(it) }
    val recent = all.count { it.done && match(it) && (it.doneDay ?: -1L) in (td - 13)..td } / 14.0
    val eta = if (pending > 0 && recent > 0) fmt(td + Math.ceil(pending / recent).toLong()) else "—"

    val base = all.filter { it.done && match(it) }
    val g = base.groupBy { it.doneDay ?: 0L }.mapValues { it.value.size }
    val vals: List<Int>
    val first: String
    val last: String
    when (chart) {
        0 -> {
            vals = (13 downTo 0).map { g[td - it] ?: 0 }
            first = fmtS(td - 13); last = fmtS(td)
        }
        1 -> {
            vals = (11 downTo 0).map { w -> (0..6).sumOf { k -> g[td - w * 7L - k] ?: 0 } }
            first = fmtS(td - 83); last = fmtS(td)
        }
        else -> {
            val ymc = base.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.doneDay ?: 0L)) }
                .mapValues { it.value.size }
            vals = (11 downTo 0).map { ymc[YearMonth.now().minusMonths(it.toLong())] ?: 0 }
            first = YearMonth.now().minusMonths(11).toString(); last = YearMonth.now().toString()
        }
    }

    val byYear = remember(all, f.prayer) {
        val src = if (f.prayer == -1) all else all.filter { it.prayer == f.prayer }
        src.groupBy { LocalDate.ofEpochDay(it.day).year }.toSortedMap()
            .map { en -> Triple(en.key, en.value.count { it.done }, en.value.size) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrayerChips(f)
        PeriodChips(f)

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine(tr("المقضي في الفترة", "Made up in period"), "$total")
                StatLine(tr("المتوسط اليومي", "Daily average"), String.format(Locale.US, "%.1f", avg))
                StatLine(
                    tr("أفضل يوم", "Best day"),
                    if (best == null) "—" else fmt(best.key) + " (" + best.value + ")"
                )
                StatLine(tr("السلسلة الحالية", "Current streak"), streakNow(allDoneDays).toString())
                StatLine(tr("أطول سلسلة", "Longest streak"), bestStreak(allDoneDays).toString())
                StatLine(tr("المتبقي", "Remaining"), "$pending")
                StatLine(tr("الانتهاء المتوقع", "Expected finish"), eta)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(tr("14 يوماً", "14 days"), tr("12 أسبوعاً", "12 weeks"), tr("12 شهراً", "12 months"))
                        .forEachIndexed { i, s ->
                            FilterChip(selected = chart == i, onClick = { chart = i }, label = { Text(s) })
                        }
                }
                Spacer(Modifier.height(10.dp))
                BarChart(vals, first, last)
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("التوزيع حسب الصلاة", "By prayer"), style = MaterialTheme.typography.titleSmall)
                val counts = Settings.activeTypes().map { t -> t to donePeriod.count { it.prayer == t.ordinal } }
                val mx = counts.maxOf { it.second }.coerceAtLeast(1)
                counts.forEach { (t, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.label(ar), Modifier.width(80.dp), fontSize = 13.sp)
                        LinearProgressIndicator(progress = { c.toFloat() / mx }, modifier = Modifier.weight(1f))
                        Text("$c", Modifier.width(44.dp), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }

        if (byYear.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("التقدم حسب سنة الفوات", "Progress by missed year"), style = MaterialTheme.typography.titleSmall)
                    byYear.forEach { (y, d, n) ->
                        val pct = if (n == 0) 0 else d * 100 / n
                        Column {
                            Row {
                                Text("$y", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("$pct%  ($d / $n)", fontSize = 13.sp)
                            }
                            LinearProgressIndicator(progress = { if (n == 0) 0f else d.toFloat() / n }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarChart(vals: List<Int>, first: String, last: String) {
    val mx = (vals.maxOrNull() ?: 0).coerceAtLeast(1)
    val col = MaterialTheme.colorScheme.primary
    Column {
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            vals.forEach { v ->
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight((v.toFloat() / mx).coerceAtLeast(0.03f))
                        .background(if (v == 0) col.copy(alpha = 0.25f) else col, RoundedCornerShape(4.dp))
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(first, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("max $mx", fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(last, fontSize = 10.sp)
        }
    }
}

private val MILES = listOf(10, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
private val STREAKS = listOf(3, 7, 30, 100)

@Composable
fun AchievementsScreen(vm: Vm) {
    val all by vm.all.collectAsStateWithLifecycle()
    val days = remember(all) { all.filter { it.done }.mapNotNull { it.doneDay }.sorted() }
    val total = days.size
    val set = remember(all) { days.toSet() }
    val best = bestStreak(set)
    val cur = streakNow(set)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            tr("المجموع: ", "Total: ") + total + "   ·   " + tr("السلسلة الحالية: ", "Current streak: ") + cur,
            style = MaterialTheme.typography.titleMedium
        )
        MILES.forEach { m ->
            Badge2(
                title = tr("$m صلاة مقضية", "$m prayers made up"),
                earned = total >= m,
                progress = total.toFloat() / m,
                sub = if (total >= m) fmt(days[m - 1]) else "$total / $m"
            )
        }
        STREAKS.forEach { s ->
            Badge2(
                title = tr("$s أيام متتالية", "$s days in a row"),
                earned = best >= s,
                progress = best.toFloat() / s,
                sub = if (best >= s) tr("تم", "Earned") else "$best / $s"
            )
        }
    }
}

@Composable
private fun Badge2(title: String, earned: Boolean, progress: Float, sub: String) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(if (earned) cs.primary else cs.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (earned) Icons.Filled.Check else Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (earned) cs.onPrimary else cs.outline
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (!earned) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
