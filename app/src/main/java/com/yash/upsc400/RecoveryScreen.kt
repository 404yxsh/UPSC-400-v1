package com.yash.upsc400

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.upsc400.engine.Recovery
import java.time.format.DateTimeFormatter

private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun RecoveryScreen(state: AppState, onOpenDay: (Int) -> Unit) {
    val tick = state.tick
    val plan = state.plan
    val today = remember(tick) { state.today() }
    val done = remember(tick) { state.db.doneKeys() }
    val over = remember(tick) { Recovery.overdue(plan.engineSlots, done, today) }
    val ordered = remember(tick) { Recovery.order(over) }
    val hours = remember(tick) { Recovery.backlogHours(over, today, plan.dayInfos) }
    val level = Recovery.warningLevel(hours)
    val clear = remember(tick) { Recovery.projectedClearDay(hours, today, plan.dayInfos) }
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Recovery queue")
                Panel {
                    Text(String.format("Backlog: %.1f h in %d slots", hours, ordered.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (level == 2) Text("Strong warning: the backlog passed about 30 hours. Consider cutting extended items now and protecting core work.", color = AlertRed)
                    else if (level == 1) Text("Warning: the backlog passed about 12 hours.", color = WarnAmber)
                    else Caption("No warning. Overdue slots are cleared on Sundays and flex days, never dumped on the next day.")
                    if (ordered.isNotEmpty()) {
                        Caption(
                            if (clear < 0) "Projected: not cleared before the last plan day if you only recover on Sundays and flex days."
                            else "Projected clear date: " + state.dateOf(clear).format(FMT) + " (Day $clear), assuming nothing new goes overdue."
                        )
                    }
                    Caption("Order: Mathematics first, then core, standard, extended; prerequisites always before the slots that need them. Extended work still open after two Sundays is reduced to a skim; core is never dropped.")
                }
            }
        }
        items(ordered.withIndex().toList()) { iv ->
            val s = iv.value
            val slot = plan.slotByKey(s.key)
            val skim = Recovery.isSkimmed(s, today, plan.dayInfos)
            val eff = Recovery.effectiveMinutes(s, today, plan.dayInfos)
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${iv.index + 1}. ${slot?.name ?: s.key}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(fmtMin(eff), style = MaterialTheme.typography.labelLarge)
                }
                Caption("Day ${s.day} · ${state.dateOf(s.day).format(FMT)} · ${slot?.subject ?: ""}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(tierName(s.tierRank()), tierColor(s.tierRank()))
                    if (skim) Tag("skim", WarnAmber)
                }
                OutlinedButton(onClick = { onOpenDay(s.day) }) { Text("Open day") }
            }
        }
    }
}
