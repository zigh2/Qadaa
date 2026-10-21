package com.qadaa.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class PrayerType(val ar: String, val en: String, val core: Boolean) {
    FAJR("الفجر", "Fajr", true),
    DHUHR("الظهر", "Dhuhr", true),
    ASR("العصر", "Asr", true),
    MAGHRIB("المغرب", "Maghrib", true),
    ISHA("العشاء", "Isha", true),
    WITR("الوتر", "Witr", false),
    FAST("الصيام", "Fasting", false),
    RAWATIB("الرواتب", "Rawatib", false);

    fun label(isAr: Boolean) = if (isAr) ar else en
}

/**
 * One row = one missed prayer on one specific day.
 * No row for a (day, prayer) means nothing is required for it.
 * done=false -> still pending, done=true -> made up on doneDay.
 */
@Entity(tableName = "qada", primaryKeys = ["day", "prayer"])
data class Qada(
    val day: Long,
    val prayer: Int,
    val done: Boolean = false,
    val doneDay: Long? = null,
    val doneAt: Long? = null,
    val note: String? = null
)

@Dao
interface QadaDao {
    @Query("SELECT * FROM qada")
    fun observeAll(): Flow<List<Qada>>

    @Query("SELECT * FROM qada")
    suspend fun getAll(): List<Qada>

    @Query("SELECT * FROM qada WHERE prayer = :p AND done = 0 ORDER BY day ASC LIMIT 1")
    suspend fun firstPending(p: Int): Qada?

    @Query("SELECT * FROM qada WHERE prayer = :p AND done = 0 ORDER BY day DESC LIMIT 1")
    suspend fun lastPending(p: Int): Qada?

    @Query("SELECT * FROM qada WHERE prayer = :p AND done = 1 AND doneDay = :d ORDER BY doneAt DESC LIMIT 1")
    suspend fun lastDone(p: Int, d: Long): Qada?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(list: List<Qada>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: List<Qada>)

    @Update
    suspend fun update(q: Qada)

    @Query("DELETE FROM qada WHERE day BETWEEN :from AND :to AND prayer IN (:prayers) AND done = 0")
    suspend fun deletePending(from: Long, to: Long, prayers: List<Int>)

    @Query("DELETE FROM qada WHERE day = :d AND prayer = :p")
    suspend fun delete(d: Long, p: Int)

    @Query("DELETE FROM qada")
    suspend fun clear()
}

@Database(entities = [Qada::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): QadaDao

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(c: Context): Db = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "qadaa.db")
                .build().also { inst = it }
        }
    }
}
