package io.github.andis382.carelog.meds;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One scheduled dose: this medicine, on this day, at this time (circle time). */
public record DoseSlot(Medication medication, LocalDate date, LocalTime time) {

    public LocalDateTime at() {
        return date.atTime(time);
    }

    public Key key() {
        return new Key(medication.getId(), date, time);
    }

    public record Key(Long medicationId, LocalDate date, LocalTime time) {
        public static Key of(DoseEvent event) {
            return new Key(event.getMedicationId(), event.getDoseDate(), event.getScheduledTime());
        }
    }
}
