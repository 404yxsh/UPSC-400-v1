package com.yash.upsc400

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var stateRef: AppState? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val st = stateRef
        if (uri != null && st != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(st.db.exportJson().toByteArray(Charsets.UTF_8)) }
                toast("Backup saved")
            } catch (e: Exception) {
                toast("Export failed: " + e.message)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val st = stateRef
        if (uri != null && st != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                st.db.importJson(text)
                st.bump()
                toast("Backup imported")
            } catch (e: Exception) {
                toast("Import failed: " + e.message)
            }
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val loaded by produceState<AppState?>(initialValue = null) {
            value = withContext(Dispatchers.IO) { AppState(applicationContext, Plan.load(applicationContext), Db(applicationContext)) }
        }
        val st = loaded
        val tick = st?.tick ?: 0
        val themeSetting = if (st != null) st.db.setting("theme", "dark") else "dark"
        val dark = when (themeSetting) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
        AppTheme(dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                if (st == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading plan…") }
                } else {
                    LaunchedEffect(st) {
                        stateRef = st
                        val a = st.db.activeSession()
                        if (a != null) {
                            val slot = st.plan.slotByKey(a.slotKey)
                            Notifier.showTimer(applicationContext, (slot?.name ?: "Study") + " · day " + a.slotKey.substringBefore('|'), slot?.subject ?: "", st.db.elapsedMs(a.slotKey, System.currentTimeMillis()))
                        } else Notifier.cancelTimer(applicationContext)
                        if (st.db.setting("reminder_on", "0") == "1") {
                            val t = st.db.setting("reminder_time", "20:00")
                            Reminder.schedule(applicationContext, t.substringBefore(':').toIntOrNull() ?: 20, t.substringAfter(':').toIntOrNull() ?: 0)
                        }
                    }
                    if (tick >= 0) AppRoot(st, { exportLauncher.launch("upsc400-backup.json") }, { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
            }
        }
    }
}

private val TABS = listOf("Today" to "●", "Plan" to "▤", "Progress" to "◔", "Recovery" to "↻", "Stats" to "∿")

@Composable
fun AppRoot(state: AppState, onExport: () -> Unit, onImport: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Today") }
    var overlay by rememberSaveable { mutableStateOf("") }
    var noteSlot by rememberSaveable { mutableStateOf("") }
    var detailDay by rememberSaveable { mutableStateOf(0) }
    var viewDay by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = overlay.isNotEmpty() || detailDay > 0) {
        if (overlay.isNotEmpty()) overlay = "" else detailDay = 0
    }
    val openNote: (String) -> Unit = { key -> noteSlot = key; overlay = "notes" }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                for ((name, glyph) in TABS) {
                    NavigationBarItem(
                        selected = tab == name && overlay.isEmpty() && detailDay == 0,
                        onClick = { tab = name; overlay = ""; detailDay = 0 },
                        icon = { Text(glyph) },
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (overlay.isNotEmpty() || detailDay > 0) {
                    TextButton(onClick = { if (overlay.isNotEmpty()) overlay = "" else detailDay = 0 }) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { noteSlot = ""; overlay = "notes" }) { Text("Notes") }
                TextButton(onClick = { overlay = "settings" }) { Text("Settings") }
            }
            Box(Modifier.weight(1f)) {
                if (overlay == "notes") NotesScreen(state, noteSlot)
                else if (overlay == "settings") SettingsScreen(state, onExport, onImport)
                else if (detailDay > 0) DayScreen(state, detailDay, { detailDay = it }, openNote)
                else when (tab) {
                    "Plan" -> PlanScreen(state) { detailDay = it }
                    "Progress" -> ProgressScreen(state)
                    "Recovery" -> RecoveryScreen(state) { detailDay = it }
                    "Stats" -> StatsScreen(state)
                    else -> {
                        val todayN = state.clampDay(state.today())
                        val shown = if (viewDay > 0) viewDay else todayN
                        DayScreen(state, shown, { viewDay = if (it == todayN) 0 else it }, openNote)
                    }
                }
            }
        }
    }
}
