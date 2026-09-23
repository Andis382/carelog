package io.github.andis382.carelog.visits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** What the doctor said, the prescription photo, and when to go back. */
@Entity
@Table(name = "doctor_visits")
public class DoctorVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "doctor_name", nullable = false)
    private String doctorName;

    private String specialty;
    private String place;
    private String notes;

    @Column(name = "next_date")
    private LocalDate nextDate;

    @Column(name = "next_time")
    private LocalTime nextTime;

    @Column(name = "prescription_file_id")
    private String prescriptionFileId;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DoctorVisit() {}

    public DoctorVisit(Long organizationId, Long recordedBy) {
        this.organizationId = organizationId;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public LocalTime getNextTime() { return nextTime; }
    public void setNextTime(LocalTime nextTime) { this.nextTime = nextTime; }
    public String getPrescriptionFileId() { return prescriptionFileId; }
    public void setPrescriptionFileId(String prescriptionFileId) { this.prescriptionFileId = prescriptionFileId; }
    public Long getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
