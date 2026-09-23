package io.github.andis382.carelog.daily;

/** Main meals, a snack, or just a drink (water, tea) logged on its own. */
public enum MealSlot {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    DRINK;

    public boolean isMainMeal() {
        return this == BREAKFAST || this == LUNCH || this == DINNER;
    }
}
