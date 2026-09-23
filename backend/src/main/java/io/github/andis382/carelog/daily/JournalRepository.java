package io.github.andis382.carelog.daily;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByOrganizationIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long organizationId, Instant from, Instant to);

    Optional<JournalEntry> findByOrganizationIdAndClientId(Long organizationId, String clientId);
}
