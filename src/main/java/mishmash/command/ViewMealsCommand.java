package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import mishmash.model.MealLog;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.ui.MealLogViewer;

//@@author GauthamNandi
/**
 * Displays the recorded meals, each with its calories and macronutrients, followed by their totals.
 */
public class ViewMealsCommand implements Command {
    /**
     * The word the user types to view recorded meals.
     */
    public static final String COMMAND_WORD = "view-meals";

    /**
     * Describes the command, which takes no arguments.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD;

    private static final String NO_MEALS_MESSAGE = "No meals recorded yet.";

    private static final Logger LOGGER = Logger.getLogger(ViewMealsCommand.class.getName());

    private final MealLog mealLog;

    /**
     * Creates a command that reads the given log when it runs.
     *
     * @param mealLog The log to display.
     * @throws NullPointerException If the meal log is null.
     */
    public ViewMealsCommand(MealLog mealLog) {
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
    }

    /**
     * Displays the log as it is when the command runs, so meals recorded after parsing are included.
     * An empty log is a valid state rather than an error.
     *
     * @param recipeBook The shared command context; viewing does not read or modify recipes.
     * @return The formatted meals and totals, or a message if nothing has been recorded.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        List<Recipe> meals = mealLog.getMeals();
        if (meals.isEmpty()) {
            LOGGER.fine("view-meals requested with no recorded meals");
            return CommandResult.note(NO_MEALS_MESSAGE);
        }
        String result = new MealLogViewer().view(meals, mealLog.getTotals());
        LOGGER.fine(() -> "view-meals displayed " + meals.size() + " meals");
        return CommandResult.info(result);
    }
}
//@@author
