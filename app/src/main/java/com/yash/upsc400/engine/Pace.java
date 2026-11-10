package com.yash.upsc400.engine;

import java.util.List;

/** Effective pace computed from logged sessions, used to recalibrate the plan's assumptions. */
public final class Pace {
    private Pace() {}

    public static final double PLAN_LECTURE_SPEED = 1.5;
    public static final double PLAN_LECTURE_OVERHEAD = 1.25;
    /** Effective minutes the plan spends per raw lecture minute (0.8333). */
    public static final double PLAN_LECTURE_COST = PLAN_LECTURE_OVERHEAD / PLAN_LECTURE_SPEED;

    /** Quantities completed in one finished slot and the time actually logged against them. */
    public static final class Sample {
        public double readingSec;
        public double lectureSec;
        public double problemSec;
        public double pages;
        public double lectureRawMin;
        public double problems;

        public Sample(double readingSec, double lectureSec, double problemSec, double pages, double lectureRawMin, double problems) {
            this.readingSec = readingSec;
            this.lectureSec = lectureSec;
            this.problemSec = problemSec;
            this.pages = pages;
            this.lectureRawMin = lectureRawMin;
            this.problems = problems;
        }
    }

    public static final class Result {
        public final double pagesPerHour;          // NaN when there is no data
        public final double problemsPerHour;       // NaN when there is no data
        public final double lectureRawMinPerHour;  // NaN when there is no data
        public final double lectureCost;           // observed effective minutes per raw minute, NaN when no data
        public final double lectureCostVsPlan;     // observed cost divided by the plan assumption

        Result(double p, double q, double l, double c) {
            pagesPerHour = p; problemsPerHour = q; lectureRawMinPerHour = l; lectureCost = c;
            lectureCostVsPlan = Double.isNaN(c) ? Double.NaN : c / PLAN_LECTURE_COST;
        }

        /** Planned effective minutes for a lecture of rawMin after recalibration (falls back to the plan formula). */
        public double recalibratedLectureMinutes(double rawMin) {
            double cost = Double.isNaN(lectureCost) ? PLAN_LECTURE_COST : lectureCost;
            return rawMin * cost;
        }
    }

    public static Result compute(List<Sample> samples) {
        double rs = 0, ls = 0, ps = 0, pages = 0, raw = 0, probs = 0;
        for (Sample s : samples) {
            rs += s.readingSec; ls += s.lectureSec; ps += s.problemSec;
            pages += s.pages; raw += s.lectureRawMin; probs += s.problems;
        }
        double pagesPerHour = rs > 0 && pages > 0 ? pages / (rs / 3600.0) : Double.NaN;
        double problemsPerHour = ps > 0 && probs > 0 ? probs / (ps / 3600.0) : Double.NaN;
        double lecPerHour = ls > 0 && raw > 0 ? raw / (ls / 3600.0) : Double.NaN;
        double cost = ls > 0 && raw > 0 ? (ls / 60.0) / raw : Double.NaN;
        return new Result(pagesPerHour, problemsPerHour, lecPerHour, cost);
    }

    /** Actual divided by planned minutes; above 1 means slower than planned. NaN when nothing is planned. */
    public static double actualToPlanned(double actualMin, double plannedMin) {
        return plannedMin > 0 ? actualMin / plannedMin : Double.NaN;
    }
}
