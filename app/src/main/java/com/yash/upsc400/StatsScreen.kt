package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400.engine.Pace
import com.yash.upsc400.engine.Rollover
import java.time.Instant
import java.time.ZoneId

private fun hours(sec: Double): String = String.format("%.1f h", sec / 3600.0)
private fun rate(x: Double, unit: String): String = if (x.isNaN()) "no data yet" else String.format("%.1f %s", x, unit)

private fun bar(frac: Double): String {
    val n = Math.round(Math.min(1.0, Math.max(0.0, frac)) * 16).toInt()
    return "█".repeat(n) + "░".repeat(16 - n)
}

@Composable
fun StatsScreen(state: AppState) {
    val tick = state.tick
    val plan = state.plan
    val now = System.currentTimeMillis()
    val data = remember(tick) {
        val sessions = state.db.allSessions()
        val zone = ZoneId.systemDefault()
        val start = state.startDate()
        val roll = state.rolloverMin()
        val perDay = HashMap<Int, Double>()
        val perSubject = LinkedHashMap<String, Double>()
        val perSlotKind = HashMap<String, DoubleArray>()   // slot -> [lecture, reading, problems] seconds
        for (s in sessions) {
            val sec = (if (s.endMs == null && !s.manual) (now - s.startMs) else s.durMs) / 1000.0
            val day = Rollover.dayNumberAt(Instant.ofEpochMilli(s.startMs), zone, roll, start)
            perDay[day] = (perDay[day] ?: 0.0) + sec
            val slot = plan.slotByKey(s.slotKey)
            val subj = if (slot == null) "Other" else if (slot.name == "Mathematics") "Mathematics" else if (slot.name.startsWith("GS slot")) slot.subject else slot.name
            perSubject[subj] = (perSubject[subj] ?: 0.0) + sec
            val arr = perSlotKind.getOrPut(s.slotKey) { DoubleArray(3) }
            val idx = when (s.kind) { "lecture" -> 0; "problems" -> 2; else -> 1 }
            arr[idx] += sec
        }
        val done = state.db.doneProblemsAll()
        val samples = ArrayList<Pace.Sample>()
        var plannedSec = 0.0
        var actualSec = 0.0
        for ((key, problems) in done) {
            val slot = plan.slotByKey(key) ?: continue
            val kinds = perSlotKind[key] ?: DoubleArray(3)
            var pages = 0.0
            var raw = 0.0
            for (p in slot.pieces) {
                if (p.itemIdx < 0) continue
                val it = plan.items[p.itemIdx]
                val f = (p.f1 - p.f0) / 1000.0
                if (p.kind == "lec") raw += it.size * f
                else if (p.kind == "gs" || p.kind == "theory") pages += it.size * f
            }
            samples.add(Pace.Sample(kinds[1], kinds[0], kinds[2], pages, raw, problems.toDouble()))
            plannedSec += slot.planned * 60.0
            actualSec += kinds[0] + kinds[1] + kinds[2]
        }
        Triple(Triple(perDay, perSubject, Pair(plannedSec, actualSec)), Pace.compute(samples), done.size)
    }
    val perDay = data.first.first
    val perSubject = data.first.second
    val plannedSec = data.first.third.first
    val actualSec = data.first.third.second
    val pace = data.second
    val doneSlots = data.third
    val today = state.today()
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("Stats") }
        item {
            Panel {
                Text("Planned versus actual", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("$doneSlots finished slots: planned ${hours(plannedSec)}, logged ${hours(actualSec)}")
                val ratio = Pace.actualToPlanned(actualSec, plannedSec)
                Caption(if (ratio.isNaN()) "Finish a slot to see your pace against the plan." else String.format("You are running at %.0f%% of planned time per finished slot.", ratio * 100))
            }
        }
        item {
            Panel {
                Text("Your effective pace", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("Reading: " + rate(pace.pagesPerHour, "pages per hour"))
                Caption("Problem solving: " + rate(pace.problemsPerHour, "problems per hour") + " (enter problems solved when you press Done on a Mathematics slot)")
                Caption("Lectures: " + rate(pace.lectureRawMinPerHour, "raw lecture minutes per hour"))
                Caption(
                    "Plan assumption: 1.5x speed plus 25% for pauses and notes, so each raw lecture minute costs " + String.format("%.2f", Pace.PLAN_LECTURE_COST) + " minutes."
                )
                if (!pace.lectureCost.isNaN()) {
                    Caption(String.format("Observed: %.2f minutes per raw minute (%.0f%% of the plan assumption). A 60-minute lecture now costs about %.0f minutes instead of %.0f.", pace.lectureCost, pace.lectureCostVsPlan * 100, pace.recalibratedLectureMinutes(60.0), 60.0 * Pace.PLAN_LECTURE_COST))
                } else Caption("Log lecture sessions and finish a lecture slot to recalibrate this.")
            }
        }
        item {
            Panel {
                Text("Last 14 days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val first = Math.max(1, today - 13)
                for (d in first..Math.max(first, today)) {
                    val sec = perDay[d] ?: 0.0
                    Text("Day $d  ${bar(sec / (9 * 3600.0))}  ${hours(sec)}", style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        item {
            Panel {
                Text("Weekly totals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val weeks = HashMap<Int, Double>()
                for ((d, sec) in perDay) if (d >= 1) weeks[(d - 1) / 7 + 1] = (weeks[(d - 1) / 7 + 1] ?: 0.0) + sec
                val lastWeek = Math.max(1, (Math.max(1, today) - 1) / 7 + 1)
                for (w in Math.max(1, lastWeek - 7)..lastWeek) Caption("Week $w: ${hours(weeks[w] ?: 0.0)}")
            }
        }
        item {
            Panel {
                Text("Time by subject (all logged time)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (perSubject.isEmpty()) Caption("Nothing logged yet.")
                for ((k, v) in perSubject.entries.sortedByDescending { it.value }) Caption("$k: ${hours(v)}")
            }
        }
    }
}
