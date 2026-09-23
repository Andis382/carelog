package io.github.andis382.carelog.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Min, max and a one-decimal average of readings. Empty input gives null, not zero. */
public final class Numbers {

    private Numbers() {}

    public static BigDecimal min(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElse(null);
    }

    public static BigDecimal max(List<BigDecimal> values) {
        return values.stream().max(BigDecimal::compareTo).orElse(null);
    }

    public static BigDecimal avg(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }
}
