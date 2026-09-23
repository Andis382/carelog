package io.github.andis382.carelog.rota;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftSwapRepository extends JpaRepository<ShiftSwap, Long> {

    Optional<ShiftSwap> findByIdAndOrganizationId(Long id, Long organizationId);

    List<ShiftSwap> findByOrganizationIdAndStatus(Long organizationId, ShiftSwap.Status status);

    List<ShiftSwap> findByShiftIdIn(Collection<Long> shiftIds);

    boolean existsByShiftIdAndStatus(Long shiftId, ShiftSwap.Status status);

    List<ShiftSwap> findByOrganizationIdAndCreatedAtAfterOrderByCreatedAtDesc(Long organizationId, Instant after);

    List<ShiftSwap> findByOrganizationIdAndRespondedAtBetween(Long organizationId, Instant from, Instant to);
}
