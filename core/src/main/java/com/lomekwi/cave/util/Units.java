package com.lomekwi.cave.util;

public final class Units {
    private Units(){}

    public static final long KILO = 1_000L;
    public static final long MEGA = KILO * KILO;
    public static final long GIGA = KILO * MEGA;
    public static final long TERA = KILO * GIGA;
    public static final long PETA = KILO * TERA;

    public static final long MICROSECOND = 1;//timebase
    public static final long MILLISECOND = KILO*MICROSECOND;
    public static final long SECOND = KILO*MILLISECOND;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;

}
