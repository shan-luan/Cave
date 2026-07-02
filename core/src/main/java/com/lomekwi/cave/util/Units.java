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

    /**
     * Nice Scale algorithm: rounds a raw interval value to the nearest "nice" number
     * (1, 2, or 5 × 10^n), suitable for axis tick spacing.
     */
    public static long niceScale(long raw) {
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double r = raw / mag;
        if (r < 2) return (long) mag;
        if (r < 5) return (long) (2 * mag);
        return (long) (5 * mag);
    }

    /**
     * Float version of niceScale: rounds a raw interval to the nearest "nice" number
     * (1, 2, 5, 10 × 10^n).
     */
    public static float niceInterval(float raw) {
        float mag = (float) Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-10f))));
        float r = raw / mag;
        if (r < 1.5f) return mag;
        if (r < 3.5f) return 2f * mag;
        if (r < 7.5f) return 5f * mag;
        return 10f * mag;
    }

}
