package io.github.andis382.carelog.meds;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Times of day stored as "08:00,20:00": sorted, without duplicates. */
@Converter
public class TimesConverter implements AttributeConverter<List<LocalTime>, String> {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(List<LocalTime> times) {
        if (times == null) {
            return "";
        }
        return times.stream().sorted().distinct().map(HH_MM::format).collect(Collectors.joining(","));
    }

    @Override
    public List<LocalTime> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(",")).map(String::trim).map(LocalTime::parse).sorted().toList();
    }
}
