package io.github.andis382.carelog.rota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Proof of work for the carer: arrived, left, and (if the phone allowed it) where. */
@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "in_lat")
    private BigDecimal inLat;

    @Column(name = "in_lng")
    private BigDecimal inLng;

    @Column(name = "in_accuracy")
    private Integer inAccuracy;

    @Column(name = "out_lat")
    private BigDecimal outLat;

    @Column(name = "out_lng")
    private BigDecimal outLng;

    @Column(name = "out_accuracy")
    private Integer outAccuracy;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "out_client_id")
    private String outClientId;

    protected CheckIn() {}

    public CheckIn(Long organizationId, Long userId, Instant checkedInAt, Position position, String clientId) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.checkedInAt = checkedInAt;
        this.clientId = clientId;
        if (position != null) {
            this.inLat = position.lat();
            this.inLng = position.lng();
            this.inAccuracy = position.accuracy();
        }
    }

    public void checkOut(Instant at, Position position, String clientId) {
        this.checkedOutAt = at;
        this.outClientId = clientId;
        if (position != null) {
            this.outLat = position.lat();
            this.outLng = position.lng();
            this.outAccuracy = position.accuracy();
        }
    }

    /** Minutes on duty; an open check-in counts until {@code now}. */
    public long minutesUntil(Instant now) {
        Instant end = checkedOutAt == null ? now : checkedOutAt;
        return Math.max(0, Duration.between(checkedInAt, end).toMinutes());
    }

    public record Position(BigDecimal lat, BigDecimal lng, Integer accuracy) {}

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getUserId() { return userId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public BigDecimal getInLat() { return inLat; }
    public BigDecimal getInLng() { return inLng; }
    public Integer getInAccuracy() { return inAccuracy; }
    public BigDecimal getOutLat() { return outLat; }
    public BigDecimal getOutLng() { return outLng; }
    public String getClientId() { return clientId; }
    public String getOutClientId() { return outClientId; }
}
