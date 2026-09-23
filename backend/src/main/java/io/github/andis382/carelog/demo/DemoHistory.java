package io.github.andis382.carelog.demo;

import io.github.andis382.carelog.alerts.CircleNotifier;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.daily.JournalEntry;
import io.github.andis382.carelog.daily.JournalKind;
import io.github.andis382.carelog.daily.JournalRepository;
import io.github.andis382.carelog.daily.Meal;
import io.github.andis382.carelog.daily.MealAmount;
import io.github.andis382.carelog.daily.MealRepository;
import io.github.andis382.carelog.daily.MealSlot;
import io.github.andis382.carelog.files.FileStorage;
import io.github.andis382.carelog.meds.DoseEvent;
import io.github.andis382.carelog.meds.DoseEventRepository;
import io.github.andis382.carelog.meds.DoseSchedule;
import io.github.andis382.carelog.meds.DoseSlot;
import io.github.andis382.carelog.meds.DoseStatus;
import io.github.andis382.carelog.meds.Frequency;
import io.github.andis382.carelog.meds.Medication;
import io.github.andis382.carelog.meds.MedicationChange;
import io.github.andis382.carelog.meds.MedicationChangeRepository;
import io.github.andis382.carelog.meds.MedicationRepository;
import io.github.andis382.carelog.messaging.OutboundMessage;
import io.github.andis382.carelog.messaging.OutboundMessageRepository;
import io.github.andis382.carelog.rota.CheckIn;
import io.github.andis382.carelog.rota.CheckInRepository;
import io.github.andis382.carelog.rota.Shift;
import io.github.andis382.carelog.rota.ShiftKind;
import io.github.andis382.carelog.rota.ShiftRepository;
import io.github.andis382.carelog.rota.ShiftSwap;
import io.github.andis382.carelog.rota.ShiftSwapRepository;
import io.github.andis382.carelog.summary.SummaryService;
import io.github.andis382.carelog.summary.WeeklySummary;
import io.github.andis382.carelog.supplies.Supply;
import io.github.andis382.carelog.supplies.SupplyEvent;
import io.github.andis382.carelog.supplies.SupplyEventRepository;
import io.github.andis382.carelog.supplies.SupplyRepository;
import io.github.andis382.carelog.supplies.SupplyStatus;
import io.github.andis382.carelog.visits.DoctorVisit;
import io.github.andis382.carelog.visits.DoctorVisitRepository;
import io.github.andis382.carelog.vitals.Vital;
import io.github.andis382.carelog.vitals.VitalKind;
import io.github.andis382.carelog.vitals.VitalRepository;
import jakarta.persistence.EntityManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Five weeks of the circle's life, generated around "now": Mira on weekday days, Gent in the
 * evenings and on Sundays, a blood-pressure medicine raised after a cardiology visit and the
 * readings coming down after it, a refused dose, a missed one, a slip in the bathroom,
 * diapers running low. Random but seeded, so every fresh start tells the same story.
 */
@Component
@Profile("!test")
class DemoHistory {

    record People(User elira, User gent, User mira) {}

    private record Medicines(Medication metformin, Medication amlodipine, Medication atorvastatin, Medication vitaminD,
                             Medication paracetamol, Medication ibuprofen) {
        List<Medication> all() {
            return List.of(metformin, amlodipine, atorvastatin, vitaminD, paracetamol, ibuprofen);
        }
    }

    private static final int DAYS = 35;
    private static final BigDecimal HOME_LAT = new BigDecimal("42.068940");
    private static final BigDecimal HOME_LNG = new BigDecimal("19.512390");

    private static final String[] REFUSALS = {
        "E refuzoi, tha që i vinte për të vjellë. I dhamë çaj.",
        "Nuk deshi ta merrte, ishte e mërzitur. Provova sërish pas 20 minutash, prapë jo.",
    };
    private static final String[] MOOD_NOTES = {
        "E qetë, dëgjoi radio gjithë mëngjesin.", "Qeshi me nipin në video.", "Pak e lodhur, por e disponuar.",
        "Foli shumë për fshatin dhe rininë e saj.", null, null, null,
    };
    private static final String[] MEAL_NOTES = {
        "Supë me perime dhe bukë misri.", "Tavë kosi, i pëlqeu shumë.", "Vetëm pak kos dhe mollë.", "Fasule me sallatë.",
        "Peshk i pjekur me patate.", null, null, null, null, null,
    };

    private final Random random = new Random(20260923L);

