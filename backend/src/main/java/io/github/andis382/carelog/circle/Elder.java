package io.github.andis382.carelog.circle;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The person the circle cares for. One per circle. */
@Entity
@Table(name = "elders")
public class Elder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "photo_file_id")
    private String photoFileId;

    private String conditions;
    private String allergies;

    @Column(name = "gp_name")
    private String gpName;

    @Column(name = "gp_phone")
    private String gpPhone;

    private String address;
    private String town;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "elder_contacts", joinColumns = @JoinColumn(name = "elder_id"))
    @OrderColumn(name = "position")
    private List<EmergencyContact> contacts = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Elder() {}

    public Elder(Long organizationId, String fullName) {
        this.organizationId = organizationId;
        this.fullName = fullName;
    }

    public String firstName() {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    public Integer ageIn(int year) {
        return birthYear == null ? null : year - birthYear;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Integer getBirthYear() { return birthYear; }
    public void setBirthYear(Integer birthYear) { this.birthYear = birthYear; }
    public String getPhotoFileId() { return photoFileId; }
    public void setPhotoFileId(String photoFileId) { this.photoFileId = photoFileId; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public String getGpName() { return gpName; }
    public void setGpName(String gpName) { this.gpName = gpName; }
    public String getGpPhone() { return gpPhone; }
    public void setGpPhone(String gpPhone) { this.gpPhone = gpPhone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTown() { return town; }
    public void setTown(String town) { this.town = town; }
    public List<EmergencyContact> getContacts() { return contacts; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
