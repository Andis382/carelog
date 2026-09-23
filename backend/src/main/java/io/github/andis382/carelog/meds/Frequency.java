package io.github.andis382.carelog.meds;

public enum Frequency {
    /** Every day at the listed times. */
    DAILY,
    /** On the listed weekdays at the listed times (vitamin D on Sundays). */
    WEEKLY,
    /** No schedule; each dose is recorded when it is given (paracetamol for pain). */
    AS_NEEDED
}
