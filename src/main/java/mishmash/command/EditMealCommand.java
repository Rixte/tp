package mishmash.command;

import java.util.Objects;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.MealEntry;
import mishmash.model.MealEntryChanges;
import mishmash.model.MealLog;
import mishmash.model.RecipeBook;
import mishmash.ui.MealLogViewer;

//@@author GauthamNandi
/**
 * Corrects the date or servings of a recorded meal, chosen by the number shown by {@code view-meals}.
 *
 * <p>Changing which recipe was eaten is not supported: deleting the entry and recording the right recipe
 * does the same job with the commands that already exist.</p>
 */
public class EditMealCommand implements Command {
    /**
     * The word the user types to correct a recorded meal.
     */
    public static final String COMMAND_WORD = "edit-meal";

    /**
     * Describes the meal number and the fields that can be corrected.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD + " MEAL_NUMBER [date/YYYY-MM-DD] [s/SERVINGS]";

    private static final Logger LOGGER = Logger.getLogger(EditMealCommand.class.getName());

    private final int mealNumber;
    private final MealEntryChanges changes;
    private final MealLog mealLog;

    /**
     * Creates a command that corrects the meal at the supplied position.
     *
     * @param mealNumber The position as shown by {@code view-meals}, where the first meal is 1.
     * @param changes The corrected values, with at least one supplied.
     * @param mealLog The log holding the meal.
     * @throws IllegalArgumentException If the number is less than 1 or no change is supplied.
     * @throws NullPointerException If the changes or the meal log are null.
     */
    public EditMealCommand(int mealNumber, MealEntryChanges changes, MealLog mealLog) {
        if (mealNumber < 1) {
            throw new IllegalArgumentException("Meal number must be at least 1.");
        }
        if (!Objects.requireNonNull(changes, "changes").hasAnyChange()) {
            throw new IllegalArgumentException("Provide date/ or s/ to correct.");
        }
        this.mealNumber = mealNumber;
        this.changes = changes;
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
    }

    /**
     * Replaces the chosen entry, leaving the log unchanged if the number does not exist.
     * The change is easy to redo, so no confirmation is needed.
     *
     * @param recipeBook The shared command context; correcting a meal does not read or modify recipes.
     * @return The corrected meal and the totals for its date, or a note if nothing would change.
     * @throws MishMashException If the log is empty or the number is larger than the number of meals.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        MealEntry current = MealLookup.find(mealLog, mealNumber, USAGE);
        MealEntry updated = current.with(changes);
        if (updated.equals(current)) {
            LOGGER.fine(() -> "edit-meal left meal " + mealNumber + " unchanged");
            return CommandResult.note("Meal " + mealNumber + " already has those details. Nothing was changed.");
        }
        mealLog.replace(mealNumber - 1, updated);
        LOGGER.fine(() -> "edit-meal updated meal " + mealNumber);
        return CommandResult.done(new MealLogViewer().viewEdited(updated, mealLog.getTotalsOn(updated.date())));
    }
}
//@@author
