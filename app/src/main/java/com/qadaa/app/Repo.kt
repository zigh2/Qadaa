package com.qadaa.app

import android.content.Context
import androidx.glance.appwidget.updateAll

object Repo {
    suspend fun refreshWidget(c: Context) {
        try {
            QadaWidget().updateAll(c)
        } catch (e: Throwable) {
        }
    }

    /** Fills the next pending day of [prayer] (oldest or newest first) with [day] as the qada date. */
    suspend fun addDone(c: Context, prayer: Int, day: Long): Boolean {
        Settings.init(c)
        val d = Db.get(c).dao()
        val q = (if (Settings.newestFirst) d.lastPending(prayer) else d.firstPending(prayer)) ?: return false
        d.update(q.copy(done = true, doneDay = day, doneAt = System.currentTimeMillis()))
        return true
    }

    suspend fun undoDone(c: Context, prayer: Int, day: Long): Boolean {
        val d = Db.get(c).dao()
        val q = d.lastDone(prayer, day) ?: return false
        d.update(q.copy(done = false, doneDay = null, doneAt = null))
        return true
    }

    /** missed=true adds pending rows (never overwrites made-up ones); false removes pending rows only. */
    suspend fun applyRange(c: Context, from: Long, to: Long, prayers: List<Int>, missed: Boolean) {
        val d = Db.get(c).dao()
        if (missed) {
            val list = ArrayList<Qada>()
            for (day in from..to) for (p in prayers) list.add(Qada(day, p))
            d.insertIgnore(list)
        } else {
            d.deletePending(from, to, prayers)
        }
    }
}
