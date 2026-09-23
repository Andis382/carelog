package io.github.andis382.carelog.circle;

/**
 * What the circle pays for. Free keeps two members and shows 30 days of history; older
 * entries are kept, just hidden, so upgrading brings them back.
 */
public enum Plan {
    FREE(2, 30),
    FAMILY(Integer.MAX_VALUE, 0);

    private final int maxMembers;
    private final int historyDays;

    Plan(int maxMembers, int historyDays) {
        this.maxMembers = maxMembers;
        this.historyDays = historyDays;
    }

    public int maxMembers() {
        return maxMembers;
    }

    /** Days of visible history, or 0 for all of it. */
    public int historyDays() {
        return historyDays;
    }
}
