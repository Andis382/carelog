package io.github.andis382.carelog.supplies;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A status change, kept for the log ("Mira: diapers running low"). */
@Entity
@Table(name = "supply_events")
public class SupplyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "supply_id", nullable = false)
    private Long supplyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplyStatus status;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected SupplyEvent() {}

    public SupplyEvent(Long organizationId, Long supplyId, SupplyStatus status, Long recordedBy, Instant recordedAt) {
        this.organizationId = organizationId;
        this.supplyId = supplyId;
        this.status = status;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getSupplyId() { return supplyId; }
    public SupplyStatus getStatus() { return status; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}
