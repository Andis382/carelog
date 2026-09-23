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

/** The circle's own "usual range" for one kind of reading. Shown next to values, never acted on. */
@Entity
@Table(name = "vital_ranges")
public class VitalRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VitalKind kind;

    private BigDecimal low;
    private BigDecimal high;
    private BigDecimal low2;
    private BigDecimal high2;

    protected VitalRange() {}

    public VitalRange(Long organizationId, VitalKind kind) {
        this.organizationId = organizationId;
        this.kind = kind;
        this.low = kind.defaultLow();
        this.high = kind.defaultHigh();
        this.low2 = kind.defaultLow2();
        this.high2 = kind.defaultHigh2();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public VitalKind getKind() { return kind; }
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public BigDecimal getLow2() { return low2; }
    public void setLow2(BigDecimal low2) { this.low2 = low2; }
    public BigDecimal getHigh2() { return high2; }
    public void setHigh2(BigDecimal high2) { this.high2 = high2; }
}
