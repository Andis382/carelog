package io.github.andis382.carelog.meds;

import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.files.FileStorage;
import io.github.andis382.carelog.meds.MedicationDtos.ChangeView;
import io.github.andis382.carelog.meds.MedicationDtos.DoseMark;
import io.github.andis382.carelog.meds.MedicationDtos.FieldChange;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationDetail;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationRequest;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationView;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The medicine list and its history. Every change is kept with who made it and when. */
@Service
public class MedicationService {

    private static final int MAX_TIMES = 8;
    private static final int RECENT_DAYS = 14;

    private final MedicationRepository medications;
    private final MedicationChangeRepository changes;
    private final DoseEventRepository doses;
    private final CircleService circle;
    private final CircleTime time;
    private final FileStorage files;
    private final ObjectMapper json;

    public MedicationService(MedicationRepository medications, MedicationChangeRepository changes, DoseEventRepository doses,
                             CircleService circle, CircleTime time, FileStorage files, ObjectMapper json) {
        this.medications = medications;
        this.changes = changes;
        this.doses = doses;
        this.circle = circle;
        this.time = time;
        this.files = files;
        this.json = json;
    }

    public List<MedicationView> list(Long orgId) {
        Map<Long, String> names = circle.names(orgId);
        return medications.findByOrganizationIdOrderByNameAsc(orgId).stream()
            .sorted(Comparator.comparing((Medication m) -> !m.isActive()))
            .map(m -> MedicationView.of(m, names))
            .toList();
    }

    public MedicationDetail detail(Long orgId, Long id) {
        Medication medication = find(orgId, id);
        Map<Long, String> names = circle.names(orgId);
        List<ChangeView> history = changes.findByMedicationIdOrderByChangedAtDesc(id).stream()
            .map(c -> new ChangeView(c.getId(), c.getKind().name(), readChanges(c.getChangesJson()), c.getNote(),
                names.get(c.getChangedBy()), c.getChangedAt()))
            .toList();
        LocalDate today = time.today(orgId);
        LocalDate from = circle.clampFrom(orgId, today.minusDays(RECENT_DAYS - 1));
        return new MedicationDetail(MedicationView.of(medication, names), history,
            recentDoses(orgId, medication, from, today, names), from);
    }

    @Transactional
    public MedicationView create(Long orgId, Long userId, MedicationRequest req) {
        validate(req);
        Medication medication = new Medication(orgId, req.name().trim(), req.frequency(), req.startDate(), userId);
        apply(medication, req, orgId);
        medication.setCreatedAt(time.now());
        medications.save(medication);
        changes.save(new MedicationChange(orgId, medication.getId(), MedicationChange.Kind.STARTED, null, null, userId, time.now()));
        return MedicationView.of(medication, circle.names(orgId));
    }

    @Transactional
    public MedicationView update(Long orgId, Long userId, Long id, MedicationRequest req) {
        Medication medication = find(orgId, id);
        if (!medication.isActive()) {
            throw ApiException.conflict("medication.stopped");
        }
        validate(req);
        Map<String, String> before = snapshot(medication);
        medication.setName(req.name().trim());
        medication.setFrequency(req.frequency());
        medication.setStartDate(req.startDate());
        apply(medication, req, orgId);
        Map<String, String> after = snapshot(medication);
        List<FieldChange> diff = before.keySet().stream()
            .filter(field -> !Objects.equals(before.get(field), after.get(field)))
            .map(field -> new FieldChange(field, before.get(field), after.get(field)))
            .toList();
        if (!diff.isEmpty()) {
            changes.save(new MedicationChange(orgId, id, MedicationChange.Kind.CHANGED, json.writeValueAsString(diff), null,
                userId, time.now()));
        }
        return MedicationView.of(medication, circle.names(orgId));
    }

    @Transactional
    public MedicationView stop(Long orgId, Long userId, Long id, String reason) {
        Medication medication = find(orgId, id);
        if (!medication.isActive()) {
            throw ApiException.conflict("medication.stopped");
        }
        String why = reason == null || reason.isBlank() ? null : reason.trim();
        Instant now = time.now();
        medication.stop(userId, now, time.today(orgId), why);
        changes.save(new MedicationChange(orgId, id, MedicationChange.Kind.STOPPED, null, why, userId, now));
        return MedicationView.of(medication, circle.names(orgId));
    }

