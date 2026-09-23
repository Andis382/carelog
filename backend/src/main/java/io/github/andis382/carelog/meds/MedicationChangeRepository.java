package io.github.andis382.carelog.meds;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationChangeRepository extends JpaRepository<MedicationChange, Long> {

    List<MedicationChange> findByMedicationIdOrderByChangedAtDesc(Long medicationId);

    List<MedicationChange> findByOrganizationIdAndChangedAtBetween(Long organizationId, Instant from, Instant to);
}
