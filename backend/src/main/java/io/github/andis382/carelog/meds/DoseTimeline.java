package io.github.andis382.carelog.meds;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Scheduled doses matched with what was recorded, as seen at one moment. */
public final class DoseTimeline {

    /** A scheduled dose and its state. {@code minutesLate} is how late it is, or was when given. */
    public record Line(DoseSlot slot, DoseEvent event, DoseState state, boolean givenLate, long minutesLate) {}

    private DoseTimeline() {}

    public static List<Line> build(List<DoseSlot> slots, Collection<DoseEvent> events, LocalDateTime now,
                                   Duration grace, ZoneId zone) {
        Map<DoseSlot.Key, DoseEvent> recorded = events.stream()
            .filter(e -> e.getScheduledTime() != null)
            .collect(Collectors.toMap(DoseSlot.Key::of, e -> e, (first, second) -> first));
        return slots.stream().map(slot -> line(slot, recorded.get(slot.key()), now, grace, zone)).toList();
    }

    private static Line line(DoseSlot slot, DoseEvent event, LocalDateTime now, Duration grace, ZoneId zone) {
        DoseState state = DoseTiming.state(slot.at(), event == null ? null : event.getStatus(), now, grace);
        if (event == null) {
            boolean overdue = state == DoseState.LATE || state == DoseState.MISSED;
            return new Line(slot, null, state, false, overdue ? DoseTiming.minutesPast(slot.at(), now) : 0);
        }
        LocalDateTime recordedAt = LocalDateTime.ofInstant(event.getRecordedAt(), zone);
        boolean late = event.getStatus() == DoseStatus.GIVEN && DoseTiming.givenLate(slot.at(), recordedAt, grace);
        return new Line(slot, event, state, late, late ? DoseTiming.minutesPast(slot.at(), recordedAt) : 0);
    }
}
