package io.github.andis382.carelog.daily;

/** How much of the plate she ate. */
public enum MealAmount {
    ALL,
    HALF,
    LITTLE,
    NONE;

    /** All or half counts as a proper meal in the weekly summary. */
    public boolean ateWell() {
        return this == ALL || this == HALF;
    }
}
