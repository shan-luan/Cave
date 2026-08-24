package com.lomekwi.cave.util;

import com.google.common.collect.Range;

public final class Ranges {
    private Ranges(){}
    public static Range<Long> shift(Range<Long> r, long delta) {
        return Range.closedOpen(r.lowerEndpoint() + delta, r.upperEndpoint() + delta);
    }
}
