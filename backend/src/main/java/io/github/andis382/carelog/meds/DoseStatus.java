package io.github.andis382.carelog.meds;

/** What a person recorded for a dose. */
public enum DoseStatus {
    GIVEN,
    /** Deliberately not given (e.g. the doctor said to pause it). A reason is expected. */
    SKIPPED,
    /** Offered, but she would not take it. */
    REFUSED
}
