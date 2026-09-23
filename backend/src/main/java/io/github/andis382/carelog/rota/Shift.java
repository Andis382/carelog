package io.github.andis382.carelog.rota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Someone is on duty from start to end (circle time). An end before the start runs past midnight. */
@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftKind kind;

    private String note;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Shift() {}

    public Shift(Long organizationId, Long userId, LocalDate shiftDate, LocalTime startTime, LocalTime endTime, ShiftKind kind,
                 String note, Long createdBy) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.shiftDate = shiftDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kind = kind;
        this.note = note;
        this.createdBy = createdBy;
    }

    public LocalDateTime startsAt() {
        return shiftDate.atTime(startTime);
    }

    public LocalDateTime endsAt() {
        return endTime.isAfter(startTime) ? shiftDate.atTime(endTime) : shiftDate.plusDays(1).atTime(endTime);
    }

    public boolean covers(LocalDateTime moment) {
        return !moment.isBefore(startsAt()) && moment.isBefore(endsAt());
    }

    public boolean overlaps(Shift other) {
        return startsAt().isBefore(other.endsAt()) && other.startsAt().isBefore(endsAt());
    }

    public long minutes() {
        return Duration.between(startsAt(), endsAt()).toMinutes();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getShiftDate() { return shiftDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public ShiftKind getKind() { return kind; }
    public String getNote() { return note; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
