package io.github.andis382.carelog.meds;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoseTimingTest {

    private static final ZoneId TIRANE = ZoneId.of("Europe/Tirane");
    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final LocalDateTime EIGHT = LocalDate.of(2026, 9, 23).atTime(8, 0);

    @Test
    void aDoseIsUpcomingThenDueThenLateThenMissed() {
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.minusMinutes(61), GRACE)).isEqualTo(DoseState.UPCOMING);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.minusMinutes(60), GRACE)).isEqualTo(DoseState.DUE);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(30), GRACE)).isEqualTo(DoseState.DUE);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(31), GRACE)).isEqualTo(DoseState.LATE);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(120), GRACE)).isEqualTo(DoseState.LATE);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(121), GRACE)).isEqualTo(DoseState.MISSED);
    }

    @Test
    void theGracePeriodIsTheCirclesChoice() {
        Duration hour = Duration.ofMinutes(60);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(45), hour)).isEqualTo(DoseState.DUE);
        assertThat(DoseTiming.state(EIGHT, null, EIGHT.plusMinutes(61), hour)).isEqualTo(DoseState.LATE);
    }

    @Test
    void whateverWasRecordedWinsOverTheClock() {
        LocalDateTime evening = EIGHT.plusHours(12);
        assertThat(DoseTiming.state(EIGHT, DoseStatus.GIVEN, evening, GRACE)).isEqualTo(DoseState.GIVEN);
        assertThat(DoseTiming.state(EIGHT, DoseStatus.REFUSED, evening, GRACE)).isEqualTo(DoseState.REFUSED);
        assertThat(DoseTiming.state(EIGHT, DoseStatus.SKIPPED, EIGHT.minusHours(3), GRACE)).isEqualTo(DoseState.SKIPPED);
    }

    @Test
    void aDoseGivenAfterTheGracePeriodCountsAsGivenLate() {
        assertThat(DoseTiming.givenLate(EIGHT, EIGHT.plusMinutes(30), GRACE)).isFalse();
        assertThat(DoseTiming.givenLate(EIGHT, EIGHT.plusMinutes(31), GRACE)).isTrue();
    }

    @Test
    void timelineMatchesEventsToSlotsAndMeasuresLateness() {
        Medication metformin = DoseScheduleTest.daily(1, "Metformin", EIGHT.toLocalDate().minusDays(5),
            LocalTime.of(8, 0), LocalTime.of(13, 0), LocalTime.of(20, 0));
        List<DoseSlot> slots = DoseSchedule.forDay(List.of(metformin), EIGHT.toLocalDate(), TIRANE);
        DoseEvent givenLate = new DoseEvent(1L, 1L, EIGHT.toLocalDate(), LocalTime.of(8, 0), DoseStatus.GIVEN, null, 7L,
            EIGHT.plusMinutes(75).atZone(TIRANE).toInstant(), null);
        LocalDateTime now = EIGHT.plusHours(5).plusMinutes(50);

        List<DoseTimeline.Line> lines = DoseTimeline.build(slots, List.of(givenLate), now, GRACE, TIRANE);

        assertThat(lines).extracting(l -> l.state()).containsExactly(DoseState.GIVEN, DoseState.LATE, DoseState.UPCOMING);
        assertThat(lines.get(0).givenLate()).isTrue();
        assertThat(lines.get(0).minutesLate()).isEqualTo(75);
        assertThat(lines.get(1).minutesLate()).isEqualTo(50);
        assertThat(lines.get(2).minutesLate()).isZero();
    }

    @Test
    void anUnrecordedDoseFromYesterdayIsMissed() {
        Medication statin = DoseScheduleTest.daily(1, "Atorvastatin", EIGHT.toLocalDate().minusDays(5), LocalTime.of(21, 0));
        List<DoseSlot> yesterday = DoseSchedule.forDay(List.of(statin), EIGHT.toLocalDate().minusDays(1), TIRANE);

        List<DoseTimeline.Line> lines = DoseTimeline.build(yesterday, List.of(), EIGHT, GRACE, TIRANE);

        assertThat(lines).singleElement().satisfies(l -> {
            assertThat(l.state()).isEqualTo(DoseState.MISSED);
            assertThat(l.minutesLate()).isEqualTo(11 * 60);
        });
    }
}