    private final MedicationRepository medications;
    private final MedicationChangeRepository changes;
    private final DoseEventRepository doses;
    private final VitalRepository vitals;
    private final MealRepository meals;
    private final JournalRepository journal;
    private final ShiftRepository shifts;
    private final ShiftSwapRepository swaps;
    private final CheckInRepository checkIns;
    private final SupplyRepository supplies;
    private final SupplyEventRepository supplyEvents;
    private final DoctorVisitRepository visits;
    private final OutboundMessageRepository outbox;
    private final FileStorage files;
    private final CircleNotifier notifier;
    private final SummaryService summaries;
    private final CircleTime time;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    private ZoneId zone;
    private LocalDateTime now;
    private LocalDate today;

    DemoHistory(MedicationRepository medications, MedicationChangeRepository changes, DoseEventRepository doses,
                VitalRepository vitals, MealRepository meals, JournalRepository journal, ShiftRepository shifts,
                ShiftSwapRepository swaps, CheckInRepository checkIns, SupplyRepository supplies,
                SupplyEventRepository supplyEvents, DoctorVisitRepository visits, OutboundMessageRepository outbox,
                FileStorage files, CircleNotifier notifier, SummaryService summaries, CircleTime time,
                EntityManager entityManager, JdbcTemplate jdbc) {
        this.medications = medications;
        this.changes = changes;
        this.doses = doses;
        this.vitals = vitals;
        this.meals = meals;
        this.journal = journal;
        this.shifts = shifts;
        this.swaps = swaps;
        this.checkIns = checkIns;
        this.supplies = supplies;
        this.supplyEvents = supplyEvents;
        this.visits = visits;
        this.outbox = outbox;
        this.files = files;
        this.notifier = notifier;
        this.summaries = summaries;
        this.time = time;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    void write(Long orgId, People p) {
        zone = time.zone(orgId);
        now = time.localNow(orgId);
        today = now.toLocalDate();
        LocalDate first = today.minusDays(DAYS);
        LocalDate cardiologyDay = today.minusDays(18);

        Medicines meds = medicines(orgId, p, first, cardiologyDay);
        List<Shift> rota = rota(orgId, p, first);
        checkIns(orgId, rota);
        doses(orgId, p, meds, rota, first);
        asNeeded(orgId, p, meds, rota);
        vitals(orgId, p, rota, first, cardiologyDay);
        meals(orgId, p, rota, first);
        journal(orgId, p, rota, first);
        visits(orgId, p, cardiologyDay);
        supplies(orgId, p);
        messages(orgId, p, meds, rota);
    }

    // ---------------------------------------------------------------- medicines

    private Medicines medicines(Long orgId, People p, LocalDate first, LocalDate cardiologyDay) {
        Instant added = instant(first.minusDays(1), 19, 10);
        Medication metformin = medicine(orgId, p.gent(), added, "Metformin", "500 mg", "1 tabletë", "Pas ngrënies",
            Frequency.DAILY, List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)), today.minusMonths(30), "Dr. Lindita Gjoni");
        Medication amlodipine = medicine(orgId, p.gent(), added, "Amlodipine", "2.5 mg", "1 tabletë", "Në mëngjes",
            Frequency.DAILY, List.of(LocalTime.of(8, 0)), today.minusMonths(14), "Dr. Arben Muça");
        Medication atorvastatin = medicine(orgId, p.gent(), added, "Atorvastatin", "20 mg", "1 tabletë", "Në darkë",
            Frequency.DAILY, List.of(LocalTime.of(21, 0)), today.minusMonths(20), "Dr. Lindita Gjoni");
        Medication vitaminD = medicine(orgId, p.gent(), added, "Vitamina D3", "25 000 IU", "1 kapsulë", "Me mëngjesin e së dielës",
            Frequency.WEEKLY, List.of(LocalTime.of(10, 0)), today.minusMonths(6), "Dr. Lindita Gjoni");
        vitaminD.setWeekdays(EnumSet.of(DayOfWeek.SUNDAY));
        Medication paracetamol = medicine(orgId, p.gent(), added, "Paracetamol", "500 mg", "1 tabletë",
            "Kur ka dhimbje. Jo më shumë se 3 në ditë, sipas mjekes.", Frequency.AS_NEEDED, List.of(), today.minusMonths(6),
            "Dr. Lindita Gjoni");
        Medication ibuprofen = medicine(orgId, p.gent(), added, "Ibuprofen", "400 mg", "1 tabletë", "Pas ngrënies, për dhimbjet e gjurit",
            Frequency.AS_NEEDED, List.of(), today.minusMonths(4), "Dr. Lindita Gjoni");

