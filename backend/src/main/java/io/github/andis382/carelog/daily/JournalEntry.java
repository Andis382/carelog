package io.github.andis382.carelog.daily;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A note, a mood or pain score, sleep, toilet, or an incident, optionally with a photo. */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalKind kind;

    private Integer score;

    private String body;

    @Column(name = "photo_file_id")
    private String photoFileId;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected JournalEntry() {}

    public JournalEntry(Long organizationId, JournalKind kind, Integer score, String body, String photoFileId, Long recordedBy,
                        Instant recordedAt, String clientId) {
        this.organizationId = organizationId;
        this.kind = kind;
        this.score = score;
        this.body = body;
        this.photoFileId = photoFileId;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public JournalKind getKind() { return kind; }
    public Integer getScore() { return score; }
    public String getBody() { return body; }
    public String getPhotoFileId() { return photoFileId; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getClientId() { return clientId; }
}
