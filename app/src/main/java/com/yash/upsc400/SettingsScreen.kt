package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState, onExport: () -> Unit, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val tick = state.tick
    var startTxt by remember(tick) { mutableStateOf(state.startDate().toString()) }
    var startMsg by remember { mutableStateOf("") }
    val roll = state.rolloverMin()
    var rh by remember(tick) { mutableStateOf((roll / 60).toString()) }
    var rm by remember(tick) { mutableStateOf((roll % 60).toString()) }
    var rollMsg by remember { mutableStateOf("") }
    val theme = state.db.setting("theme", "dark")
    val remindOn = state.db.setting("reminder_on", "0") == "1"
    val rt = state.db.setting("reminder_time", "20:00")
    var remH by remember(tick) { mutableStateOf(rt.substringBefore(':')) }
    var remM by remember(tick) { mutableStateOf(rt.substringAfter(':')) }
    var confirmImport by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("Settings") }
        item {
            Panel {
                Text("Start date (Day 1)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("The plan keeps its day numbers; changing this moves every date. Default 2026-12-05.")
                OutlinedTextField(value = startTxt, onValueChange = { startTxt = it }, label = { Text("YYYY-MM-DD") }, singleLine = true)
                Button(onClick = {
                    try {
                        val d = LocalDate.parse(startTxt.trim())
                        state.db.putSetting("start", d.toString()); startMsg = "Saved"; state.bump()
                    } catch (e: Exception) { startMsg = "Use the format YYYY-MM-DD" }
                }) { Text("Save start date") }
                if (startMsg.isNotEmpty()) Caption(startMsg)
            }
        }
        item {
            Panel {
                Text("Day rollover", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("Study before this time counts towards the previous day. Default 04:00.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rh, onValueChange = { rh = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Hour") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rm, onValueChange = { rm = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Minute") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(onClick = {
                    val h = rh.toIntOrNull(); val m = rm.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) {
                        state.db.putSetting("rollover", (h * 60 + m).toString()); rollMsg = "Saved"; state.bump()
                    } else rollMsg = "Hour 0-23 and minute 0-59"
                }) { Text("Save rollover") }
                if (rollMsg.isNotEmpty()) Caption(rollMsg)
            }
        }
        item {
            Panel {
                Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, label) in listOf("dark" to "Dark", "light" to "Light", "system" to "System")) {
                        Pill(label, theme == k, { state.db.putSetting("theme", k); state.bump() })
                    }
                }
            }
        }
        item {
            Panel {
                Text("Daily reminder (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(checked = remindOn, onCheckedChange = { on ->
                        state.db.putSetting("reminder_on", if (on) "1" else "0")
                        val h = remH.toIntOrNull() ?: 20; val m = remM.toIntOrNull() ?: 0
                        if (on) Reminder.schedule(ctx, h, m) else Reminder.cancel(ctx)
                        state.bump()
                    })
                    Text(if (remindOn) "On" else "Off")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = remH, onValueChange = { remH = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Hour") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = remM, onValueChange = { remM = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Minute") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(onClick = {
                    val h = remH.toIntOrNull(); val m = remM.toIntOrNull()
                    if (h != null && m != null && h in 0..23 && m in 0..59) {
                        state.db.putSetting("reminder_time", "%02d:%02d".format(h, m))
                        if (remindOn) Reminder.schedule(ctx, h, m)
                        state.bump()
                    }
                }) { Text("Save reminder time") }
                Caption("The reminder is a local notification. It needs notification permission on Android 13 and above.")
            }
        }
        item {
            Panel {
                Text("Backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("Exports your progress, timer logs, lecture links and notes as one JSON file. Importing replaces what is on this phone.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("Export") }
                    OutlinedButton(onClick = { confirmImport = true }) { Text("Import") }
                }
            }
        }
        item {
            Panel {
                Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Caption("Fully offline. The app declares no internet permission. Lecture links open in YouTube or your browser through the system.")
                Caption("Lecture cost assumption: duration / 1.5 x 1.25. The Stats screen shows your observed pace and the recalibrated cost.")
                Caption("Plan: 400 days, Day 1 = Saturday 5 December 2026, built in Phase 2 of this project.")
            }
        }
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import backup?") },
            text = { Text("This replaces all progress, timer logs, links and notes on this phone with the file's contents.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; onImport() }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } }
        )
    }
}
