package io.github.andis382.carelog.rota;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByOrganizationIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(Long organizationId, LocalDate from,
                                                                                      LocalDate to);

    List<Shift> findByOrganizationIdAndUserIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(Long organizationId, Long userId,
                                                                                               LocalDate from, LocalDate to);

    Optional<Shift> findByIdAndOrganizationId(Long id, Long organizationId);
}
