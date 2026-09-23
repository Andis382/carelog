package io.github.andis382.carelog.daily;

/** Journal entries. Mood and pain carry a 1-5 score; the rest are mostly words. */
public enum JournalKind {
    NOTE,
    MOOD,
    PAIN,
    SLEEP,
    TOILET,
    INCIDENT;

    public boolean needsScore() {
        return this == MOOD || this == PAIN;
    }

    public boolean needsText() {
        return this == NOTE || this == INCIDENT;
    }
}
