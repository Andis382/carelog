package io.github.andis382.carelog.meds;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The late and missed rules, in one place:
 * a dose is due from an hour before its time, late once the circle's grace period is over,
 * and missed two hours after its time. The two-hour mark is also when the optional
 * WhatsApp alert goes to the coordinator.
 */
public final class DoseTiming {

    public static final Duration DUE_EARLY = Duration.ofHours(1);
    public static final Duration MISSED_AFTER = Duration.ofHours(2);

    private DoseTiming() {}

    public static DoseState state(LocalDateTime scheduled, DoseStatus recorded, LocalDateTime now, Duration grace) {
        if (recorded != null) {
            return DoseState.of(recorded);
        }
        if (now.isBefore(scheduled.minus(DUE_EARLY))) {
            return DoseState.UPCOMING;
        }
        if (!now.isAfter(scheduled.plus(grace))) {
            return DoseState.DUE;
        }
        if (!now.isAfter(scheduled.plus(MISSED_AFTER))) {
            return DoseState.LATE;
        }
        return DoseState.MISSED;
    }

    /** Given, but after the grace period. Counted as given; shown as "given late". */
    public static boolean givenLate(LocalDateTime scheduled, LocalDateTime givenAt, Duration grace) {
        return givenAt.isAfter(scheduled.plus(grace));
    }

    public static long minutesPast(LocalDateTime scheduled, LocalDateTime moment) {
        return Math.max(0, Duration.between(scheduled, moment).toMinutes());
    }
}
