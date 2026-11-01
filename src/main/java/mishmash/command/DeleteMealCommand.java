package mishmash.command;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.MealEntry;
import mishmash.model.MealLog;
import mishmash.model.RecipeBook;
import mishmash.ui.MealLogViewer;

//@@author GauthamNandi
/**
 * Removes a recorded meal, chosen by the number shown by {@code view-meals}, after confirmation.
 */
public class DeleteMealCommand implements Command {
    /**
     * The word the user types to remove a recorded meal.
     */
    public static final String COMMAND_WORD = "delete-meal";

    /**
     * Describes the command's single meal-number argument.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD + " MEAL_NUMBER";

    private static final Logger LOGGER = Logger.getLogger(DeleteMealCommand.class.getName());

    private final int mealNumber;
    private final MealLog mealLog;

    /**
     * Creates a command that deletes the meal at the supplied position.
     *
     * @param mealNumber The position as shown by {@code view-meals}, where the first meal is 1.
     * @param mealLog The log holding the meal.
     * @throws IllegalArgumentException If the number is less than 1.
     * @throws NullPointerException If the meal log is null.
     */
    public DeleteMealCommand(int mealNumber, MealLog mealLog) {
        if (mealNumber < 1) {
            throw new IllegalArgumentException("Meal number must be at least 1.");
        }
        this.mealNumber = mealNumber;
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
    }

    /**
     * Describes the meal that would be deleted, so the user can check the number they typed.
     * A number that does not exist is reported now, rather than after the user has confirmed.
     *
     * @param recipeBook The shared command context, which is not used.
     * @return The yes/no question describing the chosen meal.
     * @throws MishMashException If the log is empty or the number is larger than the number of meals.
     */
    @Override
    public Optional<String> confirmationPrompt(RecipeBook recipeBook) throws MishMashException {
        MealEntry target = MealLookup.find(mealLog, mealNumber, USAGE);
        return Optional.of("Are you sure you want to delete meal " + mealNumber + ": "
                + new MealLogViewer().describe(target) + "? (y/n)");
    }

    /**
     * Deletes the meal, leaving the log unchanged if the number does not exist.
     * This does not ask for confirmation itself; the caller does that using {@link #confirmationPrompt}.
     *
     * @param recipeBook The shared command context, which is not used.
     * @return The deleted meal and the totals remaining for its date.
     * @throws MishMashException If the log is empty or the number is larger than the number of meals.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        MealLookup.find(mealLog, mealNumber, USAGE);
        MealEntry deleted = mealLog.remove(mealNumber - 1);
        LOGGER.fine(() -> "delete-meal removed meal " + mealNumber);
        return new MealLogViewer().viewDeleted(deleted, mealLog.getTotalsOn(deleted.date()));
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        return CommandResult.done(execute(recipeBook));
    }
}
//@@author
