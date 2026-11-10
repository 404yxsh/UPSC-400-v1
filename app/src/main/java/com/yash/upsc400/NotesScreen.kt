package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NotesScreen(state: AppState, startSlot: String) {
    val tick = state.tick
    val notes = remember(tick) { state.db.notes() }
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember(startSlot) { mutableStateOf(startSlot.isNotEmpty()) }
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Short notes")
                Caption("Quick digital notes. Your main notes stay in the physical notebook.")
                Button(onClick = { creating = true }) { Text("New note") }
            }
        }
        items(notes) { n ->
            Panel(Modifier.clickable { editing = n }) {
                Text(if (n.title.isBlank()) "Untitled" else n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (n.slotKey.isNotBlank()) {
                    val slot = state.plan.slotByKey(n.slotKey)
                    Caption("Day ${n.slotKey.substringBefore('|')} · ${slot?.name ?: n.slotKey}")
                }
                Text(n.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
            }
        }
    }
    if (creating || editing != null) {
        val existing = editing
        var title by remember(existing, creating) { mutableStateOf(existing?.title ?: "") }
        var body by remember(existing, creating) { mutableStateOf(existing?.body ?: "") }
        val slotKey = existing?.slotKey ?: startSlot
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (existing == null) "New note" else "Edit note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Note") }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank() || body.isNotBlank()) state.db.saveNote(existing?.id ?: 0L, slotKey, title.trim(), body.trim(), System.currentTimeMillis())
                    creating = false; editing = null; state.bump()
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (existing != null) TextButton(onClick = { state.db.deleteNote(existing.id); editing = null; state.bump() }) { Text("Delete") }
                    TextButton(onClick = { creating = false; editing = null }) { Text("Cancel") }
                }
            }
        )
    }
}