    public Medication find(Long orgId, Long id) {
        return medications.findByIdAndOrganizationId(id, orgId).orElseThrow(ApiException::notFound);
    }

    private void validate(MedicationRequest req) {
        List<LocalTime> times = req.times() == null ? List.of() : req.times();
        if (req.frequency() != Frequency.AS_NEEDED && times.isEmpty()) {
            throw ApiException.field("times", "medication.times_required");
        }
        if (times.size() > MAX_TIMES) {
            throw ApiException.field("times", "medication.too_many_times");
        }
        if (req.frequency() == Frequency.WEEKLY && (req.weekdays() == null || req.weekdays().isEmpty())) {
            throw ApiException.field("weekdays", "medication.weekdays_required");
        }
        if (req.weekdays() != null && req.weekdays().stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw ApiException.field("weekdays", "medication.weekdays_required");
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw ApiException.field("endDate", "medication.end_before_start");
        }
    }

    private void apply(Medication m, MedicationRequest req, Long orgId) {
        m.setStrength(blankToNull(req.strength()));
        m.setDoseText(blankToNull(req.doseText()));
        m.setInstructions(blankToNull(req.instructions()));
        m.setPrescriber(blankToNull(req.prescriber()));
        m.setEndDate(req.endDate());
        m.setBoxPhotoFileId(files.owned(orgId, req.boxPhotoFileId(), "boxPhotoFileId"));
        boolean scheduled = req.frequency() != Frequency.AS_NEEDED;
        m.setTimes(scheduled && req.times() != null ? req.times() : List.of());
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (req.frequency() == Frequency.WEEKLY) {
            req.weekdays().forEach(d -> days.add(DayOfWeek.of(d)));
        }
        m.setWeekdays(days);
    }

    /** The fields a person would care to see in the history, as display text. */
    private static Map<String, String> snapshot(Medication m) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", m.getName());
        fields.put("strength", m.getStrength());
        fields.put("doseText", m.getDoseText());
        fields.put("frequency", m.getFrequency().name());
        fields.put("times", m.getTimes().stream().map(TimesConverter.HH_MM::format).collect(Collectors.joining(", ")));
        fields.put("weekdays", new WeekdaysConverter().convertToDatabaseColumn(m.getWeekdays()));
        fields.put("instructions", m.getInstructions());
        fields.put("startDate", m.getStartDate() == null ? null : m.getStartDate().toString());
        fields.put("endDate", m.getEndDate() == null ? null : m.getEndDate().toString());
        fields.put("prescriber", m.getPrescriber());
        return fields;
    }

    private List<FieldChange> readChanges(String changesJson) {
        if (changesJson == null || changesJson.isBlank()) {
            return List.of();
        }
        return json.readValue(changesJson, new TypeReference<List<FieldChange>>() {});
    }

    private List<DoseMark> recentDoses(Long orgId, Medication medication, LocalDate from, LocalDate today,
                                       Map<Long, String> names) {
        List<DoseEvent> events = doses.findByMedicationIdAndDoseDateBetween(medication.getId(), from, today);
        List<DoseMark> marks = new ArrayList<>();
        if (medication.getFrequency() == Frequency.AS_NEEDED) {
            events.stream().sorted(Comparator.comparing(DoseEvent::getRecordedAt).reversed())
                .forEach(e -> marks.add(new DoseMark(e.getDoseDate(), null, e.getStatus().name(), false,
                    names.get(e.getRecordedBy()), e.getRecordedAt(), e.getNote())));
            return marks;
        }
        ZoneId zone = time.zone(orgId);
        LocalDateTime now = time.localNow(orgId);
        List<DoseSlot> slots = DoseSchedule.forDays(List.of(medication), from, today, zone);
        for (DoseTimeline.Line line : DoseTimeline.build(slots, events, now, circle.settings(orgId).grace(), zone)) {
            DoseEvent e = line.event();
            marks.add(new DoseMark(line.slot().date(), TimesConverter.HH_MM.format(line.slot().time()), line.state().name(),
                line.givenLate(), e == null ? null : names.get(e.getRecordedBy()), e == null ? null : e.getRecordedAt(),
                e == null ? null : e.getNote()));
        }
        return marks;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
