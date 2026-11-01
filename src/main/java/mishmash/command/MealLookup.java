package mishmash.command;

import java.util.List;

import mishmash.exception.MishMashException;
import mishmash.model.MealEntry;
import mishmash.model.MealLog;

//@@author GauthamNandi
/**
 * Finds a recorded meal by its displayed number, with the same errors for every command that needs one.
 */
final class MealLookup {
    private MealLookup() {
        // Holds a static helper only.
    }

    /**
     * Returns the entry at a displayed meal number without changing the log.
     *
     * @param mealLog The log to look in.
     * @param mealNumber The number shown by {@code view-meals}, counting from 1.
     * @param usage The calling command's format, shown with any error.
     * @return The entry at that number.
     * @throws MishMashException If the log is empty or the number is larger than the number of meals.
     */
    static MealEntry find(MealLog mealLog, int mealNumber, String usage) throws MishMashException {
        List<MealEntry> entries = mealLog.getEntries();
        if (entries.isEmpty()) {
            throw new MishMashException("No meals recorded yet. Use 'record-meal' to add one.", usage, null);
        }
        if (mealNumber > entries.size()) {
            throw new MishMashException("Meal number must be between 1 and " + entries.size() + ".", usage, null);
        }
        return entries.get(mealNumber - 1);
    }
}
//@@author
