package com.yash.upsc400

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.yash.upsc400.engine.Rollover
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Holds the plan and database; screens read [tick] so they refresh after any change. */
class AppState(val ctx: Context, val plan: Plan, val db: Db) {
    var tick by mutableIntStateOf(0)
        private set

    fun bump() { tick += 1 }

    fun startDate(): LocalDate =
        try { LocalDate.parse(db.setting("start", plan.startIso)) } catch (e: Exception) { LocalDate.parse(plan.startIso) }

    fun rolloverMin(): Int = db.setting("rollover", "240").toIntOrNull() ?: 240

    /** Plan day number now (1 = start date). Can be below 1 before the start or above 400 after the end. */
    fun today(now: Long = System.currentTimeMillis()): Int =
        Rollover.dayNumberAt(Instant.ofEpochMilli(now), ZoneId.systemDefault(), rolloverMin(), startDate())

    fun dateOf(day: Int): LocalDate = dayDate(startDate(), day)

    fun clampDay(d: Int): Int = if (d < 1) 1 else if (d > plan.days.size) plan.days.size else d

    fun lectureLabel(itemIdx: Int): String {
        if (itemIdx < 0) return ""
        val it = plan.items[itemIdx]
        return "Lecture " + it.id.substringAfter(':').substringBefore(':')
    }
}
