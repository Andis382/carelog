package io.github.andis382.carelog.visits;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.files.UploadController;
import io.github.andis382.carelog.visits.VisitService.VisitCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visits;
    private final CircleService circle;
    private final CurrentUser currentUser;

    public VisitController(VisitService visits, CircleService circle, CurrentUser currentUser) {
        this.visits = visits;
        this.circle = circle;
        this.currentUser = currentUser;
    }

    public record VisitRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 160) String doctorName,
        @Size(max = 120) String specialty,
        @Size(max = 160) String place,
        @Size(max = 4000) String notes,
        LocalDate nextDate,
        LocalTime nextTime,
        @Size(max = 36) String prescriptionFileId) {

        VisitCommand command() {
            return new VisitCommand(date, doctorName, specialty, place, notes, nextDate, nextTime, prescriptionFileId);
        }
    }

    public record VisitView(Long id, LocalDate date, String doctorName, String specialty, String place, String notes,
                            LocalDate nextDate, String nextTime, String prescriptionFileId, String prescriptionUrl,
                            Long recordedById, String recordedBy, Instant createdAt) {
        public static VisitView of(DoctorVisit v, Map<Long, String> names) {
            return new VisitView(v.getId(), v.getVisitDate(), v.getDoctorName(), v.getSpecialty(), v.getPlace(), v.getNotes(),
                v.getNextDate(), v.getNextTime() == null ? null : DateTimeFormatter.ofPattern("HH:mm").format(v.getNextTime()),
                v.getPrescriptionFileId(), UploadController.fileUrl(v.getPrescriptionFileId()), v.getRecordedBy(),
                names.get(v.getRecordedBy()), v.getCreatedAt());
        }
    }

    @GetMapping
    public List<VisitView> list() {
        Long orgId = currentUser.organizationId();
        Map<Long, String> names = circle.names(orgId);
        return visits.list(orgId).stream().map(v -> VisitView.of(v, names)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitView create(@Valid @RequestBody VisitRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return VisitView.of(visits.create(orgId, currentUser.id(), req.command()), circle.names(orgId));
    }

    @PutMapping("/{id}")
    public VisitView update(@PathVariable Long id, @Valid @RequestBody VisitRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return VisitView.of(visits.update(orgId, currentUser.id(), currentUser.role(), id, req.command()), circle.names(orgId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        currentUser.requireRecorder();
        visits.delete(currentUser.organizationId(), currentUser.id(), currentUser.role(), id);
    }
}
