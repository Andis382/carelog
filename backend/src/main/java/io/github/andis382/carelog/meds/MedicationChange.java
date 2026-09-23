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

/**
 * One line in a medicine's history: started, changed (with the fields that changed) or
 * stopped. Field names are stored, not sentences, so each reader sees them in their language.
 */
@Entity
@Table(name = "medication_changes")
public class MedicationChange {

    public enum Kind { STARTED, CHANGED, STOPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "changes_json")
    private String changesJson;

    private String note;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected MedicationChange() {}

    public MedicationChange(Long organizationId, Long medicationId, Kind kind, String changesJson, String note,
                            Long changedBy, Instant changedAt) {
        this.organizationId = organizationId;
        this.medicationId = medicationId;
        this.kind = kind;
        this.changesJson = changesJson;
        this.note = note;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getMedicationId() { return medicationId; }
    public Kind getKind() { return kind; }
    public String getChangesJson() { return changesJson; }
    public String getNote() { return note; }
    public Long getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
