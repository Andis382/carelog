package io.github.andis382.carelog.meds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Someone gave, skipped or saw a dose refused. At most one per scheduled dose (a unique index
 * backs the double-dose guard); "as needed" doses have no scheduled time.
 */
@Entity
@Table(name = "dose_events")
public class DoseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoseStatus status;

    private String note;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    /** When it happened (the tap on the phone), not when the server heard about it. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Set by the phone so a retried offline upload is recognised instead of doubled. */
    @Column(name = "client_id")
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DoseEvent() {}

    public DoseEvent(Long organizationId, Long medicationId, LocalDate doseDate, LocalTime scheduledTime, DoseStatus status,
                     String note, Long recordedBy, Instant recordedAt, String clientId) {
        this.organizationId = organizationId;
        this.medicationId = medicationId;
        this.doseDate = doseDate;
        this.scheduledTime = scheduledTime;
        this.status = status;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getMedicationId() { return medicationId; }
    public LocalDate getDoseDate() { return doseDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public DoseStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getClientId() { return clientId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
