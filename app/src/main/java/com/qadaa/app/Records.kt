@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun RecordsScreen(vm: Vm, snack: SnackbarHostState) {
    var mode by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val all by vm.all.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val okMsg = tr("تم التعديل", "Updated")
    val undoMsg = tr("تراجع", "Undo")

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text(tr("الجدول", "Table")) })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text(tr("السجل", "Log")) })
        }
        if (mode == 0) TableView(vm, all) { d, p -> edit = Pair(d, p) }
        else LogView(vm, all) { d, p -> edit = Pair(d, p) }
    }

    edit?.let { e ->
        val d = e.first
        val p = e.second
        val cur = all.firstOrNull { it.day == d && it.prayer == p }
        CellDialog(d, p, cur, onDismiss = { edit = null }) { n ->
            scope.launch {
                vm.setCell(d, p, n)
                if (snack.showSnackbar(okMsg, actionLabel = undoMsg) == SnackbarResult.ActionPerformed) {
                    vm.setCell(d, p, cur)
                }
            }
        }
    }
}

@Composable
private fun TableView(vm: Vm, all: List<Qada>, onCell: (Long, Int) -> Unit) {
    val f = vm.tableF
    val ar = Settings.arabic
    val nf = Settings.newestFirst
    val types = Settings.activeTypes().filter { f.prayer == -1 || it.ordinal == f.prayer }
    val years = remember(all) { all.map { LocalDate.ofEpochDay(it.day).year }.distinct().sorted() }
    val rows = remember(all, nf, f.prayer, f.status, f.year) {
        all.groupBy { it.day }.entries.filter { en ->
            (f.year == -1 || LocalDate.ofEpochDay(en.key).year == f.year) &&
                en.value.any { q ->
                    (f.prayer == -1 || q.prayer == f.prayer) &&
                        (f.status == 0 || (f.status == 1 && !q.done) || (f.status == 2 && q.done))
                }
        }.sortedBy { if (nf) -it.key else it.key }
    }
    val w = (88 + 72 * types.size).dp

    Column(Modifier.fillMaxSize()) {
        PrayerChips(f)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(tr("كل الحالات", "Any status"), tr("معلّقة", "Pending"), tr("مقضية", "Made up"))
                .forEachIndexed { i, s ->
                    FilterChip(selected = f.status == i, onClick = { f.status = i }, label = { Text(s) })
                }
            VerticalSpacer()
            FilterChip(selected = f.year == -1, onClick = { f.year = -1 }, label = { Text(tr("كل السنوات", "All years")) })
            years.forEach { y ->
                FilterChip(selected = f.year == y, onClick = { f.year = y }, label = { Text("$y") })
            }
        }
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text(
                tr("لا توجد بيانات. أضف فترة فائتة من الإعدادات ← إدارة الفترات.", "No data yet. Add a period from Settings → Manage periods."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(w).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(88.dp)) {
                            Text(tr("اليوم", "Day"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        types.forEach {
                            Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                                Text(it.label(ar), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    Divider(Modifier.padding(vertical = 4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(rows, key = { it.key }) { en ->
                            val m = en.value.associateBy { it.prayer }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fmt(en.key), Modifier.width(88.dp), fontSize = 12.sp)
                                types.forEach { t ->
                                    CellBox(m[t.ordinal]) { onCell(en.key, t.ordinal) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalSpacer() {
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun CellBox(q: Qada?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg: Color
    val fg: Color
    val text: String
    when {
        q == null -> { bg = Color.Transparent; fg = cs.outline; text = "✓" }
        q.done -> { bg = cs.primaryContainer; fg = cs.onPrimaryContainer; text = fmtS(q.doneDay ?: q.day) }
        else -> { bg = cs.errorContainer; fg = cs.error; text = "✕" }
    }
    val mark = if (q != null && !q.note.isNullOrBlank()) "*" else ""
    Box(
        Modifier.width(72.dp).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(text + mark, color = fg, fontSize = 12.sp) }
}

@Composable
private fun LogView(vm: Vm, all: List<Qada>, onCell: (Long, Int) -> Unit) {
    val f = vm.logF
    val ar = Settings.arabic
    val lo = f.lo()
    val hi = f.hi()
    val groups = remember(all, f.prayer, f.kind, f.from, f.to, f.notesOnly) {
        all.filter { q ->
            q.done && (f.prayer == -1 || q.prayer == f.prayer) &&
                (q.doneDay ?: Long.MIN_VALUE) in lo..hi &&
                (!f.notesOnly || !q.note.isNullOrBlank())
        }.sortedWith(compareByDescending<Qada> { it.doneDay }.thenByDescending { it.doneAt })
            .groupBy { it.doneDay ?: 0L }
    }
    Column(Modifier.fillMaxSize()) {
        PrayerChips(f)
        PeriodChips(f)
        FilterChip(
            selected = f.notesOnly,
            onClick = { f.notesOnly = !f.notesOnly },
            label = { Text(tr("لها ملاحظة", "Has a note")) }
        )
        if (groups.isEmpty()) {
            Text(
                tr("لا توجد سجلات مطابقة.", "No matching records."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(Modifier.weight(1f).padding(bottom = 8.dp)) {
            groups.forEach { (d, list) ->
                item(key = "h$d") {
                    Text(
                        fmt(d) + "  ·  " + list.size,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                }
                items(list, key = { it.day * 10 + it.prayer }) { q ->
                    Panel(Modifier.padding(vertical = 3.dp).fillMaxWidth().clickable { onCell(q.day, q.prayer) }) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                PrayerType.values()[q.prayer].label(ar) + "  ·  " + tr("عن يوم ", "for ") + fmt(q.day),
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!q.note.isNullOrBlank()) {
                                Text(
                                    q.note ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellDialog(day: Long, prayer: Int, cur: Qada?, onDismiss: () -> Unit, onSave: (Qada?) -> Unit) {
    val ar = Settings.arabic
    var state by remember { mutableStateOf(if (cur == null) 2 else if (cur.done) 1 else 0) }
    var doneDay by remember { mutableStateOf(cur?.doneDay ?: today()) }
    var note by remember { mutableStateOf(cur?.note ?: "") }
    var pick by remember { mutableStateOf(false) }
    val options = listOf(
        tr("معلّقة (لم تُقضَ بعد)", "Pending (not made up yet)"),
        tr("مقضية", "Made up"),
        tr("لا شيء مطلوب", "Nothing required")
    )
    val order = listOf(0, 1, 2)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fmt(day) + " · " + PrayerType.values()[prayer].label(ar)) },
        text = {
            Column {
                order.forEach { i ->
                    Row(
                        Modifier.fillMaxWidth().clickable { state = i }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = state == i, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(options[i])
                    }
                }
                if (state == 1) {
                    OutlinedButton(onClick = { pick = true }) {
                        Text(tr("تاريخ القضاء: ", "Qada date: ") + fmt(doneDay))
                    }
                }
                if (state != 2) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(tr("ملاحظة", "Note")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n: String? = if (note.isBlank()) null else note
                val result: Qada? = when (state) {
                    2 -> null
                    1 -> Qada(day, prayer, true, doneDay, cur?.doneAt ?: System.currentTimeMillis(), n)
                    else -> Qada(day, prayer, false, null, null, n)
                }
                onSave(result)
                onDismiss()
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } }
    )
    if (pick) DateDialog(doneDay, { doneDay = it }) { pick = false }
}
