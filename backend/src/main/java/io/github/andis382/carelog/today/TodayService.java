package io.github.andis382.carelog.today;

import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.circle.ElderRepository;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.daily.DailyController.JournalView;
import io.github.andis382.carelog.daily.JournalEntry;
import io.github.andis382.carelog.daily.JournalKind;
import io.github.andis382.carelog.daily.JournalRepository;
import io.github.andis382.carelog.daily.Meal;
import io.github.andis382.carelog.daily.MealRepository;
import io.github.andis382.carelog.files.UploadController;
import io.github.andis382.carelog.meds.DoseEvent;
import io.github.andis382.carelog.meds.DoseEventRepository;
import io.github.andis382.carelog.meds.DoseSchedule;
import io.github.andis382.carelog.meds.DoseService;
import io.github.andis382.carelog.meds.DoseStatus;
import io.github.andis382.carelog.meds.DoseTimeline;
import io.github.andis382.carelog.meds.Frequency;
import io.github.andis382.carelog.meds.Medication;
import io.github.andis382.carelog.meds.MedicationRepository;
import io.github.andis382.carelog.rota.CheckInService;
import io.github.andis382.carelog.rota.RotaService;
import io.github.andis382.carelog.rota.Shift;
import io.github.andis382.carelog.rota.ShiftSwap;
import io.github.andis382.carelog.rota.ShiftSwapRepository;
import io.github.andis382.carelog.supplies.Supply;
import io.github.andis382.carelog.supplies.SupplyRepository;
import io.github.andis382.carelog.today.TodayView.AppointmentView;
import io.github.andis382.carelog.today.TodayView.AsNeededView;
import io.github.andis382.carelog.today.TodayView.CheckInView;
import io.github.andis382.carelog.today.TodayView.DoseLineView;
import io.github.andis382.carelog.today.TodayView.DutyView;
import io.github.andis382.carelog.today.TodayView.ElderCard;
import io.github.andis382.carelog.today.TodayView.EventView;
import io.github.andis382.carelog.today.TodayView.MealMark;
import io.github.andis382.carelog.today.TodayView.ScoreMark;
import io.github.andis382.carelog.today.TodayView.VitalCard;
import io.github.andis382.carelog.visits.VisitService;
import io.github.andis382.carelog.vitals.Vital;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRange;
import io.github.andis382.carelog.vitals.VitalService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Assembles the daily card: medicines, duty, vitals, meals, mood and notes for one day. */
@Service
public class TodayService {

    /** How far back the daily card can be opened to fill in a forgotten tick. */
    private static final int BACKFILL_DAYS = 7;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ElderRepository elders;
    private final MedicationRepository medications;
    private final DoseEventRepository doses;
    private final DoseService doseService;
    private final VitalService vitals;
    private final MealRepository meals;
    private final JournalRepository journal;
    private final RotaService rota;
    private final CheckInService checkIns;
    private final ShiftSwapRepository swaps;
    private final SupplyRepository supplies;
    private final VisitService visits;
    private final CircleService circle;
    private final CircleTime time;

    public TodayService(ElderRepository elders, MedicationRepository medications, DoseEventRepository doses,
                        DoseService doseService, VitalService vitals, MealRepository meals, JournalRepository journal,
                        RotaService rota, CheckInService checkIns, ShiftSwapRepository swaps, SupplyRepository supplies,
                        VisitService visits, CircleService circle, CircleTime time) {
        this.elders = elders;
        this.medications = medications;
        this.doses = doses;
        this.doseService = doseService;
        this.vitals = vitals;
        this.meals = meals;
        this.journal = journal;
        this.rota = rota;
        this.checkIns = checkIns;
        this.swaps = swaps;
        this.supplies = supplies;
        this.visits = visits;
        this.circle = circle;
        this.time = time;
    }

    public TodayView day(Long orgId, Long userId, Role role, LocalDate requested) {
        ZoneId zone = time.zone(orgId);
        LocalDateTime now = time.localNow(orgId);
        LocalDate today = now.toLocalDate();
        LocalDate date = requested == null ? today : requested;
        if (date.isAfter(today) || date.isBefore(today.minusDays(BACKFILL_DAYS))) {
            throw ApiException.badRequest("today.date_out_of_range");
        }
        Map<Long, String> names = circle.names(orgId);
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<Medication> meds = medications.findByOrganizationIdOrderByNameAsc(orgId);
        List<DoseEvent> dayEvents = doses.findByOrganizationIdAndDoseDateBetween(orgId, date, date);
        List<DoseTimeline.Line> lines = DoseTimeline.build(DoseSchedule.forDay(meds, date, zone), dayEvents, now,
            circle.settings(orgId).grace(), zone);

        List<JournalEntry> entries = journal.findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(orgId, dayStart, dayEnd);
        List<Meal> dayMeals = meals.findByOrganizationIdAndMealDateBetweenOrderByRecordedAtAsc(orgId, date, date);

        return new TodayView(date, today, time.now(), circle.settings(orgId).getGraceMinutes(), elderCard(orgId, today),
            duty(orgId, date, now, names),
            checkIns.open(userId).map(c -> new CheckInView(c.getId(), c.getCheckedInAt())).orElse(null),
            lines.stream().map(l -> doseLine(l, names, userId)).toList(),
            asNeeded(meds, names, userId),
            vitalCards(orgId, names),
            mealMarks(dayMeals, names),
            dayMeals.stream().mapToInt(Meal::getGlasses).sum(),
            lastScore(entries, JournalKind.MOOD, names),
            lastScore(entries, JournalKind.PAIN, names),
            entries.stream().sorted(Comparator.comparing(JournalEntry::getRecordedAt).reversed())
                .map(j -> JournalView.of(j, names.get(j.getRecordedBy()))).toList(),
            appointment(orgId, today),
            supplies.findByOrganizationIdOrderByNameAsc(orgId).stream().filter(s -> s.getStatus().needsBuying())
                .map(Supply::getName).toList(),
            (int) swaps.findByOrganizationIdAndStatus(orgId, ShiftSwap.Status.PENDING).stream()
                .filter(s -> s.getToUserId().equals(userId)).count(),
            role.canRecord());
    }

