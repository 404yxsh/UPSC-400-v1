package com.yash.upsc400.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Recovery queue logic. A slot not done by the rollover becomes overdue. Rules:
 * Mathematics first, then core, standard, extended; a hard prerequisite is always cleared before the slot that needs it.
 * Extended work left over two Sundays is reduced to a skim; core work is never dropped.
 */
public final class Recovery {
    private Recovery() {}

    public static final int SKIM_AFTER_SUNDAYS = 2;
    public static final double WARN_HOURS = 12.0;
    public static final double STRONG_HOURS = 30.0;
    /** Sunday recovery slot (105 min) plus 60 min that fit under the 9 h hard maximum. */
    public static final int SUNDAY_BASE_CAPACITY_MIN = 165;
    /** With a large backlog the weekly review and context capsule (180 min) are converted too. */
    public static final int SUNDAY_HEAVY_CAPACITY_MIN = 345;
    public static final int FLEX_CAPACITY_MIN = 255;

    /** Slots strictly before today that are not done. Revision slots lapse and flex/Sunday slots are the recovery vehicles, so neither counts. */
    public static List<PlanSlot> overdue(List<PlanSlot> all, Set<String> done, int today) {
        List<PlanSlot> out = new ArrayList<>();
        for (PlanSlot s : all) {
            if (s.day >= today) continue;
            if (s.kind == PlanSlot.Kind.REVISION || s.kind == PlanSlot.Kind.OTHER) continue;
            if (done.contains(s.key)) continue;
            out.add(s);
        }
        return out;
    }

    /** Number of Sundays that fell strictly after the slot's day and strictly before today. */
    public static int sundaysElapsed(int slotDay, int today, List<DayInfo> days) {
        int n = 0;
        for (DayInfo d : days) {
            if (d.type == DayInfo.Type.SUNDAY && d.day > slotDay && d.day < today) n++;
        }
        return n;
    }

    public static boolean isSkimmed(PlanSlot s, int today, List<DayInfo> days) {
        return s.hasExtendedPieces() && sundaysElapsed(s.day, today, days) >= SKIM_AFTER_SUNDAYS;
    }

    public static int effectiveMinutes(PlanSlot s, int today, List<DayInfo> days) {
        return s.effectiveMinutes(isSkimmed(s, today, days));
    }

    private static final Comparator<PlanSlot> PRIORITY = new Comparator<PlanSlot>() {
        @Override public int compare(PlanSlot a, PlanSlot b) {
            int am = a.kind == PlanSlot.Kind.MATH ? 0 : 1;
            int bm = b.kind == PlanSlot.Kind.MATH ? 0 : 1;
            if (am != bm) return am - bm;
            if (a.tierRank() != b.tierRank()) return a.tierRank() - b.tierRank();
            if (a.day != b.day) return a.day - b.day;
            return a.key.compareTo(b.key);
        }
    };

    private static boolean dependsOn(PlanSlot later, PlanSlot earlier) {
        if (later == earlier) return false;
        if (later.stream.equals(earlier.stream) && earlier.day < later.day) return true;
        for (String p : later.prereqStreams) if (p.equals(earlier.stream)) return true;
        return false;
    }

    /** Priority order that never schedules a slot before an overdue slot it depends on. */
    public static List<PlanSlot> order(List<PlanSlot> overdue) {
        List<PlanSlot> remaining = new ArrayList<>(overdue);
        Collections.sort(remaining, PRIORITY);
        List<PlanSlot> out = new ArrayList<>();
        while (!remaining.isEmpty()) {
            PlanSlot pick = null;
            for (PlanSlot cand : remaining) {
                boolean blocked = false;
                for (PlanSlot other : remaining) {
                    if (dependsOn(cand, other)) { blocked = true; break; }
                }
                if (!blocked) { pick = cand; break; }
            }
            if (pick == null) pick = remaining.get(0); // defensive: cannot happen with day-ordered streams
            remaining.remove(pick);
            out.add(pick);
        }
        return out;
    }

    public static double backlogHours(List<PlanSlot> overdue, int today, List<DayInfo> days) {
        long min = 0;
        for (PlanSlot s : overdue) min += effectiveMinutes(s, today, days);
        return min / 60.0;
    }

    /** 0 none, 1 warning at about 12 h, 2 strong warning at about 30 h. */
    public static int warningLevel(double hours) {
        if (hours >= STRONG_HOURS) return 2;
        if (hours >= WARN_HOURS) return 1;
        return 0;
    }

    public static int capacityMinutes(DayInfo.Type type, double backlogHours) {
        switch (type) {
            case SUNDAY: return backlogHours >= WARN_HOURS ? SUNDAY_HEAVY_CAPACITY_MIN : SUNDAY_BASE_CAPACITY_MIN;
            case FLEX: return FLEX_CAPACITY_MIN;
            default: return 0;
        }
    }

    /**
     * First plan day on which the backlog would be fully cleared if every future Sunday and flex day is used for recovery
     * and nothing new goes overdue. Returns -1 if not cleared by the last plan day.
     */
    public static int projectedClearDay(double backlogHours, int today, List<DayInfo> days) {
        double remainingMin = backlogHours * 60.0;
        if (remainingMin <= 0) return today;
        for (DayInfo d : days) {
            if (d.day < today) continue;
            int cap = capacityMinutes(d.type, remainingMin / 60.0);
            if (cap <= 0) continue;
            remainingMin -= cap;
            if (remainingMin <= 0) return d.day;
        }
        return -1;
    }
}
