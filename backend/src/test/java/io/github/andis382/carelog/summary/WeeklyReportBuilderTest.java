package io.github.andis382.carelog.summary;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.andis382.carelog.daily.JournalEntry;
import io.github.andis382.carelog.daily.JournalKind;
import io.github.andis382.carelog.daily.Meal;
import io.github.andis382.carelog.daily.MealAmount;
import io.github.andis382.carelog.daily.MealSlot;
import io.github.andis382.carelog.meds.DoseEvent;
import io.github.andis382.carelog.meds.DoseSchedule;
import io.github.andis382.carelog.meds.DoseSlot;
import io.github.andis382.carelog.meds.DoseStatus;
import io.github.andis382.carelog.meds.Frequency;
import io.github.andis382.carelog.meds.Medication;
import io.github.andis382.carelog.rota.CheckIn;
import io.github.andis382.carelog.rota.Shift;
import io.github.andis382.carelog.rota.ShiftKind;
import io.github.andis382.carelog.supplies.Supply;
import io.github.andis382.carelog.supplies.SupplyStatus;
import io.github.andis382.carelog.vitals.Vital;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WeeklyReportBuilderTest {

    private static final ZoneId TIRANE = ZoneId.of("Europe/Tirane");
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
    private static final long MIRA = 10L;
    private static final long GENT = 11L;
    private static final Map<Long, String> NAMES = Map.of(MIRA, "Mira Hasani", GENT, "Gent Kola");

    private final Medication metformin = medicine(1, "Metformin", "500 mg", LocalTime.of(8, 0), LocalTime.of(20, 0));
    private final Medication statin = medicine(2, "Atorvastatin", "20 mg", LocalTime.of(21, 0));

    @Test
    void adherenceCountsGivenDosesAgainstEveryDoseThatWasDue() {
        List<DoseEvent> events = allGivenExcept(
            key(metformin, MONDAY.plusDays(2), 20), key(statin, MONDAY.plusDays(4), 21));
        events.add(event(metformin, MONDAY.plusDays(2), 20, DoseStatus.REFUSED, MONDAY.plusDays(2).atTime(20, 10)));
        replace(events, key(metformin, MONDAY.plusDays(3), 8),
            event(metformin, MONDAY.plusDays(3), 8, DoseStatus.GIVEN, MONDAY.plusDays(3).atTime(9, 0)));

        WeeklyReport report = build(MONDAY.plusDays(8).atTime(10, 0), events, List.of(), List.of(), List.of());

        assertThat(report.complete()).isTrue();
        WeeklyReport.Doses doses = report.doses();
        assertThat(doses.due()).isEqualTo(21);
        assertThat(doses.given()).isEqualTo(19);
        assertThat(doses.givenLate()).isEqualTo(1);
        assertThat(doses.refused()).isEqualTo(1);
        assertThat(doses.missed()).isEqualTo(1);
        assertThat(doses.adherencePct()).isEqualTo(90);
        assertThat(report.problems()).extracting(p -> p.date().getDayOfWeek() + " " + p.time() + " " + p.medicine() + " " + p.state())
            .containsExactly("WEDNESDAY 20:00 Metformin 500 mg REFUSED", "FRIDAY 21:00 Atorvastatin 20 mg MISSED");
        assertThat(report.problems().get(0).by()).isEqualTo("Mira Hasani");
    }

    @Test
    void aSummarySentOnSundayEveningDoesNotCountDosesThatAreNotDueYet() {
        LocalDateTime sundayEvening = MONDAY.plusDays(6).atTime(19, 0);
        List<DoseEvent> events = new ArrayList<>();
        for (DoseSlot slot : DoseSchedule.forDays(List.of(metformin, statin), MONDAY, MONDAY.plusDays(6), TIRANE)) {
            if (slot.at().isBefore(sundayEvening)) {
                events.add(event(slot.medication(), slot.date(), slot.time().getHour(), DoseStatus.GIVEN, slot.at().plusMinutes(5)));
            }
        }

        WeeklyReport report = build(sundayEvening, events, List.of(), List.of(), List.of());

        assertThat(report.complete()).isFalse();
        assertThat(report.doses().due()).isEqualTo(19);
        assertThat(report.doses().adherencePct()).isEqualTo(100);
        assertThat(report.problems()).isEmpty();
    }

    @Test
    void vitalsAreSummarisedPerKindAgainstTheUsualRange() {
        List<Vital> readings = List.of(
            bp(130, 80, MONDAY.atTime(8, 30)), bp(150, 95, MONDAY.plusDays(1).atTime(8, 30)), bp(120, 75, MONDAY.plusDays(2).atTime(8, 30)),
            new Vital(1L, VitalKind.SUGAR, new BigDecimal("118"), null, at(MONDAY.atTime(7, 50)), null, MIRA, null));

        WeeklyReport report = build(MONDAY.plusDays(8).atTime(10, 0), List.of(), readings, List.of(), List.of());

        assertThat(report.vitals()).extracting(WeeklyReport.VitalStat::kind).containsExactly("BP", "SUGAR");
        WeeklyReport.VitalStat bp = report.vitals().get(0);
        assertThat(bp.count()).isEqualTo(3);
        assertThat(bp.avg()).isEqualByComparingTo("133.3");
        assertThat(bp.min()).isEqualByComparingTo("120");
        assertThat(bp.max()).isEqualByComparingTo("150");
        assertThat(bp.avg2()).isEqualByComparingTo("83.3");
        assertThat(bp.outsideRange()).isEqualTo(1);
    }

    @Test
    void mealsCountTheLatestEntryPerMealAndAverageWaterOverRecordedDays() {
        List<Meal> meals = List.of(
            meal(MONDAY, MealSlot.LUNCH, MealAmount.HALF, 0, MONDAY.atTime(13, 30)),
            meal(MONDAY, MealSlot.LUNCH, MealAmount.ALL, 0, MONDAY.atTime(13, 45)),
            meal(MONDAY, MealSlot.DRINK, null, 3, MONDAY.atTime(15, 0)),
            meal(MONDAY.plusDays(1), MealSlot.DINNER, MealAmount.LITTLE, 0, MONDAY.plusDays(1).atTime(19, 30)),
            meal(MONDAY.plusDays(1), MealSlot.DRINK, null, 5, MONDAY.plusDays(1).atTime(20, 0)));

        WeeklyReport.Meals summary = WeeklyReportBuilder.meals(meals);

        assertThat(summary.mainMeals()).isEqualTo(2);
        assertThat(summary.ateWell()).isEqualTo(1);
        assertThat(summary.ateLittle()).isEqualTo(1);
        assertThat(summary.daysRecorded()).isEqualTo(2);
        assertThat(summary.glassesPerDay()).isEqualByComparingTo("4.0");
    }

    @Test
    void notableNotesAreIncidentsStrongPainAndLowMood() {
        List<JournalEntry> journal = List.of(
            note(JournalKind.INCIDENT, null, "Slipped in the bathroom", 1),
            note(JournalKind.PAIN, 4, "Knee after the walk", 2),
            note(JournalKind.PAIN, 2, null, 3),
            note(JournalKind.MOOD, 2, "Sad about her husband", 4),
            note(JournalKind.MOOD, 4, null, 5),
            note(JournalKind.NOTE, null, "Short walk in the yard", 6));

        WeeklyReport report = build(MONDAY.plusDays(8).atTime(10, 0), List.of(), List.of(), List.of(), journal);

        assertThat(report.notes()).extracting(WeeklyReport.Notable::kind).containsExactly("MOOD", "PAIN", "INCIDENT");
        assertThat(report.moodAverage()).isEqualByComparingTo("3.0");
        assertThat(report.painAverage()).isEqualByComparingTo("3.0");
    }

    @Test
    void hoursOnDutyShowTheRotaNextToWhatCheckInsProve() {
        List<Shift> shifts = new ArrayList<>();
        List<CheckIn> checkIns = new ArrayList<>();
        for (int day = 0; day < 6; day++) {
            LocalDate date = MONDAY.plusDays(day);
            shifts.add(new Shift(1L, MIRA, date, LocalTime.of(8, 0), LocalTime.of(16, 0), ShiftKind.DAY, null, GENT));
            CheckIn checkIn = new CheckIn(1L, MIRA, at(date.atTime(8, 3)), null, null);
            checkIn.checkOut(at(date.atTime(15, 58)), null, null);
            checkIns.add(checkIn);
        }
        shifts.add(new Shift(1L, GENT, MONDAY.plusDays(5), LocalTime.of(22, 0), LocalTime.of(6, 0), ShiftKind.NIGHT, null, GENT));

        List<WeeklyReport.Duty> duty = WeeklyReportBuilder.duty(shifts, checkIns, NAMES, at(MONDAY.plusDays(8).atTime(10, 0)));

        assertThat(duty).extracting(d -> d.name() + " " + d.scheduledMinutes() + " " + d.checkedInMinutes())
            .containsExactly("Mira Hasani 2880 2850", "Gent Kola 480 0");
    }

    @Test
    void lowSuppliesAreListedByName() {
        List<Supply> supplies = List.of(
            new Supply(1L, "Wipes", SupplyStatus.OUT, MIRA, Instant.now()),
            new Supply(1L, "Gloves", SupplyStatus.OK, MIRA, Instant.now()),
            new Supply(1L, "Diapers", SupplyStatus.RUNNING_LOW, MIRA, Instant.now()));

        assertThat(WeeklyReportBuilder.suppliesLow(supplies)).containsExactly("Diapers", "Wipes");
    }

    private WeeklyReport build(LocalDateTime now, List<DoseEvent> events, List<Vital> vitals, List<Meal> meals,
                               List<JournalEntry> journal) {
        return WeeklyReportBuilder.build(new WeeklyReportBuilder.Inputs(MONDAY, now, TIRANE, Duration.ofMinutes(30),
            List.of(metformin, statin), events, vitals, Map.of(VitalKind.BP, new VitalRange(1L, VitalKind.BP)), meals, journal,
            List.of(), List.of(), List.of(), NAMES));
    }

    private List<DoseEvent> allGivenExcept(String... skipped) {
        List<String> skip = List.of(skipped);
        List<DoseEvent> events = new ArrayList<>();
        for (DoseSlot slot : DoseSchedule.forDays(List.of(metformin, statin), MONDAY, MONDAY.plusDays(6), TIRANE)) {
            if (!skip.contains(key(slot.medication(), slot.date(), slot.time().getHour()))) {
                events.add(event(slot.medication(), slot.date(), slot.time().getHour(), DoseStatus.GIVEN, slot.at().plusMinutes(5)));
            }
        }
        return events;
    }

    private static void replace(List<DoseEvent> events, String key, DoseEvent replacement) {
        events.removeIf(e -> (e.getMedicationId() + "/" + e.getDoseDate() + "/" + e.getScheduledTime().getHour()).equals(key));
        events.add(replacement);
    }

    private static String key(Medication m, LocalDate day, int hour) {
        return m.getId() + "/" + day + "/" + hour;
    }

    private static DoseEvent event(Medication m, LocalDate day, int hour, DoseStatus status, LocalDateTime recordedAt) {
        return new DoseEvent(1L, m.getId(), day, LocalTime.of(hour, 0), status, status == DoseStatus.GIVEN ? null : "Nausea", MIRA,
            at(recordedAt), null);
    }

    private static Medication medicine(long id, String name, String strength, LocalTime... times) {
        Medication m = new Medication(1L, name, Frequency.DAILY, MONDAY.minusMonths(3), GENT);
        m.setStrength(strength);
        m.setTimes(List.of(times));
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static Vital bp(int sys, int dia, LocalDateTime when) {
        return new Vital(1L, VitalKind.BP, BigDecimal.valueOf(sys), BigDecimal.valueOf(dia), at(when), null, MIRA, null);
    }

    private static Meal meal(LocalDate day, MealSlot slot, MealAmount amount, int glasses, LocalDateTime when) {
        return new Meal(1L, day, slot, amount, glasses, null, MIRA, at(when), null);
    }

    private static JournalEntry note(JournalKind kind, Integer score, String text, int hourOffset) {
        return new JournalEntry(1L, kind, score, text, null, MIRA, at(MONDAY.plusDays(1).atTime(8, 0).plusHours(hourOffset)), null);
    }

    private static Instant at(LocalDateTime local) {
        return local.atZone(TIRANE).toInstant();
    }
}
