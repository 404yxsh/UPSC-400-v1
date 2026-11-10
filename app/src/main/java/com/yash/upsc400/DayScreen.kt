package com.yash.upsc400

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

fun openUrl(ctx: Context, raw: String) {
    val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DayScreen(state: AppState, day: Int, onDay: (Int) -> Unit, onNote: (String) -> Unit) {
    val tick = state.tick
    val dayPlan = state.plan.days[day - 1]
    val today = remember(tick) { state.today() }
    val done = remember(tick) { state.db.doneKeys() }
    val active = remember(tick) { state.db.activeSession() }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val date = state.dateOf(day)
    val doneCount = dayPlan.slots.count { it.key in done }
    val overdue = day < today && dayPlan.slots.any { (it.name == "Mathematics" || it.name.startsWith("GS slot")) && it.key !in done }
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Title(if (day == today) "Today" else "Day $day")
                Text("Day $day of ${state.plan.days.size} · ${date.format(DATE_FMT)}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(typeLabel(dayPlan.type), MaterialTheme.colorScheme.primary)
                    Tag("$doneCount of ${dayPlan.slots.size} slots done", MaterialTheme.colorScheme.secondary)
                    if (overdue) Tag("overdue", WarnAmber)
                }
                if (today < 1) Caption("The plan has not started yet; showing Day $day.")
                if (today > state.plan.days.size) Caption("The 400 days are over; showing Day $day.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDay(state.clampDay(day - 1)) }, enabled = day > 1) { Text("Previous") }
                    OutlinedButton(onClick = { onDay(state.clampDay(day + 1)) }, enabled = day < state.plan.days.size) { Text("Next") }
                    if (day != state.clampDay(today)) TextButton(onClick = { onDay(state.clampDay(today)) }) { Text("Go to today") }
                }
            }
        }
        items(dayPlan.slots) { slot ->
            SlotCard(state, slot, now, active, slot.key in done, tick, onNote)
        }
    }
}

private fun defaultKind(slot: Slot, kinds: List<String>): String {
    val p = slot.pieces.firstOrNull()
    val k = when {
        p == null -> kinds[0]
        p.kind == "lec" -> "lecture"
        p.kind == "problems" || p.kind == "timed set" -> "problems"
        else -> "reading"
    }
    return if (k in kinds) k else kinds[0]
}

