package io.github.andis382.carelog.log;

import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.daily.JournalRepository;
import io.github.andis382.carelog.daily.MealRepository;
import io.github.andis382.carelog.files.UploadController;
import io.github.andis382.carelog.meds.DoseEventRepository;
import io.github.andis382.carelog.meds.Medication;
import io.github.andis382.carelog.meds.MedicationChangeRepository;
import io.github.andis382.carelog.meds.MedicationRepository;
import io.github.andis382.carelog.rota.CheckIn;
import io.github.andis382.carelog.rota.CheckInRepository;
import io.github.andis382.carelog.rota.Shift;
import io.github.andis382.carelog.rota.ShiftRepository;
import io.github.andis382.carelog.rota.ShiftSwap;
import io.github.andis382.carelog.rota.ShiftSwapRepository;
import io.github.andis382.carelog.supplies.Supply;
import io.github.andis382.carelog.supplies.SupplyEventRepository;
import io.github.andis382.carelog.supplies.SupplyRepository;
import io.github.andis382.carelog.visits.DoctorVisit;
import io.github.andis382.carelog.visits.DoctorVisitRepository;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import io.github.andis382.carelog.vitals.VitalRepository;
import io.github.andis382.carelog.vitals.VitalService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The shared timeline: every table the circle writes to, merged into one list per day range.
 * One query per source and one for names, whatever the number of entries.
 */
@Service
public class LogService {

    public enum Type { MEDS, VITALS, MEALS, NOTES, VISITS, SHIFTS, SUPPLIES }

    /** {@code data} carries the type-specific fields; the client renders them in its language. */
    public record Entry(Type type, String kind, String key, Instant at, Long byId, String by, Map<String, Object> data) {}

    public record Page(LocalDate from, LocalDate to, LocalDate visibleFrom, boolean limitedByPlan, List<Entry> entries) {}

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DoseEventRepository doses;
    private final MedicationRepository medications;
    private final MedicationChangeRepository medicationChanges;
    private final VitalRepository vitals;
    private final MealRepository meals;
    private final JournalRepository journal;
    private final DoctorVisitRepository visits;
    private final CheckInRepository checkIns;
    private final ShiftSwapRepository swaps;
    private final ShiftRepository shifts;
    private final SupplyEventRepository supplyEvents;
    private final SupplyRepository supplies;
    private final CircleService circle;
    private final CircleTime time;

    public LogService(DoseEventRepository doses, MedicationRepository medications, MedicationChangeRepository medicationChanges,
                      VitalRepository vitals, MealRepository meals, JournalRepository journal, DoctorVisitRepository visits,
                      CheckInRepository checkIns, ShiftSwapRepository swaps, ShiftRepository shifts,
                      SupplyEventRepository supplyEvents, SupplyRepository supplies, CircleService circle, CircleTime time) {
        this.doses = doses;
        this.medications = medications;
        this.medicationChanges = medicationChanges;
        this.vitals = vitals;
        this.meals = meals;
        this.journal = journal;
        this.visits = visits;
        this.checkIns = checkIns;
        this.swaps = swaps;
        this.shifts = shifts;
        this.supplyEvents = supplyEvents;
        this.supplies = supplies;
        this.circle = circle;
        this.time = time;
    }

