package io.github.andis382.carelog.vitals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalRepository extends JpaRepository<Vital, Long> {

    List<Vital> findByOrganizationIdAndKindAndMeasuredAtGreaterThanEqualOrderByMeasuredAtAsc(Long organizationId, VitalKind kind,
                                                                                           Instant from);

    List<Vital> findByOrganizationIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(Long organizationId, Instant from, Instant to);

    Optional<Vital> findTopByOrganizationIdAndKindOrderByMeasuredAtDesc(Long organizationId, VitalKind kind);

    Optional<Vital> findByOrganizationIdAndClientId(Long organizationId, String clientId);
}
