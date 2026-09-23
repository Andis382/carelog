package io.github.andis382.carelog.summary;

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

/** A weekly summary as it was sent: to whom, when, and the exact text. */
@Entity
@Table(name = "weekly_summaries")
public class WeeklySummary {

    public enum Source { SCHEDULED, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Column(name = "sent_to_user_id")
    private Long sentToUserId;

    @Column(name = "sent_to_name")
    private String sentToName;

    @Column(name = "adherence_pct")
    private Integer adherencePct;

    @Column(nullable = false)
    private String content;

    @Column(name = "message_id")
    private Long messageId;

    protected WeeklySummary() {}

    public WeeklySummary(Long organizationId, LocalDate weekStart, Instant generatedAt, Source source, Long sentToUserId,
                         String sentToName, Integer adherencePct, String content, Long messageId) {
        this.organizationId = organizationId;
        this.weekStart = weekStart;
        this.generatedAt = generatedAt;
        this.source = source;
        this.sentToUserId = sentToUserId;
        this.sentToName = sentToName;
        this.adherencePct = adherencePct;
        this.content = content;
        this.messageId = messageId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public LocalDate getWeekStart() { return weekStart; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Source getSource() { return source; }
    public Long getSentToUserId() { return sentToUserId; }
    public String getSentToName() { return sentToName; }
    public Integer getAdherencePct() { return adherencePct; }
    public String getContent() { return content; }
    public Long getMessageId() { return messageId; }
}
