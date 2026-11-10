package com.yash.upsc400

import android.content.Context
import com.yash.upsc400.engine.DayInfo
import com.yash.upsc400.engine.PlanSlot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class Piece(
    val itemIdx: Int, val kind: String, val minutes: Float, val pages: String?, val watch: String?,
    val mode: String, val objective: String, val syllabus: String, val pyq: String, val note: String,
    val prereq: String, val approx: Boolean, val f0: Int, val f1: Int, val tier: Int,
    val resource: String, val title: String
)

class Slot(val day: Int, val name: String, val planned: Int, val subject: String, val pieces: List<Piece>) {
    val key: String get() = "$day|$name"
}

class DayPlan(val day: Int, val type: Int, val block: String, val slots: List<Slot>)

class Item(
    val id: String, val resource: String, val title: String, val tier: Int, val kind: Int,
    val size: Int, val heading: Int, val unit: String, val approx: Boolean
)

val BLOCK_NAMES = mapOf(
    "POL" to "Polity and Constitution", "GOV" to "Governance and Social Justice", "GEO" to "Geography",
    "ENV" to "Environment and Ecology", "ECO" to "Economy and Agriculture", "ST" to "Science and Technology",
    "ANC" to "Ancient India", "MED" to "Medieval India", "ART" to "Art and Culture",
    "MOD" to "Modern India and Post-independence", "WORLD" to "World History", "SOC" to "Indian Society",
    "IR" to "International Relations", "IS" to "Internal Security", "DM" to "Disaster Management",
    "ETH" to "Ethics, Integrity and Aptitude"
)

private val PRE_U = mapOf(
    "AG" to listOf("LA"), "VEC" to listOf("CAL"), "ODE" to listOf("CAL"), "DS" to listOf("VEC", "ODE"),
    "ALG" to listOf("LA"), "RA" to listOf("CAL"), "CX" to listOf("RA"), "LPP" to listOf("LA"),
    "PDE" to listOf("ODE", "CAL", "VEC"), "NUM" to listOf("LA", "CAL"), "MECH" to listOf("DS", "VEC"),
    "FD" to listOf("CX", "VEC", "MECH")
)
private val PRE_B = mapOf(
    "GOV" to listOf("POL"), "ENV" to listOf("GEO"), "ECO" to listOf("POL"), "MED" to listOf("ANC"),
    "ART" to listOf("ANC", "MED"), "MOD" to listOf("MED"), "WORLD" to listOf("MOD"), "SOC" to listOf("MOD"),
    "IR" to listOf("WORLD", "POL"), "IS" to listOf("POL", "SOC"), "DM" to listOf("GEO", "ENV"),
    "ETH" to listOf("POL", "GOV", "SOC")
)

class Plan(val startIso: String, val headings: List<String>, val items: List<Item>, val days: List<DayPlan>) {
    val dayInfos: List<DayInfo> = days.map {
        DayInfo(it.day, when (it.type) { 2 -> DayInfo.Type.SUNDAY; 1 -> DayInfo.Type.FLEX; else -> DayInfo.Type.SPINE })
    }

    /** Slots in the form the pure scheduling logic needs. */
    val engineSlots: List<PlanSlot> by lazy {
        val out = ArrayList<PlanSlot>()
        for (d in days) for (s in d.slots) {
            val kind: PlanSlot.Kind
            var stream = d.block
            var pre: List<String> = emptyList()
            if (s.name == "Mathematics") {
                kind = PlanSlot.Kind.MATH
                val first = s.pieces.firstOrNull { it.itemIdx >= 0 }
                stream = if (first != null) items[first.itemIdx].unit else "MATH"
                pre = PRE_U[stream] ?: emptyList()
            } else if (s.name.startsWith("GS slot")) {
                kind = PlanSlot.Kind.GS
                pre = PRE_B[d.block] ?: emptyList()
            } else if (s.name == "Revision and PYQ") {
                kind = PlanSlot.Kind.REVISION
            } else {
                kind = PlanSlot.Kind.OTHER
            }
            val mins = IntArray(s.pieces.size) { Math.round(s.pieces[it].minutes) }
            val tiers = IntArray(s.pieces.size) { s.pieces[it].tier }
            out.add(PlanSlot(s.key, d.day, kind, stream, pre.toTypedArray(), s.planned, mins, tiers))
        }
        out
    }

    fun slotByKey(key: String): Slot? {
        val day = key.substringBefore('|').toIntOrNull() ?: return null
        return days.getOrNull(day - 1)?.slots?.firstOrNull { it.key == key }
    }

    companion object {
        fun load(ctx: Context): Plan {
            val text = ctx.assets.open("plan.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val sArr = root.getJSONArray("S")
            val strs = Array(sArr.length()) { sArr.getString(it) }
            fun s(i: Int): String = if (i < 0) "" else strs[i]
            val hArr = root.getJSONArray("H")
            val headings = List(hArr.length()) { hArr.getString(it) }
            val iArr = root.getJSONArray("I")
            val items = List(iArr.length()) {
                val a = iArr.getJSONArray(it)
                Item(s(a.getInt(0)), s(a.getInt(1)), s(a.getInt(2)), a.getInt(3), a.getInt(4), a.getInt(5), a.getInt(6), s(a.getInt(7)), a.getInt(8) == 1)
            }
            val dArr = root.getJSONArray("D")
            val days = ArrayList<DayPlan>(dArr.length())
            for (di in 0 until dArr.length()) {
                val d = dArr.getJSONArray(di)
                val dayNo = d.getInt(0)
                val slotsJ: JSONArray = d.getJSONArray(3)
                val slots = ArrayList<Slot>(slotsJ.length())
                for (si in 0 until slotsJ.length()) {
                    val sl = slotsJ.getJSONArray(si)
                    val pj = sl.getJSONArray(3)
                    val pieces = ArrayList<Piece>(pj.length())
                    for (pi in 0 until pj.length()) {
                        val p = pj.getJSONArray(pi)
                        pieces.add(
                            Piece(
                                p.getInt(0), s(p.getInt(1)), p.getDouble(2).toFloat(),
                                if (p.getInt(3) < 0) null else s(p.getInt(3)),
                                if (p.getInt(4) < 0) null else s(p.getInt(4)),
                                s(p.getInt(5)), s(p.getInt(6)), s(p.getInt(7)), s(p.getInt(8)), s(p.getInt(9)), s(p.getInt(10)),
                                p.getInt(11) == 1, p.getInt(12), p.getInt(13), p.getInt(14), s(p.getInt(15)), s(p.getInt(16))
                            )
                        )
                    }
                    slots.add(Slot(dayNo, s(sl.getInt(0)), sl.getInt(1), s(sl.getInt(2)), pieces))
                }
                days.add(DayPlan(dayNo, d.getInt(1), s(d.getInt(2)), slots))
            }
            return Plan(root.getString("start"), headings, items, days)
        }
    }
}

fun fmtMin(m: Int): String {
    val h = m / 60
    val r = m % 60
    return if (h == 0) "${r} min" else if (r == 0) "${h} h" else "${h} h ${r} min"
}

fun fmtSec(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun typeLabel(t: Int): String = when (t) { 2 -> "Sunday"; 1 -> "Flex"; else -> "Spine" }

fun dayDate(start: LocalDate, day: Int): LocalDate = start.plusDays((day - 1).toLong())
