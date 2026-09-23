package io.github.andis382.carelog.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/** One row per circle: the plan, who pays, and how the circle wants to be alerted. */
@Entity
@Table(name = "circle_settings")
public class CircleSettings {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    /** The member who receives the weekly summary. */
    @Column(name = "payer_user_id")
    private Long payerUserId;

    /** Minutes after the scheduled time before a dose shows as late. */
    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 30;

    /** WhatsApp the coordinator when a dose is more than two hours late. */
    @Column(name = "dose_alerts", nullable = false)
    private boolean doseAlerts;

    /** Send the weekly summary to the payer on Sunday evening. */
    @Column(name = "weekly_summary", nullable = false)
    private boolean weeklySummary = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CircleSettings() {}

    public CircleSettings(Long organizationId, Long payerUserId) {
        this.organizationId = organizationId;
        this.payerUserId = payerUserId;
    }

    public Duration grace() {
        return Duration.ofMinutes(graceMinutes);
    }

    public Long getOrganizationId() { return organizationId; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public Long getPayerUserId() { return payerUserId; }
    public void setPayerUserId(Long payerUserId) { this.payerUserId = payerUserId; }
    public int getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }
    public boolean isDoseAlerts() { return doseAlerts; }
    public void setDoseAlerts(boolean doseAlerts) { this.doseAlerts = doseAlerts; }
    public boolean isWeeklySummary() { return weeklySummary; }
    public void setWeeklySummary(boolean weeklySummary) { this.weeklySummary = weeklySummary; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
