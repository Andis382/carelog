package io.github.andis382.carelog.rota;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findByUserIdAndCheckedOutAtIsNull(Long userId);

    Optional<CheckIn> findByOrganizationIdAndClientId(Long organizationId, String clientId);

    Optional<CheckIn> findByOrganizationIdAndOutClientId(Long organizationId, String outClientId);

    List<CheckIn> findByOrganizationIdAndCheckedInAtBetweenOrderByCheckedInAtAsc(Long organizationId, Instant from, Instant to);

    List<CheckIn> findByOrganizationIdAndCheckedOutAtBetween(Long organizationId, Instant from, Instant to);
}
