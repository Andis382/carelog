package io.github.andis382.carelog.meds;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findByOrganizationIdOrderByNameAsc(Long organizationId);

    List<Medication> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);

    Optional<Medication> findByIdAndOrganizationId(Long id, Long organizationId);
}
