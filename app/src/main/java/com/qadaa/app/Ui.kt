@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val LocalAr = staticCompositionLocalOf { true }

@Composable
fun tr(ar: String, en: String): String = if (LocalAr.current) ar else en

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE9), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F), secondaryContainer = Color(0xFFCCE8E3), onSecondaryContainer = Color(0xFF05201C),
    background = Color(0xFFF5F9F8), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF5F9F8), onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9F0EE), surfaceContainerHigh = Color(0xFFE3EAE8),
    surfaceContainerHighest = Color(0xFFDDE4E2)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FD4C6), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFCDEFE9),
    secondary = Color(0xFFB0CCC7), secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E3),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF6B1B14),
    surfaceContainerLowest = Color(0xFF090F0E), surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120), surfaceContainerHigh = Color(0xFF242B2A),
    surfaceContainerHighest = Color(0xFF2F3635)
)

@Composable
fun QadaaTheme(content: @Composable () -> Unit) {
    val dark = when (Settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        CompositionLocalProvider(
            LocalLayoutDirection provides (if (Settings.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr),
            LocalAr provides Settings.arabic
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) { Column(content = content) }
}

@Composable
fun DateDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }
                onDismiss()
            }) { Text(tr("تم", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } }
    ) { DatePicker(state = st) }
}

@Composable
fun TimeDialog(min: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(initialHour = min / 60, initialMinute = min % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(st.hour * 60 + st.minute)
                onDismiss()
            }) { Text(tr("تم", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
        text = { TimePicker(state = st) }
    )
}

@Composable
fun PrayerChips(f: FState) {
    val ar = Settings.arabic
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = f.prayer == -1, onClick = { f.prayer = -1 }, label = { Text(tr("الكل", "All")) })
        Settings.activeTypes().forEach { t ->
            FilterChip(selected = f.prayer == t.ordinal, onClick = { f.prayer = t.ordinal }, label = { Text(t.label(ar)) })
        }
    }
}

@Composable
fun PeriodChips(f: FState) {
    var pick by remember { mutableStateOf(0) }
    val opts = listOf(
        tr("الكل", "All"), tr("اليوم", "Today"), tr("7 أيام", "7 days"),
        tr("30 يوماً", "30 days"), tr("مخصص", "Custom")
    )
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEachIndexed { i, s ->
                FilterChip(selected = f.kind == i, onClick = { f.kind = i }, label = { Text(s) })
            }
        }
        if (f.kind == 4) {
            Row {
                TextButton(onClick = { pick = 1 }) { Text(tr("من ", "From ") + fmt(f.from)) }
                TextButton(onClick = { pick = 2 }) { Text(tr("إلى ", "To ") + fmt(f.to)) }
            }
        }
    }
    if (pick == 1) DateDialog(f.from, { f.from = it }) { pick = 0 }
    if (pick == 2) DateDialog(f.to, { f.to = it }) { pick = 0 }
}

@Composable
fun ExtrasChips() {
    val ar = Settings.arabic
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PrayerType.values().filter { !it.core }.forEach { t ->
            FilterChip(
                selected = t.ordinal in Settings.extras,
                onClick = {
                    Settings.extras = if (t.ordinal in Settings.extras) Settings.extras - t.ordinal else Settings.extras + t.ordinal
                    Settings.save()
                },
                label = { Text(t.label(ar)) }
            )
        }
    }
}
