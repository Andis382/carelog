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
import java.time.LocalDate;

/**
 * A meal or a drink. Entries are never edited: if lunch is recorded twice, the later entry
 * is what the day shows, and the log keeps both.
 */
@Entity
@Table(name = "meals")
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealSlot slot;

    @Enumerated(EnumType.STRING)
    private MealAmount amount;

    @Column(nullable = false)
    private int glasses;

    private String note;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Meal() {}

    public Meal(Long organizationId, LocalDate mealDate, MealSlot slot, MealAmount amount, int glasses, String note,
                Long recordedBy, Instant recordedAt, String clientId) {
        this.organizationId = organizationId;
        this.mealDate = mealDate;
        this.slot = slot;
        this.amount = amount;
        this.glasses = glasses;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public LocalDate getMealDate() { return mealDate; }
    public MealSlot getSlot() { return slot; }
    public MealAmount getAmount() { return amount; }
    public int getGlasses() { return glasses; }
    public String getNote() { return note; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public String getClientId() { return clientId; }
}