    /** Entries from {@code days} days ending on {@code to}, newest first, cut at the plan's history limit. */
    public Page page(Long orgId, LocalDate to, int days, Set<Type> types) {
        LocalDate requestedFrom = to.minusDays(days - 1L);
        LocalDate visibleFrom = circle.visibleFrom(orgId);
        boolean limited = visibleFrom != null && requestedFrom.isBefore(visibleFrom);
        LocalDate from = limited ? visibleFrom : requestedFrom;
        if (from.isAfter(to)) {
            return new Page(from, to, visibleFrom, true, List.of());
        }
        ZoneId zone = time.zone(orgId);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<Long, String> names = circle.names(orgId);
        Set<Type> wanted = types == null || types.isEmpty() ? EnumSet.allOf(Type.class) : types;

        List<Entry> entries = new ArrayList<>();
        if (wanted.contains(Type.MEDS)) {
            addMedicines(orgId, start, end, names, entries);
        }
        if (wanted.contains(Type.VITALS)) {
            Map<VitalKind, VitalRange> ranges = circle.ranges(orgId);
            vitals.findByOrganizationIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(orgId, start, end).forEach(v ->
                entries.add(entry(Type.VITALS, v.getKind().name(), "v" + v.getId(), v.getMeasuredAt(), v.getRecordedBy(), names,
                    data("value1", v.getValue1(), "value2", v.getValue2(), "note", v.getNote(),
                        "position", VitalService.position(v, ranges.get(v.getKind()))))));
        }
        if (wanted.contains(Type.MEALS)) {
            meals.findByOrganizationIdAndRecordedAtBetween(orgId, start, end).forEach(m ->
                entries.add(entry(Type.MEALS, m.getSlot().name(), "m" + m.getId(), m.getRecordedAt(), m.getRecordedBy(), names,
                    data("amount", m.getAmount() == null ? null : m.getAmount().name(), "glasses", m.getGlasses(),
                        "note", m.getNote()))));
        }
        if (wanted.contains(Type.NOTES)) {
            journal.findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(orgId, start, end).forEach(j ->
                entries.add(entry(Type.NOTES, j.getKind().name(), "j" + j.getId(), j.getRecordedAt(), j.getRecordedBy(), names,
                    data("score", j.getScore(), "text", j.getBody(), "photoUrl", UploadController.fileUrl(j.getPhotoFileId())))));
        }
        if (wanted.contains(Type.VISITS)) {
            visits.findByOrganizationIdAndVisitDateBetweenOrderByVisitDateAsc(orgId, from, to).forEach(v ->
                entries.add(entry(Type.VISITS, "VISIT", "d" + v.getId(), visitMoment(v, zone), v.getRecordedBy(), names,
                    data("doctorName", v.getDoctorName(), "specialty", v.getSpecialty(), "notes", v.getNotes(),
                        "nextDate", v.getNextDate(), "visitId", v.getId(),
                        "prescriptionUrl", UploadController.fileUrl(v.getPrescriptionFileId())))));
        }
        if (wanted.contains(Type.SHIFTS)) {
            addDuty(orgId, start, end, names, entries);
        }
        if (wanted.contains(Type.SUPPLIES)) {
            Map<Long, String> supplyNames = supplies.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(Supply::getId, Supply::getName));
            supplyEvents.findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(orgId, start, end).forEach(e ->
                entries.add(entry(Type.SUPPLIES, e.getStatus().name(), "s" + e.getId(), e.getRecordedAt(), e.getRecordedBy(), names,
                    data("name", supplyNames.get(e.getSupplyId())))));
        }
        entries.sort(Comparator.comparing(Entry::at).reversed());
        return new Page(from, to, visibleFrom, limited, entries);
    }

    private void addMedicines(Long orgId, Instant start, Instant end, Map<Long, String> names, List<Entry> entries) {
        Map<Long, String> labels = medications.findByOrganizationIdOrderByNameAsc(orgId).stream()
            .collect(Collectors.toMap(Medication::getId, Medication::label));
        doses.findByOrganizationIdAndRecordedAtBetween(orgId, start, end).forEach(d ->
            entries.add(entry(Type.MEDS, "DOSE_" + d.getStatus().name(), "e" + d.getId(), d.getRecordedAt(), d.getRecordedBy(), names,
                data("medication", labels.get(d.getMedicationId()), "medicationId", d.getMedicationId(),
                    "date", d.getDoseDate(), "time", d.getScheduledTime() == null ? null : HH_MM.format(d.getScheduledTime()),
                    "note", d.getNote()))));
        medicationChanges.findByOrganizationIdAndChangedAtBetween(orgId, start, end).forEach(c ->
            entries.add(entry(Type.MEDS, c.getKind().name(), "c" + c.getId(), c.getChangedAt(), c.getChangedBy(), names,
                data("medication", labels.get(c.getMedicationId()), "medicationId", c.getMedicationId(), "note", c.getNote()))));
    }

    private void addDuty(Long orgId, Instant start, Instant end, Map<Long, String> names, List<Entry> entries) {
        List<CheckIn> arrived = checkIns.findByOrganizationIdAndCheckedInAtBetweenOrderByCheckedInAtAsc(orgId, start, end);
        arrived.forEach(c -> entries.add(entry(Type.SHIFTS, "CHECK_IN", "i" + c.getId(), c.getCheckedInAt(), c.getUserId(), names,
            data("located", c.getInLat() != null))));
        checkIns.findByOrganizationIdAndCheckedOutAtBetween(orgId, start, end).forEach(c ->
            entries.add(entry(Type.SHIFTS, "CHECK_OUT", "o" + c.getId(), c.getCheckedOutAt(), c.getUserId(), names,
                data("minutes", c.minutesUntil(c.getCheckedOutAt())))));
        List<ShiftSwap> answered = swaps.findByOrganizationIdAndRespondedAtBetween(orgId, start, end).stream()
            .filter(s -> s.getStatus() == ShiftSwap.Status.ACCEPTED || s.getStatus() == ShiftSwap.Status.DECLINED)
            .toList();
        Map<Long, Shift> swapShifts = answered.isEmpty() ? Map.of()
            : shifts.findAllById(answered.stream().map(ShiftSwap::getShiftId).toList()).stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));
        answered.forEach(s -> {
            Shift shift = swapShifts.get(s.getShiftId());
            entries.add(entry(Type.SHIFTS, "SWAP_" + s.getStatus().name(), "w" + s.getId(), s.getRespondedAt(), s.getToUserId(),
                names, data("from", names.get(s.getFromUserId()), "date", shift == null ? null : shift.getShiftDate(),
                    "start", shift == null ? null : HH_MM.format(shift.getStartTime()),
                    "end", shift == null ? null : HH_MM.format(shift.getEndTime()))));
        });
    }

    /** A visit is filed on its day: at the time it was written if written that day, else at noon. */
    private static Instant visitMoment(DoctorVisit v, ZoneId zone) {
        if (v.getCreatedAt().atZone(zone).toLocalDate().equals(v.getVisitDate())) {
            return v.getCreatedAt();
        }
        return v.getVisitDate().atTime(12, 0).atZone(zone).toInstant();
    }

    private static Entry entry(Type type, String kind, String key, Instant at, Long byId, Map<Long, String> names,
                               Map<String, Object> data) {
        return new Entry(type, kind, key, at, byId, names.get(byId), data);
    }

    /** Key-value pairs that may hold nulls (Map.of does not allow them). */
    private static Map<String, Object> data(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
