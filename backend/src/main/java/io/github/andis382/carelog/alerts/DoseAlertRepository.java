package io.github.andis382.carelog.alerts;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoseAlertRepository extends JpaRepository<DoseAlert, Long> {

    boolean existsByMedicationIdAndDoseDateAndScheduledTime(Long medicationId, LocalDate doseDate, LocalTime scheduledTime);
}
