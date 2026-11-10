package com.yash.upsc400.engine;

public final class DayInfo {
    public enum Type { SPINE, FLEX, SUNDAY }

    public final int day;
    public final Type type;

    public DayInfo(int day, Type type) {
        this.day = day;
        this.type = type;
    }
}
