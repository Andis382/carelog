package io.github.andis382.carelog.vitals;

import java.math.BigDecimal;

/**
 * What can be measured at home. Blood pressure is the one paired reading (systolic over
 * diastolic). The bounds reject typing mistakes, not unusual values; the default normal
 * range is only a starting point the family can change.
 */
public enum VitalKind {
    BP(true, 60, 260, 30, 160, "90", "140", "60", "90"),
    SUGAR(false, 20, 600, 0, 0, "70", "180", null, null),
    TEMP(false, 33, 43, 0, 0, "36.0", "37.5", null, null),
    PULSE(false, 25, 220, 0, 0, "60", "100", null, null),
    SPO2(false, 50, 100, 0, 0, "94", "100", null, null),
    WEIGHT(false, 20, 250, 0, 0, null, null, null, null);

    private final boolean paired;
    private final double min;
    private final double max;
    private final double min2;
    private final double max2;
    private final BigDecimal defaultLow;
    private final BigDecimal defaultHigh;
    private final BigDecimal defaultLow2;
    private final BigDecimal defaultHigh2;

    VitalKind(boolean paired, double min, double max, double min2, double max2,
              String low, String high, String low2, String high2) {
        this.paired = paired;
        this.min = min;
        this.max = max;
        this.min2 = min2;
        this.max2 = max2;
        this.defaultLow = low == null ? null : new BigDecimal(low);
        this.defaultHigh = high == null ? null : new BigDecimal(high);
        this.defaultLow2 = low2 == null ? null : new BigDecimal(low2);
        this.defaultHigh2 = high2 == null ? null : new BigDecimal(high2);
    }

    public boolean paired() { return paired; }

    /** Temperature and weight keep one decimal; the rest are whole numbers. */
    public int decimals() {
        return this == TEMP || this == WEIGHT ? 1 : 0;
    }

    /** Always worth a line in the weekly summary; the others only when a reading was unusual. */
    public boolean alwaysSummarised() {
        return this == BP || this == SUGAR || this == WEIGHT;
    }

    public boolean plausible(double value) {
        return value >= min && value <= max;
    }

    public boolean plausibleSecond(double value) {
        return value >= min2 && value <= max2;
    }

    public BigDecimal defaultLow() { return defaultLow; }
    public BigDecimal defaultHigh() { return defaultHigh; }
    public BigDecimal defaultLow2() { return defaultLow2; }
    public BigDecimal defaultHigh2() { return defaultHigh2; }
}
