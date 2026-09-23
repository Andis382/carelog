package io.github.andis382.carelog.meds;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.meds.DoseService.DoseCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doses")
public class DoseController {

    private final DoseService doses;
    private final CircleService circle;
    private final CurrentUser currentUser;

    public DoseController(DoseService doses, CircleService circle, CurrentUser currentUser) {
        this.doses = doses;
        this.circle = circle;
        this.currentUser = currentUser;
    }

    public record DoseRequest(
        @NotNull Long medicationId,
        @NotNull LocalDate date,
        LocalTime time,
        @NotNull DoseStatus status,
        @Size(max = 500) String note,
        @Size(max = 64) String clientId,
        Instant at) {}

    public record DoseView(Long id, Long medicationId, LocalDate date, String time, String status, String note,
                           Long recordedById, String recordedBy, Instant recordedAt, boolean canUndo) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoseView record(@Valid @RequestBody DoseRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        DoseCommand cmd = new DoseCommand(req.medicationId(), req.date(), req.time(), req.status(), req.note(),
            req.clientId(), req.at());
        DoseEvent event;
        try {
            event = doses.record(orgId, currentUser.id(), cmd);
        } catch (DataIntegrityViolationException race) {
            event = doses.afterRace(orgId, cmd);
        }
        return view(event);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable Long id) {
        currentUser.requireRecorder();
        doses.undo(currentUser.organizationId(), currentUser.id(), id);
    }

    private DoseView view(DoseEvent e) {
        String by = circle.names(currentUser.organizationId()).get(e.getRecordedBy());
        return new DoseView(e.getId(), e.getMedicationId(), e.getDoseDate(),
            e.getScheduledTime() == null ? null : TimesConverter.HH_MM.format(e.getScheduledTime()), e.getStatus().name(),
            e.getNote(), e.getRecordedBy(), by, e.getRecordedAt(), doses.canUndo(e, currentUser.id()));
    }
}