    private ElderCard elderCard(Long orgId, LocalDate today) {
        return elders.findByOrganizationId(orgId)
            .map(e -> new ElderCard(e.getFullName(), e.firstName(), e.ageIn(today.getYear()), e.getTown(),
                UploadController.fileUrl(e.getPhotoFileId()), e.getConditions(), e.getAllergies()))
            .orElse(null);
    }

    private List<DutyView> duty(Long orgId, LocalDate date, LocalDateTime now, Map<Long, String> names) {
        List<Shift> shifts = new ArrayList<>(rota.between(orgId, date, date));
        if (date.equals(now.toLocalDate())) {
            rota.onDuty(orgId, now).stream()
                .filter(s -> shifts.stream().noneMatch(listed -> listed.getId().equals(s.getId())))
                .forEach(s -> shifts.add(0, s));
        }
        return shifts.stream()
            .map(s -> new DutyView(s.getUserId(), names.get(s.getUserId()), HH_MM.format(s.getStartTime()),
                HH_MM.format(s.getEndTime()), s.getKind().name(), s.covers(now)))
            .toList();
    }

    private DoseLineView doseLine(DoseTimeline.Line line, Map<Long, String> names, Long userId) {
        Medication m = line.slot().medication();
        return new DoseLineView(m.getId(), m.getName(), m.getStrength(), m.getDoseText(), m.getInstructions(),
            HH_MM.format(line.slot().time()), line.state().name(), line.givenLate(), line.minutesLate(),
            line.event() == null ? null : event(line.event(), names, userId));
    }

    private List<AsNeededView> asNeeded(List<Medication> meds, Map<Long, String> names, Long userId) {
        return meds.stream()
            .filter(m -> m.isActive() && m.getFrequency() == Frequency.AS_NEEDED)
            .map(m -> new AsNeededView(m.getId(), m.getName(), m.getStrength(), m.getDoseText(), m.getInstructions(),
                doses.findTopByMedicationIdAndScheduledTimeIsNullAndStatusOrderByRecordedAtDesc(m.getId(), DoseStatus.GIVEN)
                    .map(e -> event(e, names, userId)).orElse(null)))
            .toList();
    }

    private EventView event(DoseEvent e, Map<Long, String> names, Long userId) {
        return new EventView(e.getId(), e.getStatus().name(), e.getRecordedBy(), names.get(e.getRecordedBy()), e.getRecordedAt(),
            e.getNote(), doseService.canUndo(e, userId));
    }

    private List<VitalCard> vitalCards(Long orgId, Map<Long, String> names) {
        Map<VitalKind, VitalRange> ranges = circle.ranges(orgId);
        Map<VitalKind, Vital> latest = vitals.latest(orgId);
        List<VitalCard> cards = new ArrayList<>();
        for (VitalKind kind : VitalKind.values()) {
            Vital last = latest.get(kind);
            cards.add(new VitalCard(kind.name(), last == null ? null : VitalService.view(last, ranges.get(kind), names),
                VitalService.RangeView.of(ranges.get(kind))));
        }
        return cards;
    }

    /** The latest entry per meal: a second tap on lunch corrects the first. */
    private static Map<String, MealMark> mealMarks(List<Meal> dayMeals, Map<Long, String> names) {
        Map<String, MealMark> marks = new LinkedHashMap<>();
        dayMeals.stream().filter(m -> m.getAmount() != null).forEach(m ->
            marks.put(m.getSlot().name(), new MealMark(m.getAmount().name(), names.get(m.getRecordedBy()), m.getRecordedAt())));
        return marks;
    }

    private static ScoreMark lastScore(List<JournalEntry> entries, JournalKind kind, Map<Long, String> names) {
        return entries.stream()
            .filter(j -> j.getKind() == kind && j.getScore() != null)
            .max(Comparator.comparing(JournalEntry::getRecordedAt))
            .map(j -> new ScoreMark(j.getScore(), names.get(j.getRecordedBy()), j.getRecordedAt()))
            .orElse(null);
    }

    private AppointmentView appointment(Long orgId, LocalDate today) {
        return visits.nextAppointment(orgId)
            .map(v -> new AppointmentView(v.getNextDate(), v.getNextTime() == null ? null : HH_MM.format(v.getNextTime()),
                v.getDoctorName(), v.getSpecialty(), v.getPlace(), ChronoUnit.DAYS.between(today, v.getNextDate())))
            .orElse(null);
    }
}
