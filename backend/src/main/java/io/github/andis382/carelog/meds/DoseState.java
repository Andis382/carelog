package io.github.andis382.carelog.meds;

/** Where a scheduled dose stands right now. */
public enum DoseState {
    /** More than an hour before its time. */
    UPCOMING,
    /** From an hour before its time until the grace period is over. */
    DUE,
    /** Past the grace period, still within two hours. */
    LATE,
    /** More than two hours past its time with nothing recorded. */
    MISSED,
    GIVEN,
    SKIPPED,
    REFUSED;

    public static DoseState of(DoseStatus status) {
        return switch (status) {
            case GIVEN -> GIVEN;
            case SKIPPED -> SKIPPED;
            case REFUSED -> REFUSED;
        };
    }
}
