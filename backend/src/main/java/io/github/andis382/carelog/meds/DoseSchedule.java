package io.github.andis382.carelog.meds;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Which doses are due on a given day. Pure: no clock and no database, so it is easy to test. */
public final class DoseSchedule {

    private DoseSchedule() {}

    /** All scheduled doses of the day, by time and then by medicine name. */
    public static List<DoseSlot> forDay(Collection<Medication> medications, LocalDate day, ZoneId zone) {
        List<DoseSlot> slots = new ArrayList<>();
        for (Medication medication : medications) {
            for (LocalTime time : timesOn(medication, day, zone)) {
                slots.add(new DoseSlot(medication, day, time));
            }
        }
        slots.sort(Comparator.comparing(DoseSlot::time)
            .thenComparing(s -> s.medication().getName(), String.CASE_INSENSITIVE_ORDER));
        return slots;
    }

    public static List<DoseSlot> forDays(Collection<Medication> medications, LocalDate from, LocalDate to, ZoneId zone) {
        List<DoseSlot> slots = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            slots.addAll(forDay(medications, day, zone));
        }
        return slots;
    }

    public static List<LocalTime> timesOn(Medication medication, LocalDate day, ZoneId zone) {
        if (medication.getFrequency() == Frequency.AS_NEEDED || day.isBefore(medication.getStartDate())) {
            return List.of();
        }
        if (medication.getEndDate() != null && day.isAfter(medication.getEndDate())) {
            return List.of();
        }
        if (medication.getFrequency() == Frequency.WEEKLY && !medication.getWeekdays().contains(day.getDayOfWeek())) {
            return List.of();
        }
        LocalTime cutoff = stopCutoff(medication, day, zone);
        return medication.getTimes().stream()
            .filter(t -> cutoff == null || !t.isAfter(cutoff))
            .sorted()
            .toList();
    }

    /** A medicine stopped at 12:40 still owes its 08:00 dose that day, but not the 20:00 one. */
    private static LocalTime stopCutoff(Medication medication, LocalDate day, ZoneId zone) {
        if (medication.getStoppedAt() == null) {
            return null;
        }
        ZonedDateTime stopped = medication.getStoppedAt().atZone(zone);
        return stopped.toLocalDate().equals(day) ? stopped.toLocalTime() : null;
    }
}
