package io.github.andis382.carelog.visits;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorVisitRepository extends JpaRepository<DoctorVisit, Long> {

    List<DoctorVisit> findByOrganizationIdOrderByVisitDateDescIdDesc(Long organizationId);

    List<DoctorVisit> findByOrganizationIdAndVisitDateBetweenOrderByVisitDateAsc(Long organizationId, LocalDate from, LocalDate to);

    Optional<DoctorVisit> findTopByOrganizationIdAndNextDateGreaterThanEqualOrderByNextDateAscNextTimeAsc(Long organizationId,
                                                                                                         LocalDate from);

    Optional<DoctorVisit> findByIdAndOrganizationId(Long id, Long organizationId);
}
