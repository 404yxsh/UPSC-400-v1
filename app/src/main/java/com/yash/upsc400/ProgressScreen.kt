package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class Prog(val frac: DoubleArray, val plannedMin: DoubleArray, val doneMin: DoubleArray)

private fun compute(state: AppState, done: Set<String>): Prog {
    val itemList = state.plan.items
    val frac = DoubleArray(itemList.size)
    val planned = DoubleArray(state.plan.headings.size)
    val doneMin = DoubleArray(state.plan.headings.size)
    for (d in state.plan.days) for (s in d.slots) {
        val isDone = s.key in done
        for (p in s.pieces) {
            if (p.itemIdx < 0) continue
            val it = itemList[p.itemIdx]
            if (it.heading in planned.indices) {
                planned[it.heading] += p.minutes.toDouble()
                if (isDone) doneMin[it.heading] += p.minutes.toDouble()
            }
            if (isDone && (p.kind == "gs" || p.kind == "lec" || p.kind == "theory")) frac[p.itemIdx] += (p.f1 - p.f0) / 1000.0
        }
    }
    return Prog(frac, planned, doneMin)
}

private fun pct(x: Double): String = String.format("%.0f%%", x * 100.0)

@Composable
fun ProgressScreen(state: AppState) {
    val tick = state.tick
    val done = remember(tick) { state.db.doneKeys() }
    val prog = remember(tick) { compute(state, done) }
    var tab by rememberSaveable { mutableStateOf("Books") }
    val itemList = state.plan.items
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Progress")
                Caption("Counts a slot as done when you press Done. Pages and lectures follow the plan's page and lecture splits.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in listOf("Books", "Courses", "Syllabus")) Pill(t, tab == t, { tab = t })
                }
            }
        }
        if (tab == "Books") {
            val groups = LinkedHashMap<String, MutableList<Int>>()
            for (i in itemList.indices) {
                val it = itemList[i]
                if (it.kind == 1 || it.kind == 3 || it.size <= 0) continue
                groups.getOrPut(it.resource) { ArrayList() }.add(i)
            }
            items(groups.entries.toList()) { e ->
                val idxs = e.value
                val total = idxs.sumOf { itemList[it].size }
                val got = idxs.sumOf { itemList[it].size * minOf(1.0, prog.frac[it]) }
                val chDone = idxs.count { prog.frac[it] >= 0.98 }
                Panel {
                    Text(e.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = if (total > 0) (got / total).toFloat() else 0f, modifier = Modifier.fillMaxWidth())
                    Caption("${got.toInt()} of $total pages in scope (${pct(if (total > 0) got / total else 0.0)}) · chapters/parts $chDone of ${idxs.size}")
                }
            }
        } else if (tab == "Courses") {
            val groups = LinkedHashMap<String, MutableList<Int>>()
            for (i in itemList.indices) if (itemList[i].kind == 1) groups.getOrPut(itemList[i].resource) { ArrayList() }.add(i)
            items(groups.entries.toList()) { e ->
                val idxs = e.value
                val doneIdx = idxs.filter { prog.frac[it] >= 0.98 }
                val rawAll = idxs.sumOf { itemList[it].size }
                val rawDone = idxs.sumOf { itemList[it].size * minOf(1.0, prog.frac[it]) }
                fun num(i: Int): String = itemList[i].id.substringAfter(':')
                Panel {
                    Text(e.key, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = if (rawAll > 0) (rawDone / rawAll).toFloat() else 0f, modifier = Modifier.fillMaxWidth())
                    Caption("Lectures ${doneIdx.size} of ${idxs.size} · ${String.format("%.1f", rawDone / 60.0)} of ${String.format("%.1f", rawAll / 60.0)} raw hours")
                    Caption("Done: " + (if (doneIdx.isEmpty()) "none yet" else doneIdx.joinToString(", ") { num(it) }))
                    val rem = idxs.filter { it !in doneIdx }
                    Caption("Remaining: " + (if (rem.isEmpty()) "none" else rem.joinToString(", ") { num(it) }))
                }
            }
        } else {
            items(state.plan.headings.indices.toList()) { h ->
                val planned = prog.plannedMin[h]
                if (planned > 0) {
                    val d = prog.doneMin[h]
                    Panel {
                        Text(state.plan.headings[h], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = (d / planned).toFloat(), modifier = Modifier.fillMaxWidth())
                        Caption("${pct(d / planned)} of planned study time done · ${String.format("%.1f", d / 60.0)} of ${String.format("%.1f", planned / 60.0)} h")
                    }
                }
            }
        }
    }
}
