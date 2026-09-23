package io.github.andis382.carelog.summary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One week of care in numbers, as the payer sees it. Built by {@link WeeklyReportBuilder}. */
public record WeeklyReport(
    LocalDate weekStart,
    LocalDate weekEnd,
    LocalDateTime asOf,
    boolean complete,
    Doses doses,
    List<Problem> problems,
    List<VitalStat> vitals,
    Meals meals,
    List<Notable> notes,
    BigDecimal moodAverage,
    BigDecimal painAverage,
    List<String> suppliesLow,
    List<Duty> duty) {

    /** Doses that were due by {@code asOf}. Doses still inside their window are "open" and not counted. */
    public record Doses(int due, int given, int givenLate, int skipped, int refused, int missed, int open, Integer adherencePct) {}

    public record Problem(LocalDate date, String time, String medicine, String state, String note, String by) {}

    public record VitalStat(String kind, int count, BigDecimal min, BigDecimal avg, BigDecimal max, BigDecimal min2,
                            BigDecimal avg2, BigDecimal max2, int outsideRange) {}

    public record Meals(int mainMeals, int ateWell, int ateLittle, int daysRecorded, BigDecimal glassesPerDay) {}

    public record Notable(Instant at, String kind, Integer score, String text, String by) {}

    public record Duty(Long userId, String name, long scheduledMinutes, long checkedInMinutes) {}
}
