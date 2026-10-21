package com.qadaa.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

object Settings {
    private var sp: SharedPreferences? = null

    var setupDone by mutableStateOf(false)
    var newestFirst by mutableStateOf(false)
    var goal by mutableStateOf(5)
    var extras by mutableStateOf(setOf<Int>())
    var remindersOn by mutableStateOf(false)
    var reminderMins by mutableStateOf(listOf(360, 810, 1050, 1230, 1320))
    var quietFrom by mutableStateOf(1380)
    var quietTo by mutableStateOf(300)
    var maxAlerts by mutableStateOf(5)
    var lockOn by mutableStateOf(false)
    var theme by mutableStateOf(0)
    var arabic by mutableStateOf(true)
    var autoBackup by mutableStateOf(true)

    @Synchronized
    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("qadaa", Context.MODE_PRIVATE)
        sp = p
        setupDone = p.getBoolean("setupDone", false)
        newestFirst = p.getBoolean("newestFirst", false)
        goal = p.getInt("goal", 5)
        extras = (p.getString("extras", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        remindersOn = p.getBoolean("remindersOn", false)
        p.getString("rem", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }?.let { reminderMins = it }
        quietFrom = p.getInt("quietFrom", 1380)
        quietTo = p.getInt("quietTo", 300)
        maxAlerts = p.getInt("maxAlerts", 5)
        lockOn = p.getBoolean("lockOn", false)
        theme = p.getInt("theme", 0)
        arabic = p.getBoolean("arabic", true)
        autoBackup = p.getBoolean("autoBackup", true)
    }

    fun save() {
        val e = sp?.edit() ?: return
        e.putBoolean("setupDone", setupDone)
        e.putBoolean("newestFirst", newestFirst)
        e.putInt("goal", goal)
        e.putString("extras", extras.joinToString(","))
        e.putBoolean("remindersOn", remindersOn)
        e.putString("rem", reminderMins.joinToString(","))
        e.putInt("quietFrom", quietFrom)
        e.putInt("quietTo", quietTo)
        e.putInt("maxAlerts", maxAlerts)
        e.putBoolean("lockOn", lockOn)
        e.putInt("theme", theme)
        e.putBoolean("arabic", arabic)
        e.putBoolean("autoBackup", autoBackup)
        e.apply()
    }

    fun activeTypes(): List<PrayerType> =
        PrayerType.values().filter { it.core || it.ordinal in extras }

    fun alertsToday(): Int {
        val p = sp ?: return 0
        return if (p.getLong("alertDay", -1L) == LocalDate.now().toEpochDay()) p.getInt("alertCount", 0) else 0
    }

    fun bumpAlerts() {
        val p = sp ?: return
        val n = alertsToday() + 1
        p.edit().putLong("alertDay", LocalDate.now().toEpochDay()).putInt("alertCount", n).apply()
    }
}
