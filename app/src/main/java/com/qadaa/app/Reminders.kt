package com.qadaa.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    const val CH = "qadaa_reminders"
    const val A_ALARM = "com.qadaa.app.ALARM"
    const val A_DONE = "com.qadaa.app.DONE"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Qadaa", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alarmPi(c: Context, p: Int): PendingIntent = PendingIntent.getBroadcast(
        c, p,
        Intent(c, ReminderReceiver::class.java).setAction(A_ALARM).putExtra("p", p),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleAll(c: Context) {
        Settings.init(c)
        val am = c.getSystemService(AlarmManager::class.java)
        for (p in 0..4) {
            if (Settings.remindersOn) schedule(c, p) else am.cancel(alarmPi(c, p))
        }
    }

    fun schedule(c: Context, p: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val m = Settings.reminderMins[p]
        val now = LocalDateTime.now()
        var t = LocalDate.now().atTime(m / 60, m % 60)
        if (!t.isAfter(now)) t = t.plusDays(1)
        val ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, alarmPi(c, p))
    }

    private fun inQuiet(min: Int): Boolean {
        val a = Settings.quietFrom
        val b = Settings.quietTo
        return if (a <= b) min in a until b else (min >= a || min < b)
    }

    suspend fun notifyIfNeeded(c: Context, p: Int) {
        if (Settings.alertsToday() >= Settings.maxAlerts) return
        val n = LocalTime.now()
        if (inQuiet(n.hour * 60 + n.minute)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val d = Db.get(c).dao()
        val q = (if (Settings.newestFirst) d.lastPending(p) else d.firstPending(p)) ?: return
        val ar = Settings.arabic
        val name = PrayerType.values()[p].label(ar)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), flags)
        val done = PendingIntent.getBroadcast(
            c, 100 + p,
            Intent(c, ReminderReceiver::class.java).setAction(A_DONE).putExtra("p", p), flags
        )
        val nt = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (ar) "وقت القضاء" else "Qada time")
            .setContentText(
                if (ar) "اقضِ صلاة $name فائتة (التالي: ${fmt(q.day)})"
                else "Make up a missed $name (next: ${fmt(q.day)})"
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, if (ar) "صليتُ" else "Done", done)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(200 + p, nt)
        Settings.bumpAlerts()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Settings.init(c)
        val p = i.getIntExtra("p", 0)
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (i.action == Reminders.A_DONE) {
                    Repo.addDone(c, p, today())
                    Repo.refreshWidget(c)
                    c.getSystemService(NotificationManager::class.java).cancel(200 + p)
                } else if (Settings.remindersOn) {
                    Reminders.schedule(c, p)
                    Reminders.notifyIfNeeded(c, p)
                }
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.scheduleAll(c)
    }
}
