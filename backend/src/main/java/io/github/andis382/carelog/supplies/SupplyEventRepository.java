package io.github.andis382.carelog.supplies;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyEventRepository extends JpaRepository<SupplyEvent, Long> {

    List<SupplyEvent> findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long organizationId, Instant from, Instant to);
}
