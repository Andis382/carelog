package io.github.andis382.carelog.rota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** "Can you take my Saturday?" One member asks another; only the one asked can answer. */
@Entity
@Table(name = "shift_swaps")
public class ShiftSwap {

    public enum Status { PENDING, ACCEPTED, DECLINED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ShiftSwap() {}

    public ShiftSwap(Long organizationId, Long shiftId, Long fromUserId, Long toUserId, String message, Instant createdAt) {
        this.organizationId = organizationId;
        this.shiftId = shiftId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void answer(Status answer, Instant at) {
        this.status = answer;
        this.respondedAt = at;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getShiftId() { return shiftId; }
    public Long getFromUserId() { return fromUserId; }
    public Long getToUserId() { return toUserId; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
