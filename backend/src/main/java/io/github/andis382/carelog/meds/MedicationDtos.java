package io.github.andis382.carelog.meds;

import io.github.andis382.carelog.files.UploadController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class MedicationDtos {

    private MedicationDtos() {}

    public record MedicationRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 80) String strength,
        @Size(max = 160) String doseText,
        @Size(max = 500) String instructions,
        @NotNull Frequency frequency,
        List<LocalTime> times,
        List<Integer> weekdays,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 160) String prescriber,
        @Size(max = 36) String boxPhotoFileId) {}

    public record StopRequest(@Size(max = 300) String reason) {}

    public record MedicationView(Long id, String name, String strength, String label, String doseText, String instructions,
                                 String frequency, List<String> times, List<Integer> weekdays, LocalDate startDate,
                                 LocalDate endDate, boolean active, String prescriber, String boxPhotoFileId,
                                 String boxPhotoUrl, Instant createdAt, String createdByName, Instant stoppedAt,
                                 String stoppedByName, String stopReason) {

        public static MedicationView of(Medication m, Map<Long, String> names) {
            return new MedicationView(m.getId(), m.getName(), m.getStrength(), m.label(), m.getDoseText(), m.getInstructions(),
                m.getFrequency().name(), m.getTimes().stream().map(TimesConverter.HH_MM::format).toList(),
                m.getWeekdays().stream().sorted().map(DayOfWeek::getValue).toList(), m.getStartDate(), m.getEndDate(),
                m.isActive(), m.getPrescriber(), m.getBoxPhotoFileId(), UploadController.fileUrl(m.getBoxPhotoFileId()),
                m.getCreatedAt(), names.get(m.getCreatedBy()), m.getStoppedAt(), names.get(m.getStoppedBy()), m.getStopReason());
        }
    }

    /** One changed field. Values are plain text ("08:00, 20:00"); weekdays are ISO numbers ("1,4"). */
    public record FieldChange(String field, String from, String to) {}

    public record ChangeView(Long id, String kind, List<FieldChange> changes, String note, String byName, Instant at) {}

    /** A recorded or expected dose, as the medicine's detail page shows it. */
    public record DoseMark(LocalDate date, String time, String state, boolean givenLate, String byName, Instant at, String note) {}

    public record MedicationDetail(MedicationView medication, List<ChangeView> history, List<DoseMark> recent,
                                   LocalDate recentFrom) {}
}
