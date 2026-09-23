package io.github.andis382.carelog.summary;

import io.github.andis382.carelog.common.Numbers;
import io.github.andis382.carelog.daily.JournalEntry;
import io.github.andis382.carelog.daily.JournalKind;
import io.github.andis382.carelog.daily.Meal;
import io.github.andis382.carelog.daily.MealAmount;
import io.github.andis382.carelog.meds.DoseEvent;
import io.github.andis382.carelog.meds.DoseSchedule;
import io.github.andis382.carelog.meds.DoseSlot;
import io.github.andis382.carelog.meds.DoseTimeline;
import io.github.andis382.carelog.meds.Medication;
import io.github.andis382.carelog.rota.CheckIn;
import io.github.andis382.carelog.rota.Shift;
import io.github.andis382.carelog.summary.WeeklyReport.Doses;
import io.github.andis382.carelog.summary.WeeklyReport.Duty;
import io.github.andis382.carelog.summary.WeeklyReport.Meals;
import io.github.andis382.carelog.summary.WeeklyReport.Notable;
import io.github.andis382.carelog.summary.WeeklyReport.Problem;
import io.github.andis382.carelog.summary.WeeklyReport.VitalStat;
import io.github.andis382.carelog.supplies.Supply;
import io.github.andis382.carelog.vitals.Vital;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import io.github.andis382.carelog.vitals.VitalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Turns a week of records into the numbers of the weekly summary. Pure: everything it needs
 * comes in through {@link Inputs}, so the arithmetic can be tested without a database.
 */
public final class WeeklyReportBuilder {

    private static final int MAX_NOTES = 6;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private WeeklyReportBuilder() {}

    public record Inputs(LocalDate weekStart, LocalDateTime now, ZoneId zone, Duration grace, List<Medication> medications,
                         List<DoseEvent> doses, List<Vital> vitals, Map<VitalKind, VitalRange> ranges, List<Meal> meals,
                         List<JournalEntry> journal, List<Supply> supplies, List<Shift> shifts, List<CheckIn> checkIns,
                         Map<Long, String> names) {}

    public static WeeklyReport build(Inputs in) {
        LocalDate weekEnd = in.weekStart().plusDays(6);
        LocalDateTime end = in.weekStart().plusDays(7).atStartOfDay();
        boolean complete = !in.now().isBefore(end);
        LocalDateTime asOf = complete ? end : in.now();

        List<DoseTimeline.Line> lines = doseLines(in, asOf);
        return new WeeklyReport(in.weekStart(), weekEnd, asOf, complete, doses(lines), problems(lines, in.names()),
            vitals(in.vitals(), in.ranges()), meals(in.meals()), notable(in.journal(), in.names()),
            average(in.journal(), JournalKind.MOOD), average(in.journal(), JournalKind.PAIN), suppliesLow(in.supplies()),
            duty(in.shifts(), in.checkIns(), in.names(), asOf.atZone(in.zone()).toInstant()));
    }

    /** Scheduled doses up to {@code asOf}; a Sunday 19:00 summary does not count the 20:00 dose. */
    private static List<DoseTimeline.Line> doseLines(Inputs in, LocalDateTime asOf) {
        LocalDate lastDay = asOf.toLocalDate().isAfter(in.weekStart().plusDays(6)) ? in.weekStart().plusDays(6) : asOf.toLocalDate();
        List<DoseSlot> slots = DoseSchedule.forDays(in.medications(), in.weekStart(), lastDay, in.zone()).stream()
            .filter(s -> !s.at().isAfter(asOf))
            .toList();
        return DoseTimeline.build(slots, in.doses(), asOf, in.grace(), in.zone());
    }

    static Doses doses(List<DoseTimeline.Line> lines) {
        int given = 0;
        int late = 0;
        int skipped = 0;
        int refused = 0;
        int missed = 0;
        int open = 0;
        for (DoseTimeline.Line line : lines) {
            switch (line.state()) {
                case GIVEN -> {
                    given++;
                    if (line.givenLate()) {
                        late++;
                    }
                }
                case SKIPPED -> skipped++;
                case REFUSED -> refused++;
                case MISSED -> missed++;
                default -> open++;
            }
        }
        int due = given + skipped + refused + missed;
        Integer pct = due == 0 ? null : (int) Math.round(given * 100.0 / due);
        return new Doses(due, given, late, skipped, refused, missed, open, pct);
    }

    static List<Problem> problems(List<DoseTimeline.Line> lines, Map<Long, String> names) {
        return lines.stream()
            .filter(l -> switch (l.state()) {
                case MISSED, REFUSED, SKIPPED -> true;
                default -> false;
            })
            .map(l -> new Problem(l.slot().date(), HH_MM.format(l.slot().time()), l.slot().medication().label(), l.state().name(),
                l.event() == null ? null : l.event().getNote(), l.event() == null ? null : names.get(l.event().getRecordedBy())))
            .toList();
    }

