package io.github.andis382.carelog.meds;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoseEventRepository extends JpaRepository<DoseEvent, Long> {

    List<DoseEvent> findByOrganizationIdAndDoseDateBetween(Long organizationId, LocalDate from, LocalDate to);

    List<DoseEvent> findByMedicationIdAndDoseDateBetween(Long medicationId, LocalDate from, LocalDate to);

    List<DoseEvent> findByOrganizationIdAndRecordedAtBetween(Long organizationId, Instant from, Instant to);

    Optional<DoseEvent> findByMedicationIdAndDoseDateAndScheduledTime(Long medicationId, LocalDate doseDate, LocalTime scheduledTime);

    Optional<DoseEvent> findByOrganizationIdAndClientId(Long organizationId, String clientId);

    Optional<DoseEvent> findByIdAndOrganizationId(Long id, Long organizationId);

    /** The last "as needed" dose, shown before anyone gives another. */
    Optional<DoseEvent> findTopByMedicationIdAndScheduledTimeIsNullAndStatusOrderByRecordedAtDesc(Long medicationId, DoseStatus status);
}
