package io.github.andis382.carelog.visits;

import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.files.FileStorage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Doctor visits. A visit's next appointment stays visible whatever the plan's history limit. */
@Service
public class VisitService {

    private final DoctorVisitRepository visits;
    private final CircleService circle;
    private final CircleTime time;
    private final FileStorage files;

    public VisitService(DoctorVisitRepository visits, CircleService circle, CircleTime time, FileStorage files) {
        this.visits = visits;
        this.circle = circle;
        this.time = time;
        this.files = files;
    }

    public record VisitCommand(LocalDate date, String doctorName, String specialty, String place, String notes,
                               LocalDate nextDate, LocalTime nextTime, String prescriptionFileId) {}

    public List<DoctorVisit> list(Long orgId) {
        LocalDate visible = circle.visibleFrom(orgId);
        LocalDate today = time.today(orgId);
        return visits.findByOrganizationIdOrderByVisitDateDescIdDesc(orgId).stream()
            .filter(v -> visible == null || !v.getVisitDate().isBefore(visible)
                || (v.getNextDate() != null && !v.getNextDate().isBefore(today)))
            .toList();
    }

    public Optional<DoctorVisit> nextAppointment(Long orgId) {
        return visits.findTopByOrganizationIdAndNextDateGreaterThanEqualOrderByNextDateAscNextTimeAsc(orgId, time.today(orgId));
    }

    @Transactional
    public DoctorVisit create(Long orgId, Long userId, VisitCommand cmd) {
        DoctorVisit visit = new DoctorVisit(orgId, userId);
        apply(orgId, visit, cmd);
        return visits.save(visit);
    }

    /** The person who wrote it, or a planner, may correct it. */
    @Transactional
    public DoctorVisit update(Long orgId, Long userId, Role role, Long id, VisitCommand cmd) {
        DoctorVisit visit = find(orgId, id);
        if (!visit.getRecordedBy().equals(userId) && !role.canPlan()) {
            throw ApiException.forbidden();
        }
        apply(orgId, visit, cmd);
        return visit;
    }

    @Transactional
    public void delete(Long orgId, Long userId, Role role, Long id) {
        DoctorVisit visit = find(orgId, id);
        if (!visit.getRecordedBy().equals(userId) && role != Role.OWNER) {
            throw ApiException.forbidden();
        }
        visits.delete(visit);
    }

    private DoctorVisit find(Long orgId, Long id) {
        return visits.findByIdAndOrganizationId(id, orgId).orElseThrow(ApiException::notFound);
    }

    private void apply(Long orgId, DoctorVisit visit, VisitCommand cmd) {
        if (cmd.date().isAfter(time.today(orgId))) {
            throw ApiException.field("date", "visit.future");
        }
        if (cmd.nextDate() != null && cmd.nextDate().isBefore(cmd.date())) {
            throw ApiException.field("nextDate", "visit.next_before_visit");
        }
        visit.setVisitDate(cmd.date());
        visit.setDoctorName(cmd.doctorName().trim());
        visit.setSpecialty(trim(cmd.specialty()));
        visit.setPlace(trim(cmd.place()));
        visit.setNotes(trim(cmd.notes()));
        visit.setNextDate(cmd.nextDate());
        visit.setNextTime(cmd.nextDate() == null ? null : cmd.nextTime());
        visit.setPrescriptionFileId(files.owned(orgId, cmd.prescriptionFileId(), "prescriptionFileId"));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
