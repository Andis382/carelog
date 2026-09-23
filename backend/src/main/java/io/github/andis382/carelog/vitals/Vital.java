package io.github.andis382.carelog.vitals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One reading. Blood pressure uses both values (systolic, diastolic); the rest only the first. */
@Entity
@Table(name = "vitals")
public class Vital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VitalKind kind;

    @Column(nullable = false)
    private BigDecimal value1;

    private BigDecimal value2;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    private String note;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Vital() {}

    public Vital(Long organizationId, VitalKind kind, BigDecimal value1, BigDecimal value2, Instant measuredAt, String note,
                 Long recordedBy, String clientId) {
        this.organizationId = organizationId;
        this.kind = kind;
        this.value1 = value1;
        this.value2 = value2;
        this.measuredAt = measuredAt;
        this.note = note;
        this.recordedBy = recordedBy;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public VitalKind getKind() { return kind; }
    public BigDecimal getValue1() { return value1; }
    public BigDecimal getValue2() { return value2; }
    public Instant getMeasuredAt() { return measuredAt; }
    public String getNote() { return note; }
    public Long getRecordedBy() { return recordedBy; }
    public String getClientId() { return clientId; }
    public Instant getCreatedAt() { return createdAt; }
}
