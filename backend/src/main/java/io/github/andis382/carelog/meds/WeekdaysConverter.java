package io.github.andis382.carelog.meds;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Weekdays stored as ISO numbers, "1,4" for Monday and Thursday. */
@Converter
public class WeekdaysConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(days).stream().map(d -> String.valueOf(d.getValue())).collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String column) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(String::trim).map(Integer::parseInt).map(DayOfWeek::of).forEach(days::add);
        }
        return days;
    }
}
