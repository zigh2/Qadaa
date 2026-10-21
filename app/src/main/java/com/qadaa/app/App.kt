@file:OptIn(ExperimentalMaterial3Api::class)

package com.qadaa.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App(vm: Vm, locked: Boolean, onUnlock: () -> Unit) {
    if (locked) {
        LockScreen(onUnlock)
    } else {
        var screen by rememberSaveable { mutableStateOf(if (Settings.setupDone) "main" else "setup") }
        var tab by rememberSaveable { mutableStateOf(0) }
        val snack = remember { SnackbarHostState() }
        BackHandler(enabled = screen == "settings" || screen == "periods") {
            screen = if (screen == "periods") "settings" else "main"
        }
        when (screen) {
            "setup" -> PeriodsScreen(vm, true) {
                Settings.setupDone = true
                Settings.save()
                screen = "main"
            }
            "periods" -> PeriodsScreen(vm, false) { screen = "settings" }
            "settings" -> SettingsScreen(vm, { screen = "main" }) { screen = "periods" }
            else -> {
                val titles = listOf(
                    tr("الرئيسية", "Home"), tr("السجلات", "Records"),
                    tr("الإحصائيات", "Statistics"), tr("الإنجازات", "Achievements")
                )
                val icons = listOf(Icons.Filled.Home, Icons.AutoMirrored.Filled.List, Icons.Filled.DateRange, Icons.Filled.Star)
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(titles[tab]) },
                            actions = {
                                IconButton(onClick = { screen = "settings" }) {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            titles.forEachIndexed { i, s ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icons[i], contentDescription = null) },
                                    label = { Text(s, maxLines = 1) }
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snack) }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (tab) {
                            0 -> HomeScreen(vm)
                            1 -> RecordsScreen(vm, snack)
                            2 -> StatsScreen(vm)
                            else -> AchievementsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(tr("التطبيق مقفل", "Locked"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text(tr("فتح", "Unlock")) }
        }
    }
}
