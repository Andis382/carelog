package io.github.andis382.carelog.daily;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {

    List<Meal> findByOrganizationIdAndMealDateBetweenOrderByRecordedAtAsc(Long organizationId, LocalDate from, LocalDate to);

    List<Meal> findByOrganizationIdAndRecordedAtBetween(Long organizationId, Instant from, Instant to);

    Optional<Meal> findByOrganizationIdAndClientId(Long organizationId, String clientId);
}
