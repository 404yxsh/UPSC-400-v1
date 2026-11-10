package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

private val ROW_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun PlanScreen(state: AppState, onOpenDay: (Int) -> Unit) {
    val tick = state.tick
    val done = remember(tick) { state.db.doneKeys() }
    val today = remember(tick) { state.today() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val idx = state.clampDay(today) - 1
        listState.scrollToItem(if (idx > 2) idx - 1 else 0)
    }
    LazyColumn(state = listState, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Title("Plan")
                Caption("400 days: 318 spine, 25 flex, 57 Sundays. Tap a day to open its slots.")
            }
        }
        items(state.plan.days) { d ->
            val total = d.slots.size
            val n = d.slots.count { it.key in done }
            val overdue = d.day < today && d.slots.any {
                it.name == "Mathematics" || it.name.startsWith("GS slot")
            } && d.slots.any { (it.name == "Mathematics" || it.name.startsWith("GS slot")) && it.key !in done }
            val isToday = d.day == today
            val shape = RoundedCornerShape(12.dp)
            val bg = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
            val subject = when (d.type) {
                2 -> "Weekly consolidation and recovery"
                1 -> "Flex: " + (BLOCK_NAMES[d.block] ?: "recovery and consolidation")
                else -> BLOCK_NAMES[d.block] ?: ""
            }
            Surface(color = bg, shape = shape, modifier = Modifier.fillMaxWidth().clip(shape).clickable { onOpenDay(d.day) }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Day ${d.day} · ${state.dateOf(d.day).format(ROW_FMT)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Caption(subject)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (overdue) Tag("overdue", WarnAmber)
                        Tag(typeLabel(d.type), MaterialTheme.colorScheme.onSurfaceVariant)
                        Caption("$n/$total")
                    }
                }
            }
        }
    }
}
