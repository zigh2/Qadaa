@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** First-run setup and "manage periods": decide which days/prayers were missed. */
@Composable
fun PeriodsScreen(vm: Vm, first: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ar = Settings.arabic
    val all by vm.all.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(0) }
    var to by remember { mutableStateOf(today() - 1) }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(1).minusYears(1).plusDays(1).toEpochDay()) }
    var yrs by remember { mutableStateOf("1") }
    var mon by remember { mutableStateOf("0") }
    var missed by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf(0) }
    val sel = remember { mutableStateListOf(0, 1, 2, 3, 4) }
    val counts = remember { mutableStateMapOf<Int, String>() }

    fun recalc() {
        from = LocalDate.ofEpochDay(to)
            .minusYears(yrs.toLongOrNull() ?: 0L)
            .minusMonths(mon.toLongOrNull() ?: 0L)
            .plusDays(1).toEpochDay()
    }

    val doneMsg = tr("تم التطبيق", "Applied")
    val badRange = tr("تاريخ البداية بعد تاريخ النهاية", "Start date is after end date")
    val noPrayer = tr("اختر صلاة واحدة على الأقل", "Pick at least one prayer")
    val impMsg = tr("تم استيراد خلايا: ", "Cells imported: ")
    val badMsg = tr("تعذّر قراءة الملف", "Could not read the file")
    val csvIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importCsv(u) { n -> toast(ctx, if (n >= 0) impMsg + n else badMsg) }
    }
    val pendingBy = remember(all) { all.filter { !it.done }.groupingBy { it.prayer }.eachCount() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (first) tr("الإعداد الأولي", "Initial setup") else tr("إدارة الفترات", "Manage periods")) },
                navigationIcon = {
                    if (!first) IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                tr(
                    "حدّد الأيام التي فاتتك فيها صلوات. يمكنك إضافة أكثر من فترة، وتعديل أي يوم لاحقاً من شاشة السجلات.",
                    "Choose the days on which prayers were missed. You can add several periods and edit any day later from Records."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text(tr("بالمدة", "By period")) })
                FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text(tr("بالعدد", "By count")) })
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pick = 2 }) {
                        Text(
                            if (mode == 0) tr("حتى تاريخ: ", "Until: ") + fmt(to)
                            else tr("آخر يوم فائت: ", "Last missed day: ") + fmt(to)
                        )
                    }
                    if (mode == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = yrs,
                                onValueChange = { s -> if (s.all { it.isDigit() } && s.length <= 3) { yrs = s; recalc() } },
                                label = { Text(tr("سنوات", "Years")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = mon,
                                onValueChange = { s -> if (s.all { it.isDigit() } && s.length <= 3) { mon = s; recalc() } },
                                label = { Text(tr("أشهر", "Months")) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(onClick = { pick = 1 }) { Text(tr("من تاريخ: ", "From: ") + fmt(from)) }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Settings.activeTypes().forEach { t ->
                                FilterChip(
                                    selected = t.ordinal in sel,
                                    onClick = { if (t.ordinal in sel) sel.remove(t.ordinal) else sel.add(t.ordinal) },
                                    label = { Text(t.label(ar)) }
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable { missed = true }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = missed, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("اعتبارها فائتة (تُضاف معلّقة)", "Mark as missed (added as pending)"))
                        }
                        Row(Modifier.fillMaxWidth().clickable { missed = false }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !missed, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("غير مطلوبة (تُحذف المعلّقة فقط، ولا تُمسّ المقضية)", "Not required (removes pending only, keeps made-up logs)"))
                        }
                    } else {
                        Text(
                            tr("عدد الصلوات الفائتة لكل نوع، تُوزَّع على أيام متتالية تنتهي بالتاريخ أعلاه.",
                                "Missed count per type, spread over consecutive days ending on the date above."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Settings.activeTypes().forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.label(ar), Modifier.weight(1f))
                                OutlinedTextField(
                                    value = counts[t.ordinal] ?: "",
                                    onValueChange = { s -> if (s.all { it.isDigit() } && s.length <= 6) counts[t.ordinal] = s },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (mode == 0) {
                                if (to < from) toast(ctx, badRange)
                                else if (sel.isEmpty()) toast(ctx, noPrayer)
                                else {
                                    vm.applyRange(from, to, sel.toList(), missed)
                                    toast(ctx, doneMsg)
                                }
                            } else {
                                counts.forEach { (p, s) ->
                                    val n = s.toIntOrNull() ?: 0
                                    if (n > 0) vm.applyRange(to - n + 1, to, listOf(p), true)
                                }
                                toast(ctx, doneMsg)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("تطبيق", "Apply")) }
                }
            }

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("المعلّق حالياً", "Currently pending"), style = MaterialTheme.typography.titleSmall)
                    Settings.activeTypes().forEach { t ->
                        Row {
                            Text(t.label(ar), Modifier.weight(1f))
                            Text("${pendingBy[t.ordinal] ?: 0}")
                        }
                    }
                }
            }

            Text(tr("الأنواع الإضافية", "Extra types"), style = MaterialTheme.typography.titleSmall)
            ExtrasChips()

            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("استيراد من ملف CSV", "Import from CSV"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("الصف الأول عناوين، ثم صف لكل يوم: اليوم (dd/MM/yyyy) ثم قيمة لكل صلاة: TRUE = لا شيء مطلوب، FALSE = فائتة، أو تاريخ القضاء.",
                            "First row is a header, then one row per day: the day (dd/MM/yyyy) then one value per prayer: TRUE = nothing required, FALSE = missed, or the qada date."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { csvIn.launch(arrayOf("*/*")) }) { Text(tr("اختيار ملف", "Choose file")) }
                }
            }

            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(if (first) tr("ابدأ", "Start") else tr("تم", "Done"))
            }
        }
    }

    if (pick == 1) DateDialog(from, { from = it }) { pick = 0 }
    if (pick == 2) DateDialog(to, { to = it; recalc() }) { pick = 0 }
}
