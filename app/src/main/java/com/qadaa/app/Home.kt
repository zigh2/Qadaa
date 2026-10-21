@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeScreen(vm: Vm) {
    val ar = Settings.arabic
    val ctx = LocalContext.current
    val all by vm.all.collectAsStateWithLifecycle()
    val day = vm.selectedDay
    val nf = Settings.newestFirst
    val types = Settings.activeTypes()

    val pend = remember(all, nf) {
        val m = HashMap<Int, Pair<Int, Long>>()
        for (q in all) {
            if (q.done) continue
            val cur = m[q.prayer]
            m[q.prayer] = if (cur == null) Pair(1, q.day)
            else Pair(cur.first + 1, if (nf) maxOf(cur.second, q.day) else minOf(cur.second, q.day))
        }
        m
    }
    val doneOn = remember(all, day) {
        all.filter { it.done && it.doneDay == day }.groupingBy { it.prayer }.eachCount()
    }
    val streak = remember(all) {
        streakNow(all.mapNotNullTo(HashSet<Long>()) { if (it.done) it.doneDay else null })
    }
    val totalToday = types.sumOf { doneOn[it.ordinal] ?: 0 }
    val remaining = types.sumOf { pend[it.ordinal]?.first ?: 0 }
    val emptyMsg = tr("لا توجد صلوات معلّقة لهذا النوع", "Nothing pending for this type")

    var showDate by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    val sel = remember { mutableStateListOf(0, 1, 2, 3, 4) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // qada date (today by default; pick an earlier date to log retroactively)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { showDate = true },
                label = { Text((if (day == today()) tr("اليوم", "Today") + " · " else "") + fmt(day)) },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            if (day != today()) {
                TextButton(onClick = { vm.selectedDay = today() }) { Text(tr("العودة لليوم", "Back to today")) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !nf,
                onClick = { Settings.newestFirst = false; Settings.save() },
                label = { Text(tr("الأقدم أولاً", "Oldest first")) }
            )
            FilterChip(
                selected = nf,
                onClick = { Settings.newestFirst = true; Settings.save() },
                label = { Text(tr("الأحدث أولاً", "Newest first")) }
            )
        }

        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("قضاء هذا اليوم", "Made up this day"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("$totalToday / ${Settings.goal}", fontWeight = FontWeight.Bold)
                    if (streak > 0) Text("   🔥 $streak", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (totalToday.toFloat() / Settings.goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        types.forEach { t ->
            val pd = pend[t.ordinal]
            val n = doneOn[t.ordinal] ?: 0
            Panel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.label(ar), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (pd == null) tr("مكتملة ✓", "Complete ✓")
                            else tr("المتبقي ", "Left ") + pd.first + "  ·  " + tr("التالي ", "Next ") + fmt(pd.second),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(onClick = { vm.minus(t.ordinal) }, enabled = n > 0) {
                        Text("−", fontSize = 22.sp)
                    }
                    Text(
                        "$n",
                        Modifier.widthIn(min = 44.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    FilledIconButton(onClick = { vm.plus(t.ordinal) { toast(ctx, emptyMsg) } }, enabled = pd != null) {
                        Text("+", fontSize = 22.sp)
                    }
                }
            }
        }

        FilledTonalButton(onClick = { full = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("قضاء يوم كامل", "Make up a full day"))
        }
        Text(
            tr("المتبقي الكلي: ", "Total remaining: ") + remaining,
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDate) DateDialog(day, { vm.selectedDay = minOf(it, today()) }) { showDate = false }

    if (full) {
        AlertDialog(
            onDismissRequest = { full = false },
            title = { Text(tr("قضاء يوم كامل", "Full day")) },
            text = {
                Column {
                    PrayerType.values().filter { it.core }.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (t.ordinal in sel) sel.remove(t.ordinal) else sel.add(t.ordinal)
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = t.ordinal in sel, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(t.label(ar))
                        }
                    }
                    Text(
                        tr("التاريخ: ", "Date: ") + fmt(day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    sel.toList().forEach { vm.plus(it) }
                    full = false
                }) { Text(tr("تسجيل", "Log")) }
            },
            dismissButton = { TextButton(onClick = { full = false }) { Text(tr("إلغاء", "Cancel")) } }
        )
    }
}
