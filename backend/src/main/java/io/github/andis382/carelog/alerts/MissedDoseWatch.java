package io.github.andis382.carelog.alerts;

import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.meds.DoseEventRepository;
import io.github.andis382.carelog.meds.DoseSchedule;
import io.github.andis382.carelog.meds.DoseSlot;
import io.github.andis382.carelog.meds.DoseState;
import io.github.andis382.carelog.meds.DoseTimeline;
import io.github.andis382.carelog.meds.MedicationRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The optional "dose over two hours late" alert. It only looks back a few hours, so switching
 * alerts on does not replay old history to the coordinator's phone.
 */
@Service
public class MissedDoseWatch {

    private static final Duration LOOK_BACK = Duration.ofHours(6);

    private final MedicationRepository medications;
    private final DoseEventRepository doses;
    private final DoseAlertRepository alerts;
    private final CircleService circle;
    private final CircleTime time;
    private final CircleNotifier notifier;

    public MissedDoseWatch(MedicationRepository medications, DoseEventRepository doses, DoseAlertRepository alerts,
                           CircleService circle, CircleTime time, CircleNotifier notifier) {
        this.medications = medications;
        this.doses = doses;
        this.alerts = alerts;
        this.circle = circle;
        this.time = time;
        this.notifier = notifier;
    }

    /** Returns how many late doses were reported. */
    @Transactional
    public int check(Long orgId) {
        ZoneId zone = time.zone(orgId);
        LocalDateTime now = time.localNow(orgId);
        LocalDate today = now.toLocalDate();
        List<DoseSlot> slots = DoseSchedule.forDays(medications.findByOrganizationIdOrderByNameAsc(orgId), today.minusDays(1), today, zone)
            .stream()
            .filter(s -> s.at().isAfter(now.minus(LOOK_BACK)))
            .toList();
        List<DoseTimeline.Line> lines = DoseTimeline.build(slots,
            doses.findByOrganizationIdAndDoseDateBetween(orgId, today.minusDays(1), today), now, circle.settings(orgId).grace(), zone);
        int reported = 0;
        for (DoseTimeline.Line line : lines) {
            DoseSlot slot = line.slot();
            if (line.state() != DoseState.MISSED
                || alerts.existsByMedicationIdAndDoseDateAndScheduledTime(slot.medication().getId(), slot.date(), slot.time())) {
                continue;
            }
            notifier.doseMissed(orgId, slot.medication().getId(), slot.medication().label(), slot.time());
            alerts.save(new DoseAlert(orgId, slot.medication().getId(), slot.date(), slot.time(), time.now()));
            reported++;
        }
        return reported;
    }
}
