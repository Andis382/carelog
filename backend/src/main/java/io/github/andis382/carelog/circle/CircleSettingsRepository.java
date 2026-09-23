package io.github.andis382.carelog.circle;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleSettingsRepository extends JpaRepository<CircleSettings, Long> {

    List<CircleSettings> findByWeeklySummaryTrue();

    List<CircleSettings> findByDoseAlertsTrue();
}
