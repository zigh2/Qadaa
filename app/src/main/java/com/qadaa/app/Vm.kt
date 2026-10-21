package com.qadaa.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filter state shared by the table, log and statistics screens. */
class FState {
    var prayer by mutableIntStateOf(-1)
    var status by mutableIntStateOf(0)
    var year by mutableIntStateOf(-1)
    var kind by mutableIntStateOf(0) // 0 all, 1 today, 2 last 7 days, 3 last 30 days, 4 custom
    var from by mutableLongStateOf(today() - 29)
    var to by mutableLongStateOf(today())
    var notesOnly by mutableStateOf(false)

    fun lo(): Long = when (kind) {
        1 -> today()
        2 -> today() - 6
        3 -> today() - 29
        4 -> from
        else -> Long.MIN_VALUE
    }

    fun hi(): Long = when (kind) {
        1, 2, 3 -> today()
        4 -> to
        else -> Long.MAX_VALUE
    }
}

class Vm(app: Application) : AndroidViewModel(app) {
    private val c: Context = app.applicationContext
    val dao = Db.get(c).dao()
    val all: StateFlow<List<Qada>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var selectedDay by mutableLongStateOf(today())
    val tableF = FState()
    val logF = FState()
    val statF = FState()

    fun plus(p: Int, onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            if (!Repo.addDone(c, p, selectedDay)) onEmpty()
            Repo.refreshWidget(c)
        }
    }

    fun minus(p: Int) {
        viewModelScope.launch {
            Repo.undoDone(c, p, selectedDay)
            Repo.refreshWidget(c)
        }
    }

    suspend fun setCell(day: Long, prayer: Int, q: Qada?) {
        if (q == null) dao.delete(day, prayer) else dao.upsert(listOf(q))
        Repo.refreshWidget(c)
    }

    fun applyRange(from: Long, to: Long, prayers: List<Int>, missed: Boolean) {
        viewModelScope.launch {
            Repo.applyRange(c, from, to, prayers, missed)
            Repo.refreshWidget(c)
        }
    }

    fun importCsv(uri: Uri, done: (Int) -> Unit) {
        viewModelScope.launch {
            val n = try {
                val l = Backup.parseCsv(Backup.readText(c, uri))
                dao.upsert(l)
                l.size
            } catch (e: Exception) {
                -1
            }
            Repo.refreshWidget(c)
            done(n)
        }
    }

    fun restore(uri: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                val l = Backup.parseJson(Backup.readText(c, uri))
                Db.get(c).withTransaction {
                    dao.clear()
                    dao.upsert(l)
                }
                true
            } catch (e: Exception) {
                false
            }
            Repo.refreshWidget(c)
            done(ok)
        }
    }
}
