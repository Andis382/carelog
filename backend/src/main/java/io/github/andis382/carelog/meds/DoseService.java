package io.github.andis382.carelog.meds;

import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records doses. The one rule that matters most lives here: a scheduled dose can be
 * recorded once. The second "Given" gets a 409 that says who gave it and when.
 */
@Service
public class DoseService {

    /** A mis-tap can be taken back by the person who made it, for a few minutes. */
    public static final Duration UNDO_WINDOW = Duration.ofMinutes(10);
    /** Doses can be filled in a week back (a forgotten tick, or a phone that was offline). */
    private static final int BACKFILL_DAYS = 7;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DoseEventRepository events;
    private final MedicationRepository medications;
    private final UserRepository users;
    private final CircleTime time;

    public DoseService(DoseEventRepository events, MedicationRepository medications, UserRepository users, CircleTime time) {
        this.events = events;
        this.medications = medications;
        this.users = users;
        this.time = time;
    }

    public record DoseCommand(Long medicationId, LocalDate date, LocalTime time, DoseStatus status, String note,
                              String clientId, Instant at) {}

    @Transactional
    public DoseEvent record(Long orgId, Long userId, DoseCommand cmd) {
        Optional<DoseEvent> replay = replayOf(orgId, cmd.clientId());
        if (replay.isPresent()) {
            return replay.get();
        }
        Medication medication = medications.findByIdAndOrganizationId(cmd.medicationId(), orgId)
            .orElseThrow(() -> ApiException.field("medicationId", "error.not_found"));
        LocalDate today = time.today(orgId);
        if (cmd.date().isAfter(today)) {
            throw ApiException.field("date", "dose.future");
        }
        if (cmd.date().isBefore(today.minusDays(BACKFILL_DAYS))) {
            throw ApiException.field("date", "dose.too_old");
        }
        if (cmd.status() != DoseStatus.GIVEN && (cmd.note() == null || cmd.note().isBlank())) {
            throw ApiException.field("note", "dose.reason_required");
        }
        if (cmd.time() == null) {
            if (medication.getFrequency() != Frequency.AS_NEEDED) {
                throw ApiException.field("time", "dose.not_scheduled");
            }
        } else {
            ZoneId zone = time.zone(orgId);
            if (!DoseSchedule.timesOn(medication, cmd.date(), zone).contains(cmd.time())) {
                throw ApiException.field("time", "dose.not_scheduled");
            }
            events.findByMedicationIdAndDoseDateAndScheduledTime(medication.getId(), cmd.date(), cmd.time())
                .ifPresent(existing -> {
                    throw alreadyRecorded(existing, zone);
                });
        }
        String note = cmd.note() == null || cmd.note().isBlank() ? null : cmd.note().trim();
        DoseEvent event = new DoseEvent(orgId, medication.getId(), cmd.date(), cmd.time(), cmd.status(), note, userId,
            time.resolveAt(cmd.at()), cmd.clientId());
        return events.saveAndFlush(event);
    }

    /**
     * Two phones tapped at the same moment and the unique index let one through. Either this
     * was our own retry (same client id), or someone else won and the caller gets the 409.
     */
    @Transactional(readOnly = true)
    public DoseEvent afterRace(Long orgId, DoseCommand cmd) {
        Optional<DoseEvent> replay = replayOf(orgId, cmd.clientId());
        if (replay.isPresent()) {
            return replay.get();
        }
        DoseEvent winner = events.findByMedicationIdAndDoseDateAndScheduledTime(cmd.medicationId(), cmd.date(), cmd.time())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "error.server"));
        throw alreadyRecorded(winner, time.zone(orgId));
    }

    @Transactional
    public void undo(Long orgId, Long userId, Long eventId) {
        DoseEvent event = events.findByIdAndOrganizationId(eventId, orgId).orElseThrow(ApiException::notFound);
        if (!event.getRecordedBy().equals(userId)) {
            throw ApiException.forbidden();
        }
        if (event.getCreatedAt().plus(UNDO_WINDOW).isBefore(time.now())) {
            throw ApiException.conflict("dose.undo_expired");
        }
        events.delete(event);
    }

    public boolean canUndo(DoseEvent event, Long userId) {
        return event.getRecordedBy().equals(userId) && event.getCreatedAt().plus(UNDO_WINDOW).isAfter(time.now());
    }

    private Optional<DoseEvent> replayOf(Long orgId, String clientId) {
        return clientId == null || clientId.isBlank() ? Optional.empty() : events.findByOrganizationIdAndClientId(orgId, clientId);
    }

    /** "Already given by Mira at 08:05", with the details the dialog needs. */
    private ApiException alreadyRecorded(DoseEvent existing, ZoneId zone) {
        User by = users.findById(existing.getRecordedBy()).orElseThrow();
        String at = HH_MM.format(existing.getRecordedAt().atZone(zone));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", existing.getId());
        details.put("status", existing.getStatus().name());
        details.put("recordedBy", by.getName());
        details.put("recordedAt", existing.getRecordedAt().toString());
        details.put("time", at);
        details.put("note", existing.getNote());
        return ApiException.conflict("dose.already." + existing.getStatus().name(), by.firstName(), at).withDetails(details);
    }
}
