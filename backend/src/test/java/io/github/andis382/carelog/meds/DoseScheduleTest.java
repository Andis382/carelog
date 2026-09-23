package io.github.andis382.carelog.meds;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DoseScheduleTest {

    private static final ZoneId TIRANE = ZoneId.of("Europe/Tirane");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);

    static Medication daily(long id, String name, LocalDate start, LocalTime... times) {
        Medication m = new Medication(1L, name, Frequency.DAILY, start, 1L);
        m.setTimes(List.of(times));
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void dailyMedicineIsDueAtEachTimeSortedAcrossMedicines() {
        Medication metformin = daily(1, "Metformin", MONDAY.minusDays(30), LocalTime.of(20, 0), LocalTime.of(8, 0));
        Medication amlodipine = daily(2, "Amlodipine", MONDAY.minusDays(30), LocalTime.of(8, 0));

        List<DoseSlot> slots = DoseSchedule.forDay(List.of(metformin, amlodipine), MONDAY, TIRANE);

        assertThat(slots).extracting(s -> s.time() + " " + s.medication().getName())
            .containsExactly("08:00 Amlodipine", "08:00 Metformin", "20:00 Metformin");
    }

    @Test
    void nothingIsDueBeforeTheStartDateOrAfterTheEndDate() {
        Medication course = daily(1, "Amoxicillin", MONDAY, LocalTime.of(9, 0));
        course.setEndDate(MONDAY.plusDays(6));

        assertThat(DoseSchedule.forDay(List.of(course), MONDAY.minusDays(1), TIRANE)).isEmpty();
        assertThat(DoseSchedule.forDay(List.of(course), MONDAY, TIRANE)).hasSize(1);
        assertThat(DoseSchedule.forDay(List.of(course), MONDAY.plusDays(6), TIRANE)).hasSize(1);
        assertThat(DoseSchedule.forDay(List.of(course), MONDAY.plusDays(7), TIRANE)).isEmpty();
    }

    @Test
    void weeklyMedicineIsDueOnlyOnItsWeekdays() {
        Medication vitaminD = daily(1, "Vitamin D", MONDAY.minusDays(60), LocalTime.of(10, 0));
        vitaminD.setFrequency(Frequency.WEEKLY);
        vitaminD.setWeekdays(EnumSet.of(DayOfWeek.SUNDAY));

        List<DoseSlot> week = DoseSchedule.forDays(List.of(vitaminD), MONDAY, MONDAY.plusDays(6), TIRANE);

        assertThat(week).singleElement().satisfies(s -> assertThat(s.date().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY));
    }

    @Test
    void asNeededMedicineHasNoSchedule() {
        Medication paracetamol = new Medication(1L, "Paracetamol", Frequency.AS_NEEDED, MONDAY.minusDays(10), 1L);

        assertThat(DoseSchedule.forDay(List.of(paracetamol), MONDAY, TIRANE)).isEmpty();
    }

    @Test
    void stoppingAtMiddayKeepsTheMorningDoseButNotTheEveningOne() {
        Medication metformin = daily(1, "Metformin", MONDAY.minusDays(30), LocalTime.of(8, 0), LocalTime.of(20, 0));
        metformin.stop(1L, MONDAY.atTime(12, 40).atZone(TIRANE).toInstant(), MONDAY, "Doctor stopped it");

        assertThat(DoseSchedule.timesOn(metformin, MONDAY, TIRANE)).containsExactly(LocalTime.of(8, 0));
        assertThat(DoseSchedule.timesOn(metformin, MONDAY.minusDays(1), TIRANE)).hasSize(2);
        assertThat(DoseSchedule.timesOn(metformin, MONDAY.plusDays(1), TIRANE)).isEmpty();
    }
}
