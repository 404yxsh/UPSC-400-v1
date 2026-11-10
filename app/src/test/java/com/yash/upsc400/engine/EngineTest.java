package com.yash.upsc400.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class EngineTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate START = LocalDate.of(2026, 12, 5);

    private static Instant at(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(IST).toInstant();
    }

    private static PlanSlot slot(String key, int day, PlanSlot.Kind kind, String stream, String[] pre, int[] mins, int[] tiers) {
        int sum = 0;
        for (int m : mins) sum += m;
        return new PlanSlot(key, day, kind, stream, pre, sum, mins, tiers);
    }

    private static List<DayInfo> days(int n, int... sundays) {
        List<DayInfo> out = new ArrayList<>();
        Set<Integer> sun = new HashSet<>();
        for (int s : sundays) sun.add(s);
        for (int i = 1; i <= n; i++) out.add(new DayInfo(i, sun.contains(i) ? DayInfo.Type.SUNDAY : DayInfo.Type.SPINE));
        return out;
    }

    @Test
    public void rolloverCountsLateNightToPreviousDay() {
        assertEquals(1, Rollover.dayNumberAt(at(2026, 12, 6, 3, 30), IST, 240, START));
        assertEquals(2, Rollover.dayNumberAt(at(2026, 12, 6, 4, 0), IST, 240, START));
        assertEquals(1, Rollover.dayNumberAt(at(2026, 12, 5, 12, 0), IST, 240, START));
    }

    @Test
    public void rolloverZeroUsesCalendarDate() {
        assertEquals(2, Rollover.dayNumberAt(at(2026, 12, 6, 0, 10), IST, 0, START));
        assertEquals(400, Rollover.dayNumber(START, LocalDate.of(2028, 1, 8)));
        assertEquals(0, Rollover.dayNumber(START, LocalDate.of(2026, 12, 4)));
    }

    @Test
    public void overdueExcludesDoneFutureRevisionAndOther() {
        List<PlanSlot> all = new ArrayList<>();
        all.add(slot("a", 1, PlanSlot.Kind.GS, "POL", null, new int[]{60}, new int[]{0}));
        all.add(slot("b", 1, PlanSlot.Kind.MATH, "LA", null, new int[]{60}, new int[]{0}));
        all.add(slot("c", 2, PlanSlot.Kind.REVISION, "REV", null, new int[]{45}, new int[]{0}));
        all.add(slot("d", 2, PlanSlot.Kind.OTHER, "FLEX", null, new int[]{255}, new int[]{0}));
        all.add(slot("e", 5, PlanSlot.Kind.GS, "POL", null, new int[]{60}, new int[]{0}));
        all.add(slot("f", 3, PlanSlot.Kind.GS, "POL", null, new int[]{60}, new int[]{0}));
        Set<String> done = new HashSet<>(Arrays.asList("b"));
        List<PlanSlot> o = Recovery.overdue(all, done, 5);
        assertEquals(2, o.size());
        assertEquals("a", o.get(0).key);
        assertEquals("f", o.get(1).key);
    }

    @Test
    public void mathematicsComesFirstThenTier() {
        PlanSlot gsCore = slot("gsCore", 1, PlanSlot.Kind.GS, "POL", null, new int[]{60}, new int[]{0});
        PlanSlot mathExt = slot("mathExt", 5, PlanSlot.Kind.MATH, "LA", null, new int[]{60}, new int[]{2});
        PlanSlot gsStd = slot("gsStd", 2, PlanSlot.Kind.GS, "GEO", null, new int[]{60}, new int[]{1});
        PlanSlot gsExt = slot("gsExt", 3, PlanSlot.Kind.GS, "ENV", null, new int[]{60}, new int[]{2});
        List<PlanSlot> ordered = Recovery.order(Arrays.asList(gsExt, gsStd, mathExt, gsCore));
        assertEquals("mathExt", ordered.get(0).key);
        assertEquals("gsCore", ordered.get(1).key);
        assertEquals("gsStd", ordered.get(2).key);
        assertEquals("gsExt", ordered.get(3).key);
    }

    @Test
    public void prerequisitesAreClearedFirst() {
        PlanSlot a = slot("anc", 3, PlanSlot.Kind.GS, "ANC", null, new int[]{60}, new int[]{2});
        PlanSlot b = slot("med", 4, PlanSlot.Kind.GS, "MED", new String[]{"ANC"}, new int[]{60}, new int[]{0});
        List<PlanSlot> ordered = Recovery.order(Arrays.asList(b, a));
        assertEquals("anc", ordered.get(0).key);
        assertEquals("med", ordered.get(1).key);
    }

    @Test
    public void sameStreamKeepsDayOrder() {
        PlanSlot x = slot("x", 2, PlanSlot.Kind.MATH, "CAL", null, new int[]{60}, new int[]{2});
        PlanSlot y = slot("y", 3, PlanSlot.Kind.MATH, "CAL", null, new int[]{60}, new int[]{0});
        List<PlanSlot> ordered = Recovery.order(Arrays.asList(y, x));
        assertEquals("x", ordered.get(0).key);
        assertEquals("y", ordered.get(1).key);
    }

    @Test
    public void extendedWorkIsSkimmedAfterTwoSundaysButCoreNever() {
        List<DayInfo> d = days(20, 2, 9, 16);
        PlanSlot ext = slot("ext", 1, PlanSlot.Kind.GS, "POL", null, new int[]{100}, new int[]{2});
        PlanSlot core = slot("core", 1, PlanSlot.Kind.GS, "POL", null, new int[]{100}, new int[]{0});
        PlanSlot mixed = slot("mixed", 1, PlanSlot.Kind.GS, "POL", null, new int[]{60, 40}, new int[]{0, 2});
        assertEquals(1, Recovery.sundaysElapsed(1, 9, d));
        assertEquals(100, Recovery.effectiveMinutes(ext, 9, d));
        assertEquals(2, Recovery.sundaysElapsed(1, 10, d));
        assertEquals(50, Recovery.effectiveMinutes(ext, 10, d));
        assertEquals(100, Recovery.effectiveMinutes(core, 20, d));
        assertEquals(80, Recovery.effectiveMinutes(mixed, 20, d));
        assertFalse(Recovery.isSkimmed(core, 20, d));
        assertTrue(Recovery.isSkimmed(ext, 10, d));
    }

    @Test
    public void warningLevels() {
        assertEquals(0, Recovery.warningLevel(11.99));
        assertEquals(1, Recovery.warningLevel(12.0));
        assertEquals(1, Recovery.warningLevel(29.99));
        assertEquals(2, Recovery.warningLevel(30.0));
    }

    @Test
    public void capacities() {
        assertEquals(165, Recovery.capacityMinutes(DayInfo.Type.SUNDAY, 3));
        assertEquals(345, Recovery.capacityMinutes(DayInfo.Type.SUNDAY, 12));
        assertEquals(255, Recovery.capacityMinutes(DayInfo.Type.FLEX, 40));
        assertEquals(0, Recovery.capacityMinutes(DayInfo.Type.SPINE, 40));
    }

    @Test
    public void projectedClearDay() {
        List<DayInfo> d = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            DayInfo.Type t = (i == 7 || i == 14) ? DayInfo.Type.SUNDAY : (i == 10 ? DayInfo.Type.FLEX : DayInfo.Type.SPINE);
            d.add(new DayInfo(i, t));
        }
        assertEquals(8, Recovery.projectedClearDay(0, 8, d));
        assertEquals(14, Recovery.projectedClearDay(5.0, 8, d));
        assertEquals(10, Recovery.projectedClearDay(4.0, 8, d));
        assertEquals(-1, Recovery.projectedClearDay(13.0, 1, d));
    }

    @Test
    public void backlogHoursAddsEffectiveMinutes() {
        List<DayInfo> d = days(10, 6);
        List<PlanSlot> o = new ArrayList<>();
        o.add(slot("a", 1, PlanSlot.Kind.GS, "POL", null, new int[]{120}, new int[]{0}));
        o.add(slot("b", 2, PlanSlot.Kind.MATH, "LA", null, new int[]{180}, new int[]{1}));
        assertEquals(5.0, Recovery.backlogHours(o, 4, d), 1e-9);
    }

    @Test
    public void paceRecalibration() {
        List<Pace.Sample> s = new ArrayList<>();
        s.add(new Pace.Sample(3600, 7200, 7200, 20, 120, 30));
        Pace.Result r = Pace.compute(s);
        assertEquals(20.0, r.pagesPerHour, 1e-9);
        assertEquals(60.0, r.lectureRawMinPerHour, 1e-9);
        assertEquals(15.0, r.problemsPerHour, 1e-9);
        assertEquals(1.0, r.lectureCost, 1e-9);
        assertEquals(1.2, r.lectureCostVsPlan, 1e-9);
        assertEquals(60.0, r.recalibratedLectureMinutes(60), 1e-9);
        assertEquals(0.8333333333, Pace.PLAN_LECTURE_COST, 1e-9);
        Pace.Result empty = Pace.compute(new ArrayList<Pace.Sample>());
        assertTrue(Double.isNaN(empty.pagesPerHour));
        assertEquals(50.0, empty.recalibratedLectureMinutes(60), 1e-6);
        assertEquals(1.5, Pace.actualToPlanned(90, 60), 1e-9);
    }
}
