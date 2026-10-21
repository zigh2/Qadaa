@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: Vm, onBack: () -> Unit, onPeriods: () -> Unit) {
    val ctx = LocalContext.current
    val ar = Settings.arabic
    val scope = rememberCoroutineScope()
    var timePick by remember { mutableStateOf(-1) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val okMsg = tr("تم الحفظ", "Saved")
    val badMsg = tr("تعذّر تنفيذ العملية", "Operation failed")
    val lockMsg = tr("فعّل قفل الشاشة أو البصمة في الجهاز أولاً", "Set up a screen lock or fingerprint on the device first")
    val restoredMsg = tr("تمت الاستعادة", "Restored")

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        if (u != null) scope.launch {
            Backup.writeText(ctx, u, Backup.buildCsv(vm.dao.getAll()))
            toast(ctx, okMsg)
        }
    }
    val pdfOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null) scope.launch {
            Backup.exportPdf(ctx, u, vm.dao.getAll())
            toast(ctx, okMsg)
        }
    }
    val jsonOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch {
            Backup.writeText(ctx, u, Backup.buildJson(vm.dao.getAll()))
            toast(ctx, okMsg)
        }
    }
    val jsonIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) restoreUri = u
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("الإعدادات", "Settings")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)
        ) {
            Sec(tr("المظهر", "Appearance"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("تلقائي", "System"), tr("فاتح", "Light"), tr("ليلي", "Dark")).forEachIndexed { i, s ->
                    FilterChip(selected = Settings.theme == i, onClick = { Settings.theme = i; Settings.save() }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = Settings.arabic, onClick = { Settings.arabic = true; Settings.save() }, label = { Text("العربية") })
                FilterChip(selected = !Settings.arabic, onClick = { Settings.arabic = false; Settings.save() }, label = { Text("English") })
            }

            Sec(tr("القضاء", "Qada"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !Settings.newestFirst,
                    onClick = { Settings.newestFirst = false; Settings.save() },
                    label = { Text(tr("الأقدم أولاً", "Oldest first")) }
                )
                FilterChip(
                    selected = Settings.newestFirst,
                    onClick = { Settings.newestFirst = true; Settings.save() },
                    label = { Text(tr("الأحدث أولاً", "Newest first")) }
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("الهدف اليومي", "Daily goal"), Modifier.weight(1f))
                Stepper(Settings.goal, 1, 100) { Settings.goal = it; Settings.save() }
            }
            Text(tr("الأنواع الإضافية", "Extra types"))
            ExtrasChips()
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onPeriods, modifier = Modifier.fillMaxWidth()) {
                Text(tr("إدارة الفترات / إعادة حساب الفائت", "Manage periods / recalculate missed"))
            }

            Sec(tr("التذكير", "Reminders"))
            SwitchRow(tr("تذكير بالقضاء بعد كل صلاة", "Qada reminder after each prayer"), Settings.remindersOn) { on ->
                Settings.remindersOn = on
                Settings.save()
                if (on && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                Reminders.scheduleAll(ctx)
            }
            if (Settings.remindersOn) {
                Text(
                    tr("اختر وقت التذكير المناسب بعد كل صلاة:", "Pick a reminder time after each prayer:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (i in 0..4) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("بعد ", "After ") + PrayerType.values()[i].label(ar), Modifier.weight(1f))
                        TextButton(onClick = { timePick = i }) { Text(hhmm(Settings.reminderMins[i])) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ساعات هادئة", "Quiet hours"), Modifier.weight(1f))
                    TextButton(onClick = { timePick = 10 }) { Text(hhmm(Settings.quietFrom)) }
                    Text("→")
                    TextButton(onClick = { timePick = 11 }) { Text(hhmm(Settings.quietTo)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("أقصى عدد تنبيهات يومياً", "Max alerts per day"), Modifier.weight(1f))
                    Stepper(Settings.maxAlerts, 1, 5) { Settings.maxAlerts = it; Settings.save() }
                }
            }

            Sec(tr("الأمان", "Security"))
            SwitchRow(tr("قفل التطبيق (بصمة أو رمز الجهاز)", "App lock (fingerprint or device PIN)"), Settings.lockOn) { on ->
                if (on && BiometricManager.from(ctx).canAuthenticate(lockAuthenticators()) != BiometricManager.BIOMETRIC_SUCCESS) {
                    toast(ctx, lockMsg)
                } else {
                    Settings.lockOn = on
                    Settings.save()
                }
            }

            Sec(tr("البيانات", "Data"))
            OutlinedButton(onClick = { csvOut.launch("qadaa.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("تصدير CSV", "Export CSV"))
            }
            OutlinedButton(onClick = { pdfOut.launch("qadaa.pdf") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("تصدير PDF", "Export PDF"))
            }
            OutlinedButton(onClick = { jsonOut.launch("qadaa-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("نسخة احتياطية الآن", "Back up now"))
            }
            OutlinedButton(onClick = { jsonIn.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("استعادة من نسخة احتياطية", "Restore from backup"))
            }
            SwitchRow(tr("نسخ احتياطي تلقائي يومي على الجهاز", "Daily automatic backup on this device"), Settings.autoBackup) { on ->
                Settings.autoBackup = on
                Settings.save()
                BackupWorker.schedule(ctx)
            }
            Text(
                Backup.autoDir(ctx).absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (timePick >= 0) {
        val cur = when {
            timePick < 5 -> Settings.reminderMins[timePick]
            timePick == 10 -> Settings.quietFrom
            else -> Settings.quietTo
        }
        TimeDialog(cur, { m ->
            when {
                timePick < 5 -> {
                    val l = Settings.reminderMins.toMutableList()
                    l[timePick] = m
                    Settings.reminderMins = l
                }
                timePick == 10 -> Settings.quietFrom = m
                else -> Settings.quietTo = m
            }
            Settings.save()
            Reminders.scheduleAll(ctx)
        }) { timePick = -1 }
    }

    restoreUri?.let { u ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(tr("استعادة النسخة الاحتياطية", "Restore backup")) },
            text = { Text(tr("سيتم استبدال كل البيانات الحالية. هل تريد المتابعة؟", "All current data will be replaced. Continue?")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(u) { ok -> toast(ctx, if (ok) restoredMsg else badMsg) }
                    restoreUri = null
                }) { Text(tr("استعادة", "Restore")) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(tr("إلغاء", "Cancel")) } }
        )
    }
}

@Composable
private fun Sec(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(v: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = { if (v > min) onChange(v - 1) }) { Text("−") }
        Text("$v", Modifier.padding(horizontal = 14.dp))
        OutlinedIconButton(onClick = { if (v < max) onChange(v + 1) }) { Text("+") }
    }
}
