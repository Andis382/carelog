package io.github.andis382.carelog.auth;

/** What a person may do inside the care circle. */
public enum Role {
    /** The coordinator, usually the child abroad who pays. Manages members, plan and settings. */
    OWNER,
    /** Siblings, spouse: record care and plan it (medicines, rota, elder profile). */
    FAMILY,
    /** The hired carer: records care, keeps the rota, cannot change the plan or the circle. */
    CARER,
    /** A neighbour or relative who may read the log but not write to it. */
    VIEWER;

    /** May write to the log: doses, vitals, meals, notes, check-ins, supplies, visits. */
    public boolean canRecord() {
        return this != VIEWER;
    }

    /** May change the care plan: medicines, shifts for others, the elder's profile. */
    public boolean canPlan() {
        return this == OWNER || this == FAMILY;
    }
}