    static List<VitalStat> vitals(List<Vital> readings, Map<VitalKind, VitalRange> ranges) {
        Map<VitalKind, List<Vital>> byKind = new EnumMap<>(VitalKind.class);
        readings.forEach(v -> byKind.computeIfAbsent(v.getKind(), k -> new ArrayList<>()).add(v));
        List<VitalStat> stats = new ArrayList<>();
        byKind.forEach((kind, list) -> {
            List<BigDecimal> firsts = list.stream().map(Vital::getValue1).toList();
            List<BigDecimal> seconds = list.stream().map(Vital::getValue2).filter(Objects::nonNull).toList();
            int outside = (int) list.stream().filter(v -> VitalService.position(v, ranges.get(kind)) != null).count();
            stats.add(new VitalStat(kind.name(), list.size(), Numbers.min(firsts), Numbers.avg(firsts), Numbers.max(firsts),
                Numbers.min(seconds), Numbers.avg(seconds), Numbers.max(seconds), outside));
        });
        return stats;
    }

    /** The last entry per meal wins: a corrected lunch replaces the first tap. */
    static Meals meals(List<Meal> meals) {
        Map<String, MealAmount> mainMeals = new LinkedHashMap<>();
        Map<LocalDate, Integer> glassesByDay = new TreeMap<>();
        meals.stream().sorted(Comparator.comparing(Meal::getRecordedAt)).forEach(m -> {
            glassesByDay.merge(m.getMealDate(), m.getGlasses(), Integer::sum);
            if (m.getSlot().isMainMeal() && m.getAmount() != null) {
                mainMeals.put(m.getMealDate() + "/" + m.getSlot(), m.getAmount());
            }
        });
        int well = (int) mainMeals.values().stream().filter(MealAmount::ateWell).count();
        int glasses = glassesByDay.values().stream().mapToInt(Integer::intValue).sum();
        BigDecimal perDay = glassesByDay.isEmpty() ? null
            : BigDecimal.valueOf(glasses).divide(BigDecimal.valueOf(glassesByDay.size()), 1, RoundingMode.HALF_UP);
        return new Meals(mainMeals.size(), well, mainMeals.size() - well, glassesByDay.size(), perDay);
    }

    /** Incidents, strong pain (4-5) and very low mood (1-2): what a child abroad should not miss. */
    static List<Notable> notable(List<JournalEntry> journal, Map<Long, String> names) {
        return journal.stream()
            .filter(WeeklyReportBuilder::isNotable)
            .sorted(Comparator.comparing(JournalEntry::getRecordedAt).reversed())
            .limit(MAX_NOTES)
            .map(j -> new Notable(j.getRecordedAt(), j.getKind().name(), j.getScore(), j.getBody(), names.get(j.getRecordedBy())))
            .toList();
    }

    static boolean isNotable(JournalEntry j) {
        return switch (j.getKind()) {
            case INCIDENT -> true;
            case PAIN -> j.getScore() != null && j.getScore() >= 4;
            case MOOD -> j.getScore() != null && j.getScore() <= 2;
            default -> false;
        };
    }

    static BigDecimal average(List<JournalEntry> journal, JournalKind kind) {
        List<BigDecimal> scores = journal.stream()
            .filter(j -> j.getKind() == kind && j.getScore() != null)
            .map(j -> BigDecimal.valueOf(j.getScore()))
            .toList();
        return Numbers.avg(scores);
    }

    static List<String> suppliesLow(List<Supply> supplies) {
        return supplies.stream().filter(s -> s.getStatus().needsBuying()).map(Supply::getName).sorted().toList();
    }

    /** Rota hours next to checked-in hours: what was planned, and what the carer's phone proves. */
    static List<Duty> duty(List<Shift> shifts, List<CheckIn> checkIns, Map<Long, String> names, Instant asOf) {
        Map<Long, long[]> minutes = new LinkedHashMap<>();
        shifts.forEach(s -> minutes.computeIfAbsent(s.getUserId(), k -> new long[2])[0] += s.minutes());
        checkIns.forEach(c -> minutes.computeIfAbsent(c.getUserId(), k -> new long[2])[1] += c.minutesUntil(asOf));
        return minutes.entrySet().stream()
            .map(e -> new Duty(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
            .sorted(Comparator.comparingLong((Duty d) -> -Math.max(d.scheduledMinutes(), d.checkedInMinutes()))
                .thenComparing(d -> d.name() == null ? "" : d.name()))
            .toList();
    }
}
