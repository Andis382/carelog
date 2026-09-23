package io.github.andis382.carelog.meds;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A medicine on the elder's list, with its schedule. The app never decides doses; it records them. */
@Entity
@Table(name = "medications")
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String name;

    private String strength;

    @Column(name = "dose_text")
    private String doseText;

    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency = Frequency.DAILY;

    @Convert(converter = TimesConverter.class)
    @Column(nullable = false)
    private List<LocalTime> times = List.of();

    @Convert(converter = WeekdaysConverter.class)
    @Column(nullable = false)
    private Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    private String prescriber;

    @Column(name = "box_photo_file_id")
    private String boxPhotoFileId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "stopped_by")
    private Long stoppedBy;

    @Column(name = "stop_reason")
    private String stopReason;

    protected Medication() {}

    public Medication(Long organizationId, String name, Frequency frequency, LocalDate startDate, Long createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.frequency = frequency;
        this.startDate = startDate;
        this.createdBy = createdBy;
    }

    /** "Metformin 500 mg" */
    public String label() {
        return strength == null || strength.isBlank() ? name : name + " " + strength;
    }

    /** Stops from this moment: today's earlier doses still count, later ones are no longer due. */
    public void stop(Long by, Instant at, LocalDate day, String reason) {
        this.active = false;
        this.stoppedAt = at;
        this.stoppedBy = by;
        this.stopReason = reason;
        this.endDate = day;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }
    public String getDoseText() { return doseText; }
    public void setDoseText(String doseText) { this.doseText = doseText; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public List<LocalTime> getTimes() { return times; }
    public void setTimes(List<LocalTime> times) { this.times = times.stream().sorted().distinct().toList(); }
    public Set<DayOfWeek> getWeekdays() { return weekdays; }
    public void setWeekdays(Set<DayOfWeek> weekdays) {
        this.weekdays = weekdays.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(weekdays);
    }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isActive() { return active; }
    public String getPrescriber() { return prescriber; }
    public void setPrescriber(String prescriber) { this.prescriber = prescriber; }
    public String getBoxPhotoFileId() { return boxPhotoFileId; }
    public void setBoxPhotoFileId(String boxPhotoFileId) { this.boxPhotoFileId = boxPhotoFileId; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStoppedAt() { return stoppedAt; }
    public Long getStoppedBy() { return stoppedBy; }
    public String getStopReason() { return stopReason; }
}
