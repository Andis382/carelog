package io.github.andis382.carelog.summary;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySummaryRepository extends JpaRepository<WeeklySummary, Long> {

    List<WeeklySummary> findTop12ByOrganizationIdOrderByGeneratedAtDesc(Long organizationId);

    boolean existsByOrganizationIdAndWeekStartAndSource(Long organizationId, LocalDate weekStart, WeeklySummary.Source source);
}