        Instant visitNoon = instant(cardiologyDay, 12, 5);
        ibuprofen.stop(p.gent().getId(), visitNoon, cardiologyDay, "Kardiologu e ndërpreu: rrit tensionin. Për dhimbjet vetëm Paracetamol.");
        changes.save(new MedicationChange(orgId, ibuprofen.getId(), MedicationChange.Kind.STOPPED, null, ibuprofen.getStopReason(),
            p.gent().getId(), visitNoon));
        amlodipine.setStrength("5 mg");
        changes.save(new MedicationChange(orgId, amlodipine.getId(), MedicationChange.Kind.CHANGED,
            "[{\"field\":\"strength\",\"from\":\"2.5 mg\",\"to\":\"5 mg\"}]", null, p.gent().getId(), visitNoon.plusSeconds(120)));
        return new Medicines(metformin, amlodipine, atorvastatin, vitaminD, paracetamol, ibuprofen);
    }

    private Medication medicine(Long orgId, User by, Instant added, String name, String strength, String dose, String instructions,
                                Frequency frequency, List<LocalTime> times, LocalDate start, String prescriber) {
        Medication m = new Medication(orgId, name, frequency, start, by.getId());
        m.setStrength(strength);
        m.setDoseText(dose);
        m.setInstructions(instructions);
        m.setTimes(times);
        m.setPrescriber(prescriber);
        m.setCreatedAt(added);
        medications.save(m);
        changes.save(new MedicationChange(orgId, m.getId(), MedicationChange.Kind.STARTED, null, null, by.getId(), added));
        return m;
    }

    // ---------------------------------------------------------------- rota

    /** Mira Monday to Saturday by day, Gent every evening and on Sundays, two weeks ahead too. */
    private List<Shift> rota(Long orgId, People p, LocalDate first) {
        List<Shift> list = new ArrayList<>();
        LocalDate eliraSaturday = today.minusDays(21).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate eliraSunday = eliraSaturday.plusDays(1);
        for (LocalDate d = first; !d.isAfter(today.plusDays(14)); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                Shift sunday = shift(orgId, p.gent(), p.gent(), d, 8, 0, 21, 0, ShiftKind.DAY, null);
                if (d.equals(eliraSunday)) {
                    ShiftSwap swap = new ShiftSwap(orgId, sunday.getId(), p.gent().getId(), p.elira().getId(),
                        "Ti je këtu këtë fundjavë, a e merr ti të dielën? Unë kam turn në punë.", instant(d.minusDays(3), 21, 14));
                    swap.answer(ShiftSwap.Status.ACCEPTED, instant(d.minusDays(3), 21, 52));
                    swaps.save(swap);
                    sunday.setUserId(p.elira().getId());
                }
                list.add(sunday);
            } else {
                list.add(shift(orgId, p.mira(), p.gent(), d, 8, 0, 16, 0, ShiftKind.DAY, null));
                list.add(shift(orgId, p.gent(), p.gent(), d, 18, 30, 21, 30, ShiftKind.VISIT, null));
            }
        }
        list.add(shift(orgId, p.elira(), p.elira(), eliraSaturday, 10, 0, 18, 0, ShiftKind.VISIT, "Erdha nga Milano për fundjavë."));
        list.add(shift(orgId, p.gent(), p.gent(), today.minusDays(12), 21, 30, 7, 30, ShiftKind.NIGHT,
            "Kishte temperaturë, rri natën me të."));

        LocalDate nextSaturday = today.plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        list.stream()
            .filter(s -> s.getUserId().equals(p.mira().getId()) && s.getShiftDate().equals(nextSaturday))
            .findFirst()
            .ifPresent(s -> {
                ShiftSwap pending = swaps.save(new ShiftSwap(orgId, s.getId(), p.mira().getId(), p.gent().getId(),
                    "Kam dasmë në familje të shtunën. A mund të rrish ti?", instant(today.minusDays(1), 17, 20)));
                notifier.swapRequested(orgId, pending.getId(), p.mira(), p.gent(), s.getShiftDate(), s.getStartTime(),
                    s.getEndTime(), pending.getMessage());
                backdateLastMessage(orgId, instant(today.minusDays(1), 17, 20));
            });
        return list;
    }

    private Shift shift(Long orgId, User who, User by, LocalDate day, int sh, int sm, int eh, int em, ShiftKind kind, String note) {
        return shifts.save(new Shift(orgId, who.getId(), day, LocalTime.of(sh, sm), LocalTime.of(eh, em), kind, note, by.getId()));
    }

    /** Proof of work: arrivals a few minutes either side of the rota, phones mostly sharing location. */
    private void checkIns(Long orgId, List<Shift> rota) {
        for (Shift s : rota) {
            LocalDateTime in = s.startsAt().plusMinutes(random.nextInt(14) - 8);
            if (!in.isBefore(now) || (s.getKind() == ShiftKind.VISIT && random.nextInt(10) == 0)) {
                continue;
            }
            boolean located = s.getKind() != ShiftKind.VISIT || random.nextBoolean();
            CheckIn checkIn = new CheckIn(orgId, s.getUserId(), in.atZone(zone).toInstant(), located ? near() : null, null);
            LocalDateTime out = s.endsAt().plusMinutes(random.nextInt(12) - 3);
            if (out.isBefore(now)) {
                checkIn.checkOut(out.atZone(zone).toInstant(), located ? near() : null, null);
            }
            checkIns.save(checkIn);
        }
    }

    private CheckIn.Position near() {
        BigDecimal dLat = BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.0004);
        BigDecimal dLng = BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.0004);
        return new CheckIn.Position(HOME_LAT.add(dLat).setScale(6, RoundingMode.HALF_UP),
            HOME_LNG.add(dLng).setScale(6, RoundingMode.HALF_UP), 8 + random.nextInt(20));
    }

    // ---------------------------------------------------------------- doses

    private void doses(Long orgId, People p, Medicines meds, List<Shift> rota, LocalDate first) {
        Map<String, DoseStatus> story = Map.of(
            key(meds.metformin(), today.minusDays(9), 20), DoseStatus.REFUSED,
            key(meds.metformin(), today.minusDays(16), 8), DoseStatus.SKIPPED);
        String missed = key(meds.atorvastatin(), today.minusDays(6), 21);
        String lateOnPurpose = key(meds.metformin(), today.minusDays(3), 20);

        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            for (DoseSlot slot : DoseSchedule.forDay(meds.all(), d, zone)) {
                LocalDateTime at = slot.at();
                // Today: the latest doses stay open so the daily card shows what is due right now.
                if (!at.isBefore(now.minusMinutes(75))) {
                    continue;
                }
                String key = key(slot.medication(), d, slot.time().getHour());
                boolean recent = !d.isBefore(today.minusDays(1));
                if (key.equals(missed) || (!recent && story.get(key) == null && random.nextInt(100) < 2)) {
                    continue;
                }
                User by = onDuty(rota, at, p);
                DoseStatus status = story.getOrDefault(key, DoseStatus.GIVEN);
                String note = null;
                int offset = random.nextInt(24) - 6;
                if (status == DoseStatus.REFUSED) {
                    note = REFUSALS[0];
                } else if (status == DoseStatus.SKIPPED) {
                    note = "Sheqeri 68 në mëngjes. Mora në telefon dr. Gjonin, tha ta anashkalojmë këtë dozë.";
                } else if (key.equals(lateOnPurpose)) {
                    offset = 74;
                    note = "Darka u vonua, e dhashë pas ngrënies.";
                } else if (!recent && random.nextInt(100) < 3) {
                    status = DoseStatus.REFUSED;
                    note = REFUSALS[1];
                } else if (!recent && random.nextInt(100) < 5) {
                    offset = 40 + random.nextInt(60);
                }
                LocalDateTime recordedAt = at.plusMinutes(offset);
                if (recordedAt.isAfter(now.minusMinutes(5))) {
                    recordedAt = at.plusMinutes(4);
                }
                dose(orgId, slot.medication(), d, slot.time(), status, note, by, recordedAt);
            }
        }
    }

    private void asNeeded(Long orgId, People p, Medicines meds, List<Shift> rota) {
        Object[][] given = {
            {meds.ibuprofen(), 33, 17, 40, "Dhimbje në gjurin e majtë pas shëtitjes."},
            {meds.ibuprofen(), 29, 20, 15, "Dhimbje gjuri."},
            {meds.ibuprofen(), 24, 13, 30, null},
            {meds.ibuprofen(), 20, 19, 50, "Dhimbje gjuri, s'mund të flinte."},
            {meds.paracetamol(), 15, 18, 5, "Dhimbje gjuri pas banjës."},
            {meds.paracetamol(), 12, 19, 20, "Temperaturë 37,6."},
            {meds.paracetamol(), 11, 7, 45, "Temperaturë 37,1 në mëngjes."},
            {meds.paracetamol(), 7, 14, 10, "Dhimbje koke."},
            {meds.paracetamol(), 1, 20, 35, "Dhimbje në gjurin e djathtë pas shëtitjes."},
        };
        for (Object[] g : given) {
            LocalDate day = today.minusDays((Integer) g[1]);
            LocalDateTime at = day.atTime((Integer) g[2], (Integer) g[3]);
            dose(orgId, (Medication) g[0], day, null, DoseStatus.GIVEN, (String) g[4], onDuty(rota, at, p), at);
        }
    }

    private void dose(Long orgId, Medication m, LocalDate day, LocalTime slot, DoseStatus status, String note, User by,
                      LocalDateTime at) {
        Instant recorded = at.atZone(zone).toInstant();
        DoseEvent event = new DoseEvent(orgId, m.getId(), day, slot, status, note, by.getId(), recorded, null);
        event.setCreatedAt(recorded);
        doses.save(event);
    }

    private static String key(Medication m, LocalDate day, int hour) {
        return m.getName() + "/" + day + "/" + hour;
    }

    // ---------------------------------------------------------------- vitals

    /** Blood pressure comes down over the week after the dose change; sugar wanders; one fever. */
    private void vitals(Long orgId, People p, List<Shift> rota, LocalDate first, LocalDate cardiologyDay) {
        LocalDate feverDay = today.minusDays(12);
        LocalDate lowSugarDay = today.minusDays(16);
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            double effect = Math.max(0, Math.min(1, (d.toEpochDay() - cardiologyDay.toEpochDay()) / 7.0));
            double sys = 142 - 13 * effect;
            double dia = 87 - 8 * effect;

            LocalDateTime morning = d.atTime(8, 25 + random.nextInt(20));
            boolean spike = random.nextInt(100) < 4;
            reading(orgId, rota, p, VitalKind.BP, morning, sys + gauss(6) + (spike ? 14 : 0), dia + gauss(4) + (spike ? 6 : 0),
                spike ? "E shqetësuar në mëngjes, e mata sërish pas 10 minutash." : null);
            reading(orgId, rota, p, VitalKind.PULSE, morning.plusMinutes(1), 74 + gauss(5), null, null);

            double sugar = d.equals(lowSugarDay) ? 68 : 132 + gauss(14);
            String sugarNote = d.equals(lowSugarDay) ? "Pak e dobët. I dhamë lëng portokalli dhe mëngjes menjëherë." : null;
            if (d.equals(today.minusDays(25))) {
                sugar = 191;
                sugarNote = "Dje në mbrëmje hëngri ëmbëlsirë te fqinjët.";
            }
            reading(orgId, rota, p, VitalKind.SUGAR, d.atTime(7, 45 + random.nextInt(10)), sugar, null, sugarNote);

            LocalDateTime evening = d.atTime(19, 30 + random.nextInt(25));
            reading(orgId, rota, p, VitalKind.BP, evening, sys - 4 + gauss(6), dia - 2 + gauss(4), null);

            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                reading(orgId, rota, p, VitalKind.WEIGHT, d.atTime(8, 50), 64.9 - 0.02 * (d.toEpochDay() - first.toEpochDay())
                    + gauss(0.15), null, null);
            }
            if (d.getDayOfWeek() == DayOfWeek.WEDNESDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY) {
                reading(orgId, rota, p, VitalKind.SPO2, d.atTime(9, 5), 96 + random.nextInt(2), null, null);
                reading(orgId, rota, p, VitalKind.TEMP, d.atTime(9, 6), 36.5 + random.nextInt(4) / 10.0, null, null);
            }
            if (d.equals(feverDay)) {
                reading(orgId, rota, p, VitalKind.TEMP, d.atTime(19, 10), 37.6, null, "Pak temperaturë, i dhamë Paracetamol.");
                reading(orgId, rota, p, VitalKind.TEMP, d.plusDays(1).atTime(7, 40), 37.1, null, null);
            }
        }
    }

    private void reading(Long orgId, List<Shift> rota, People p, VitalKind kind, LocalDateTime at, double v1, Double v2, String note) {
        if (!at.isBefore(now.minusMinutes(10))) {
            return;
        }
        int scale = kind == VitalKind.TEMP || kind == VitalKind.WEIGHT ? 1 : 0;
        BigDecimal first = BigDecimal.valueOf(v1).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal second = v2 == null ? null : BigDecimal.valueOf(v2).setScale(0, RoundingMode.HALF_UP);
        vitals.save(new Vital(orgId, kind, first, second, at.atZone(zone).toInstant(), note, onDuty(rota, at, p).getId(), null));
    }

    // ---------------------------------------------------------------- meals

    private void meals(Long orgId, People p, List<Shift> rota, LocalDate first) {
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            meal(orgId, rota, p, d, MealSlot.BREAKFAST, d.atTime(9, 5), amount(55, 30, 12));
            meal(orgId, rota, p, d, MealSlot.LUNCH, d.atTime(13, 40), amount(45, 35, 15));
            meal(orgId, rota, p, d, MealSlot.DINNER, d.atTime(19, 45), amount(42, 38, 15));
            drink(orgId, rota, p, d, d.atTime(10, 30), 2);
            drink(orgId, rota, p, d, d.atTime(15, 30), 2);
            drink(orgId, rota, p, d, d.atTime(20, 30), 1 + random.nextInt(2));
        }
    }

    private MealAmount amount(int all, int half, int little) {
        int r = random.nextInt(100);
        if (r < all) {
            return MealAmount.ALL;
        }
        if (r < all + half) {
            return MealAmount.HALF;
        }
        return r < all + half + little ? MealAmount.LITTLE : MealAmount.NONE;
    }

    private void meal(Long orgId, List<Shift> rota, People p, LocalDate day, MealSlot slot, LocalDateTime at, MealAmount amount) {
        if (at.isBefore(now.minusMinutes(10))) {
            String note = MEAL_NOTES[random.nextInt(MEAL_NOTES.length)];
            meals.save(new Meal(orgId, day, slot, amount, 0, note, onDuty(rota, at, p).getId(), at.atZone(zone).toInstant(), null));
        }
    }

    private void drink(Long orgId, List<Shift> rota, People p, LocalDate day, LocalDateTime at, int glasses) {
        if (at.isBefore(now.minusMinutes(10))) {
            meals.save(new Meal(orgId, day, MealSlot.DRINK, null, glasses, null, onDuty(rota, at, p).getId(),
                at.atZone(zone).toInstant(), null));
        }
    }

    // ---------------------------------------------------------------- journal

    private void journal(Long orgId, People p, List<Shift> rota, LocalDate first) {
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            if (random.nextInt(10) < 7) {
                int r = random.nextInt(100);
                int mood = r < 10 ? 5 : r < 55 ? 4 : r < 90 ? 3 : 2;
                String note = mood >= 3 ? MOOD_NOTES[random.nextInt(MOOD_NOTES.length)] : "E heshtur sot, nuk kishte shumë qejf të fliste.";
                entry(orgId, rota, p, JournalKind.MOOD, mood, note, d.atTime(11, 5));
            }
            if (d.toEpochDay() % 3 == 0) {
                entry(orgId, rota, p, JournalKind.PAIN, 2 + random.nextInt(2), "Gjuri i majtë.", d.atTime(17, 30));
            }
            if (d.toEpochDay() % 4 == 1) {
                int sleep = 3 + random.nextInt(3);
                entry(orgId, rota, p, JournalKind.SLEEP, sleep,
                    sleep >= 4 ? "Fjeti mirë, u zgjua vetëm një herë natën." : "U zgjua disa herë, tha që i dhembnin këmbët.",
                    d.atTime(8, 15));
            }
            if (d.toEpochDay() % 5 == 2) {
                entry(orgId, rota, p, JournalKind.TOILET, null, "Normale.", d.atTime(10, 10));
            }
        }
        Object[][] notes = {
            {34, 16, 20, JournalKind.NOTE, null, "Bëmë një shëtitje të shkurtër në oborr, rreth 15 minuta. U gëzua që doli."},
            {31, 18, 50, JournalKind.NOTE, null, "Folëm me Elirën në video. Pyeti për nipërit dhe për shkollën e tyre."},
            {28, 12, 10, JournalKind.NOTE, null, "Erdhi motra e saj, Zana, për kafe. Qeshën shumë."},
            {26, 20, 5, JournalKind.MOOD, 2, "E mërzitur. Foli për babain, sot bën tre vjet që ka ndërruar jetë."},
            {23, 15, 30, JournalKind.NOTE, null, "Këmbët pak të fryra pasdite. I vendosëm lart në jastëk, pas një ore më mirë."},
            {21, 11, 0, JournalKind.NOTE, null, "Erdhi Elira nga Milano për fundjavë. Drita shumë e lumtur."},
            {17, 18, 0, JournalKind.PAIN, 4, "Dhimbje e fortë në gjurin e majtë pas shëtitjes deri te kisha."},
            {13, 9, 40, JournalKind.NOTE, null, "U ankua për marramendje kur u ngrit shpejt nga shtrati. Pas pak iu kalua."},
            {11, 10, 15, JournalKind.INCIDENT, null,
                "Rrëshqiti lehtë në banjo, nuk ra. E mbajta dhe u ul. Nuk ka mavijosje. Vendosëm tapetin kundër rrëshqitjes."},
            {8, 16, 45, JournalKind.NOTE, null, "Pastruam dhomën dhe ndërruam çarçafët. Hapëm dritaret, kishte diell."},
            {5, 19, 15, JournalKind.NOTE, null, "Shikuam së bashku fotot e vjetra të familjes. Tregoi për dasmën e saj."},
            {3, 12, 20, JournalKind.NOTE, null, "Farmacia afër nuk kishte Metformin 500, e mora te farmacia te Sheshi Demokracia."},
            {1, 17, 50, JournalKind.PAIN, 3, "Gjuri i djathtë pas shëtitjes, i dhashë Paracetamol."},
            {0, 10, 40, JournalKind.NOTE, null, "Mëngjesi i qetë. Hëngri gjithë mëngjesin dhe kërkoi çaj mali."},
        };
        for (Object[] n : notes) {
            LocalDateTime at = today.minusDays((Integer) n[0]).atTime((Integer) n[1], (Integer) n[2]);
            entry(orgId, rota, p, (JournalKind) n[3], (Integer) n[4], (String) n[5], at);
        }
    }

    private void entry(Long orgId, List<Shift> rota, People p, JournalKind kind, Integer score, String text, LocalDateTime at) {
        if (at.isBefore(now.minusMinutes(10))) {
            journal.save(new JournalEntry(orgId, kind, score, text, null, onDuty(rota, at, p).getId(), at.atZone(zone).toInstant(),
                null));
        }
    }

    // ---------------------------------------------------------------- visits and supplies

    private void visits(Long orgId, People p, LocalDate cardiologyDay) {
        DoctorVisit cardiology = new DoctorVisit(orgId, p.gent().getId());
        cardiology.setVisitDate(cardiologyDay);
        cardiology.setDoctorName("Dr. Arben Muça");
        cardiology.setSpecialty("Kardiolog");
        cardiology.setPlace("Spitali Rajonal Shkodër");
        cardiology.setNotes("Tensioni në mëngjes rreth 145/88. Amlodipina rritet nga 2.5 mg në 5 mg. Ibuprofeni ndërpritet, "
            + "për dhimbjet vetëm Paracetamol. Të matet tensioni dy herë në ditë dhe të shënohet. Kontroll pas katër javësh me EKG.");
        cardiology.setNextDate(today.plusDays(10));
        cardiology.setNextTime(LocalTime.of(10, 30));
        cardiology.setPrescriptionFileId(files.store(orgId, prescriptionImage(), "image/png", "receta-kardiologu.png").getId());
        cardiology.setCreatedAt(instant(cardiologyDay, 11, 55));
        visits.save(cardiology);

        DoctorVisit family = new DoctorVisit(orgId, p.mira().getId());
        family.setVisitDate(today.minusDays(30));
        family.setDoctorName("Dr. Lindita Gjoni");
        family.setSpecialty("Mjeke familjeje");
        family.setPlace("Qendra Shëndetësore Nr. 2");
        family.setNotes("Kontroll rutinë. HbA1c 7,1%. Metformina vazhdon si më parë. Analizat e gjakut përsëri pas tre muajsh.");
        family.setCreatedAt(instant(today.minusDays(30), 12, 40));
        visits.save(family);

        DoctorVisit eyes = new DoctorVisit(orgId, p.gent().getId());
        eyes.setVisitDate(today.minusDays(62));
        eyes.setDoctorName("Dr. Ermal Hoxha");
        eyes.setSpecialty("Okulist");
        eyes.setPlace("Klinika Syri, Shkodër");
        eyes.setNotes("Katarakt i lehtë në syrin e majtë. Syze të reja për lexim. Kontroll pas gjashtë muajsh.");
        eyes.setNextDate(today.plusDays(120));
        eyes.setCreatedAt(instant(today.minusDays(62), 17, 20));
        visits.save(eyes);
    }

    private void supplies(Long orgId, People p) {
        supply(orgId, p.mira(), "Pelena për të rritur (L)", SupplyStatus.RUNNING_LOW, instant(today.minusDays(2), 15, 40));
        supply(orgId, p.gent(), "Shirita për glukometër", SupplyStatus.OK, instant(today.minusDays(9), 18, 50));
        supply(orgId, p.gent(), "Doreza njëpërdorimshe", SupplyStatus.OK, instant(today.minusDays(10), 19, 5));
        supply(orgId, p.mira(), "Lecka të lagura", SupplyStatus.OK, instant(today.minusDays(6), 12, 30));
        supply(orgId, p.mira(), "Krem kundër skuqjes", SupplyStatus.OK, instant(today.minusDays(14), 9, 30));
        supply(orgId, p.gent(), "Çaj mali", SupplyStatus.OK, instant(today.minusDays(4), 20, 10));
    }

    private void supply(Long orgId, User by, String name, SupplyStatus status, Instant at) {
        Supply supply = supplies.save(new Supply(orgId, name, status, by.getId(), at));
        if (status != SupplyStatus.OK) {
            supplyEvents.save(new SupplyEvent(orgId, supply.getId(), SupplyStatus.OK, by.getId(), at.minusSeconds(86400L * 20)));
        }
        supplyEvents.save(new SupplyEvent(orgId, supply.getId(), status, by.getId(), at));
    }

    // ---------------------------------------------------------------- messages

    /** The WhatsApp outbox as it would look after five weeks: summaries, a supply alert, a late dose. */
    private void messages(Long orgId, People p, Medicines meds, List<Shift> rota) {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int weeksAgo = 4; weeksAgo >= 1; weeksAgo--) {
            LocalDate monday = thisMonday.minusWeeks(weeksAgo);
            WeeklySummary sent = summaries.send(orgId, monday, WeeklySummary.Source.SCHEDULED);
            Instant sunday = instant(monday.plusDays(6), 19, random.nextInt(9));
            entityManager.flush();
            jdbc.update("UPDATE weekly_summaries SET generated_at = ? WHERE id = ?", Timestamp.from(sunday), sent.getId());
            jdbc.update("UPDATE outbound_messages SET created_at = ?, sent_at = ? WHERE id = ?", Timestamp.from(sunday),
                Timestamp.from(sunday), sent.getMessageId());
        }
        notifier.supplyLow(orgId, null, "Pelena për të rritur (L)", false, p.mira());
        backdateLastMessage(orgId, instant(today.minusDays(2), 15, 41));
        notifier.doseMissed(orgId, meds.atorvastatin().getId(), meds.atorvastatin().label(), LocalTime.of(21, 0));
        backdateLastMessage(orgId, instant(today.minusDays(6), 23, 5));
    }

    private void backdateLastMessage(Long orgId, Instant at) {
        entityManager.flush();
        OutboundMessage last = outbox.findTopByOrganizationIdOrderByIdDesc(orgId).orElseThrow();
        jdbc.update("UPDATE outbound_messages SET created_at = ?, sent_at = ? WHERE id = ?", Timestamp.from(at),
            Timestamp.from(at), last.getId());
    }

    // ---------------------------------------------------------------- helpers

    /** Whoever the rota has at the home at that moment; Mira covers the gaps. */
    private User onDuty(List<Shift> rota, LocalDateTime at, People p) {
        return rota.stream()
            .filter(s -> s.covers(at))
            .findFirst()
            .map(s -> s.getUserId().equals(p.gent().getId()) ? p.gent() : s.getUserId().equals(p.elira().getId()) ? p.elira() : p.mira())
            .orElse(p.mira());
    }

    private Instant instant(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(zone).toInstant();
    }

    private double gauss(double sd) {
        return random.nextGaussian() * sd;
    }

    /** A photographed prescription, drawn rather than shipped: paper, letterhead, lines, stamp, signature. */
    private static byte[] prescriptionImage() {
        BufferedImage img = new BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE9E4DA));
        g.fillRect(0, 0, 900, 1200);
        g.setColor(new Color(0xFDFCF8));
        g.fillRoundRect(60, 50, 780, 1100, 18, 18);
        g.setColor(new Color(0x2F6F8F));
        g.fillRect(60, 50, 780, 120);
        g.setColor(new Color(0xFFFFFF));
        g.fillRoundRect(110, 85, 260, 18, 9, 9);
        g.fillRoundRect(110, 118, 180, 12, 6, 6);
        g.setColor(new Color(0x9AA7B0));
        for (int y = 250; y < 820; y += 58) {
            g.fillRoundRect(120, y, 560 - (y % 3) * 60, 12, 6, 6);
        }
        g.setColor(new Color(0x1E3A5F));
        g.setStroke(new BasicStroke(5f));
        g.draw(new CubicCurve2D.Double(480, 980, 540, 900, 600, 1060, 700, 950));
        g.setColor(new Color(0x3C5A99));
        g.setStroke(new BasicStroke(6f));
        g.drawOval(170, 900, 170, 170);
        g.drawOval(195, 925, 120, 120);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
