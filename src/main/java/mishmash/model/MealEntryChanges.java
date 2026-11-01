package mishmash.model;

import java.time.LocalDate;

//@@author GauthamNandi
/**
 * The optional date and servings typed for {@code record-meal} or {@code edit-meal}.
 * A null field means the user did not supply it.
 *
 * @param date The supplied date, or null.
 * @param servings The supplied serving count, or null.
 */
public record MealEntryChanges(LocalDate date, Integer servings) {
    /**
     * Creates the changes after checking any supplied serving count.
     *
     * @throws IllegalArgumentException If a supplied serving count is out of range.
     */
    public MealEntryChanges {
        if (servings != null) {
            MealEntry.requireValidServings(servings);
        }
    }

    /**
     * Returns whether at least one field was supplied.
     */
    public boolean hasAnyChange() {
        return date != null || servings != null;
    }
}
//@@author
