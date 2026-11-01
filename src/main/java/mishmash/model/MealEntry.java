package mishmash.model;

import java.time.LocalDate;
import java.util.Objects;

//@@author GauthamNandi
/**
 * One meal in the {@link MealLog}: which recipe was eaten, on which date, and how many servings.
 *
 * <p>Entries are immutable, so correcting a meal replaces its entry instead of changing it. The recipe
 * is kept as a snapshot, so later recipe edits do not alter what was recorded.</p>
 *
 * @param recipe The recipe that was eaten.
 * @param date The date it was eaten.
 * @param servings The number of whole servings, from 1 to {@value #MAX_SERVINGS}.
 */
public record MealEntry(Recipe recipe, LocalDate date, int servings) {
    /** Largest serving count for one entry, which catches typos and keeps nutrition totals within range. */
    public static final int MAX_SERVINGS = 100;

    /**
     * Creates an entry after checking every field.
     *
     * @throws NullPointerException If the recipe or date is null.
     * @throws IllegalArgumentException If the serving count is out of range.
     */
    public MealEntry {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(date, "date");
        requireValidServings(servings);
    }

    /**
     * Rejects a serving count outside the supported range.
     *
     * @param servings The serving count to check.
     * @throws IllegalArgumentException If it is not between 1 and {@value #MAX_SERVINGS}.
     */
    public static void requireValidServings(int servings) {
        if (servings < 1 || servings > MAX_SERVINGS) {
            throw new IllegalArgumentException("Servings must be between 1 and " + MAX_SERVINGS + ".");
        }
    }

    /**
     * Returns the nutrition of this entry, which is the recipe's nutrition multiplied by the servings.
     */
    public NutritionTotals nutrition() {
        // Multiply as long values so a large recipe cannot overflow an int.
        return new NutritionTotals((long) recipe.getCalories() * servings, (long) recipe.getProtein() * servings,
                (long) recipe.getCarbs() * servings, (long) recipe.getFats() * servings);
    }

    /**
     * Returns a copy of this entry with the supplied changes applied.
     *
     * @param changes The new date and servings; a null field keeps the current value.
     * @return The corrected entry; this entry is not modified.
     * @throws NullPointerException If the changes are null.
     */
    public MealEntry with(MealEntryChanges changes) {
        Objects.requireNonNull(changes, "changes");
        return new MealEntry(recipe, changes.date() != null ? changes.date() : date,
                changes.servings() != null ? changes.servings() : servings);
    }
}
//@@author