@Composable
fun SlotCard(state: AppState, slot: Slot, now: Long, active: Session?, isDone: Boolean, tick: Int, onNote: (String) -> Unit) {
    val ctx = LocalContext.current
    val isMath = slot.name == "Mathematics"
    val kinds = if (isMath) listOf("reading", "problems") else listOf("lecture", "reading")
    var kind by remember(slot.key) { mutableStateOf(defaultKind(slot, kinds)) }
    var showDone by remember(slot.key) { mutableStateOf(false) }
    var showAdd by remember(slot.key) { mutableStateOf(false) }
    val running = active != null && active.slotKey == slot.key
    val elapsedMs = state.db.elapsedMs(slot.key, now)
    val actualMin = (elapsedMs / 60000).toInt()

    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(slot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("planned ${fmtMin(slot.planned)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(slot.subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtSec(elapsedMs / 1000), style = MaterialTheme.typography.headlineMedium, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (isDone) {
                val short = slot.planned - actualMin
                Column(horizontalAlignment = Alignment.End) {
                    Tag("Done", DoneGreen)
                    Caption(if (short > 0) "shortfall ${fmtMin(short)}" else "plan met")
                }
            } else if (actualMin < slot.planned && elapsedMs > 0) {
                Caption("${fmtMin(slot.planned - actualMin)} to plan")
            }
        }
        if (!isDone) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in kinds) Pill(k, kind == k, { kind = k })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    Button(onClick = {
                        state.db.pauseActive(System.currentTimeMillis())
                        Notifier.cancelTimer(ctx)
                        state.bump()
                    }) { Text("Pause") }
                } else {
                    Button(onClick = {
                        val t = System.currentTimeMillis()
                        val before = state.db.elapsedMs(slot.key, t)
                        state.db.startSession(slot.key, kind, t)
                        Notifier.showTimer(ctx, slot.name + " · day " + slot.day, slot.subject, before)
                        state.bump()
                    }) { Text(if (elapsedMs > 0) "Resume" else "Start") }
                }
                OutlinedButton(onClick = { showDone = true }) { Text("Done") }
                TextButton(onClick = { showAdd = true }) { Text("± time") }
                TextButton(onClick = { onNote(slot.key) }) { Text("Note") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { state.db.reopen(slot.key); state.bump() }) { Text("Reopen") }
                TextButton(onClick = { showAdd = true }) { Text("± time") }
                TextButton(onClick = { onNote(slot.key) }) { Text("Note") }
            }
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        for (p in slot.pieces) PieceRow(state, p)
    }

    if (showDone) {
        var problems by remember { mutableStateOf("") }
        val short = slot.planned - actualMin
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = { Text("Finish this slot?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Planned ${fmtMin(slot.planned)}, logged ${fmtMin(actualMin)}.")
                    Text(if (short > 0) "Shortfall ${fmtMin(short)}. It is shown but not carried to another day." else "Plan met.")
                    if (isMath) OutlinedTextField(
                        value = problems, onValueChange = { problems = it.filter { c -> c.isDigit() } },
                        label = { Text("Problems solved (optional)") }, singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = System.currentTimeMillis()
                    if (running) { state.db.pauseActive(t); Notifier.cancelTimer(ctx) }
                    state.db.markDone(slot.key, t, problems.toIntOrNull() ?: 0)
                    showDone = false
                    state.bump()
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDone = false }) { Text("Cancel") } }
        )
    }
    if (showAdd) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Adjust logged time") },
            text = {
                OutlinedTextField(
                    value = txt, onValueChange = { txt = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("Minutes to add (negative to remove)") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = txt.toIntOrNull()
                    if (m != null && m != 0) {
                        state.db.addManual(slot.key, if (kind in kinds) kind else kinds[0], m * 60000L, System.currentTimeMillis())
                        state.bump()
                    }
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PieceRow(state: AppState, p: Piece) {
    val ctx = LocalContext.current
    val item = if (p.itemIdx >= 0) state.plan.items[p.itemIdx] else null
    var open by remember(p) { mutableStateOf(false) }
    val isPlan = p.resource == "Plan"
    Column(Modifier.fillMaxWidth().clickable { open = !open }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isPlan) Tag(tierName(p.tier), tierColor(p.tier))
            Caption("${Math.round(p.minutes)} min")
            if (p.approx && !isPlan) Caption("approximate pages")
        }
        Text(if (isPlan) p.title else p.resource + ": " + p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (item != null && item.kind == 1) {
            val raw = item.size
            Caption(state.lectureLabel(p.itemIdx) + " · " + fmtMin(raw) + " raw video · " + (p.watch ?: ""))
        } else if (p.pages != null) {
            Caption((if (p.approx) "about " else "") + p.pages)
        } else if (p.kind == "problems" || p.kind == "timed set") {
            Caption("time-boxed problem solving")
        }
        if (isPlan || open) {
            Text(p.objective, style = MaterialTheme.typography.bodySmall)
        }
        if (open && !isPlan) {
            Caption("Mode: " + p.mode)
            if (p.prereq.isNotBlank()) Caption("Prerequisites: " + p.prereq)
            if (p.syllabus.isNotBlank()) Caption("Syllabus: " + p.syllabus)
            if (p.pyq.isNotBlank()) Caption("PYQ focus: " + p.pyq)
            if (p.note.isNotBlank()) Caption("Note: " + p.note)
        }
        if (item != null && item.kind == 1) LinkRow(state, item.id, ctx)
    }
}

@Composable
private fun LinkRow(state: AppState, itemId: String, ctx: Context) {
    var link by remember(itemId) { mutableStateOf(state.db.link(itemId)) }
    var edit by remember(itemId) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { edit = true }) { Text(if (link.isBlank()) "Add link" else "Edit link") }
        if (link.isNotBlank()) Button(onClick = { openUrl(ctx, link) }) { Text("Open") }
    }
    if (edit) {
        var txt by remember { mutableStateOf(link) }
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("Lecture link") },
            text = { OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("URL (opens in YouTube or a browser)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { state.db.putLink(itemId, txt); link = txt.trim(); edit = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancel") } }
        )
    }
}
