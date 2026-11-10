package com.yash.upsc400.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Day-rollover logic: late-night study counts towards the previous plan day. */
public final class Rollover {
    private Rollover() {}

    /** The study date an instant belongs to when the day rolls over at rolloverMinutes after local midnight. */
    public static LocalDate studyDate(Instant instant, ZoneId zone, int rolloverMinutes) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
        return local.minus(Duration.ofMinutes(rolloverMinutes)).toLocalDate();
    }

    /** Plan day number (1 = start date). Values below 1 or above planLength mean outside the plan. */
    public static int dayNumber(LocalDate start, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(start, date) + 1;
    }

    public static int dayNumberAt(Instant instant, ZoneId zone, int rolloverMinutes, LocalDate start) {
        return dayNumber(start, studyDate(instant, zone, rolloverMinutes));
    }
}
