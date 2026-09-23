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

/** Something the home keeps in stock: diapers, test strips, gloves. */
@Entity
@Table(name = "supplies")
public class Supply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplyStatus status = SupplyStatus.OK;

    private String note;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Supply() {}

    public Supply(Long organizationId, String name, SupplyStatus status, Long updatedBy, Instant updatedAt) {
        this.organizationId = organizationId;
        this.name = name;
        this.status = status;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void mark(SupplyStatus status, Long by, Instant at) {
        this.status = status;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public SupplyStatus getStatus() { return status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
