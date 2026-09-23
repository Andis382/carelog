package io.github.andis382.carelog.alerts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Marks a late dose as already reported, so the coordinator hears about it once. */
@Entity
@Table(name = "dose_alerts")
public class DoseAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected DoseAlert() {}

    public DoseAlert(Long organizationId, Long medicationId, LocalDate doseDate, LocalTime scheduledTime, Instant sentAt) {
        this.organizationId = organizationId;
        this.medicationId = medicationId;
        this.doseDate = doseDate;
        this.scheduledTime = scheduledTime;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public Long getMedicationId() { return medicationId; }
    public LocalDate getDoseDate() { return doseDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public Instant getSentAt() { return sentAt; }
}
