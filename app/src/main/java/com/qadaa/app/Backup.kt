package com.qadaa.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Backup {
    fun autoDir(c: Context): File = File(c.getExternalFilesDir(null) ?: c.filesDir, "backups")

    suspend fun writeText(c: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        c.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        Unit
    }

    suspend fun readText(c: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
    }

    // ---- CSV (same layout as a day x prayer table: TRUE / FALSE / qada date) ----
    fun buildCsv(list: List<Qada>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("اليوم")
        PrayerType.values().forEach { sb.append(',').append(it.ar) }
        sb.append('\n')
        list.groupBy { it.day }.toSortedMap().forEach { (day, rows) ->
            sb.append(fmt(day))
            val m = rows.associateBy { it.prayer }
            for (p in PrayerType.values()) {
                val q = m[p.ordinal]
                sb.append(',')
                sb.append(
                    when {
                        q == null -> "TRUE"
                        q.done -> fmt(q.doneDay ?: day)
                        else -> "FALSE"
                    }
                )
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun parseCsv(text: String): List<Qada> {
        val out = ArrayList<Qada>()
        for (raw in text.removePrefix("\uFEFF").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val sep = if ('\t' in line) '\t' else if (';' in line) ';' else ','
            val cells = line.split(sep).map { it.trim().trim('"') }
            val day = parseDate(cells[0]) ?: continue
            for (i in 1 until cells.size) {
                if (i - 1 >= PrayerType.values().size) break
                val v = cells[i]
                if (v.isEmpty() || v.equals("TRUE", true)) continue
                if (v.equals("FALSE", true)) {
                    out.add(Qada(day, i - 1))
                } else {
                    val d = parseDate(v) ?: continue
                    out.add(Qada(day, i - 1, true, d, d * 86_400_000L))
                }
            }
        }
        return out
    }

    // ---- JSON backup ----
    fun buildJson(list: List<Qada>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONArray().put(it.day).put(it.prayer).put(it.done)
                    .put(it.doneDay ?: JSONObject.NULL)
                    .put(it.doneAt ?: JSONObject.NULL)
                    .put(it.note ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("app", "qadaa").put("version", 1).put("rows", arr).toString()
    }

    fun parseJson(s: String): List<Qada> {
        val a = JSONObject(s).getJSONArray("rows")
        return List(a.length()) { i ->
            val r = a.getJSONArray(i)
            Qada(
                r.getLong(0), r.getInt(1), r.getBoolean(2),
                if (r.isNull(3)) null else r.getLong(3),
                if (r.isNull(4)) null else r.getLong(4),
                if (r.isNull(5)) null else r.getString(5)
            )
        }
    }

    // ---- PDF ----
    suspend fun exportPdf(c: Context, uri: Uri, list: List<Qada>) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val p = Paint().apply { textSize = 9f; isAntiAlias = true }
        val b = Paint(p).apply { isFakeBoldText = true }
        val t = Paint(p).apply { textSize = 16f; isFakeBoldText = true }
        val types = PrayerType.values()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var cv: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            page = pg
            cv = pg.canvas
            y = 40f
        }

        fun row(label: String, cells: List<String>, paint: Paint) {
            val canvas = cv!!
            canvas.drawText(label, 40f, y, paint)
            cells.forEachIndexed { i, s -> canvas.drawText(s, 110f + i * 56f, y, paint) }
            y += 14f
        }

        newPage()
        cv!!.drawText("Qadaa", 40f, y, t)
        y += 20f
        cv!!.drawText(LocalDate.now().format(DF), 40f, y, p)
        y += 22f
        row("", types.map { it.ar }, b)
        row("Pending", types.map { ty -> list.count { it.prayer == ty.ordinal && !it.done }.toString() }, p)
        row("Done", types.map { ty -> list.count { it.prayer == ty.ordinal && it.done }.toString() }, p)
        y += 12f
        row("Day", types.map { it.ar }, b)
        list.groupBy { it.day }.toSortedMap().forEach { (day, rows) ->
            if (y > h - 40) {
                newPage()
                row("Day", types.map { it.ar }, b)
            }
            val m = rows.associateBy { it.prayer }
            row(fmt(day), types.map { ty ->
                val q = m[ty.ordinal]
                when {
                    q == null -> "-"
                    q.done -> fmtS(q.doneDay ?: day)
                    else -> "x"
                }
            }, p)
        }
        page?.let { doc.finishPage(it) }
        c.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
    }
}

class BackupWorker(c: Context, params: WorkerParameters) : CoroutineWorker(c, params) {
    override suspend fun doWork(): Result {
        Settings.init(applicationContext)
        if (!Settings.autoBackup) return Result.success()
        val list = Db.get(applicationContext).dao().getAll()
        val dir = Backup.autoDir(applicationContext).apply { mkdirs() }
        File(dir, "qadaa-" + LocalDate.now() + ".json").writeText(Backup.buildJson(list))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(7)?.forEach { it.delete() }
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val wm = WorkManager.getInstance(c)
            if (Settings.autoBackup) {
                wm.enqueueUniquePeriodicWork(
                    "backup", ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
                )
            } else {
                wm.cancelUniqueWork("backup")
            }
        }
    }
}
