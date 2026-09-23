package io.github.andis382.carelog.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class EmergencyContact {

    @Column(nullable = false)
    private String name;

    private String relation;

    private String phone;

    protected EmergencyContact() {}

    public EmergencyContact(String name, String relation, String phone) {
        this.name = name;
        this.relation = relation;
        this.phone = phone;
    }

    public String getName() { return name; }
    public String getRelation() { return relation; }
    public String getPhone() { return phone; }
}
