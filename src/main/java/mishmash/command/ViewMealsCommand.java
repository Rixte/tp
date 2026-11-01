package mishmash.command;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import mishmash.model.MealEntry;
import mishmash.model.MealLog;
import mishmash.model.RecipeBook;
import mishmash.ui.MealLogViewer;

//@@author GauthamNandi
/**
 * Displays the recorded meals, or those of one date, each with its servings, date, calories and
 * macronutrients, followed by their totals.
 */
public class ViewMealsCommand implements Command {
    /**
     * The word the user types to view recorded meals.
     */
    public static final String COMMAND_WORD = "view-meals";

    /**
     * Describes the command's optional date.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD + " [date/YYYY-MM-DD]";

    private static final String NO_MEALS_MESSAGE = "No meals recorded yet.";

    private static final Logger LOGGER = Logger.getLogger(ViewMealsCommand.class.getName());

    private final MealLog mealLog;

    /** The date to show, or null to show every recorded meal. */
    private final LocalDate date;

    /**
     * Creates a command that shows the whole log when it runs.
     *
     * @param mealLog The log to display.
     * @throws NullPointerException If the meal log is null.
     */
    public ViewMealsCommand(MealLog mealLog) {
        this(mealLog, null);
    }

    /**
     * Creates a command that shows one date of the log when it runs.
     *
     * @param mealLog The log to display.
     * @param date The date to show, or null to show every recorded meal.
     * @throws NullPointerException If the meal log is null.
     */
    public ViewMealsCommand(MealLog mealLog, LocalDate date) {
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
        this.date = date;
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
        List<MealEntry> entries = mealLog.getEntries();
        if (entries.isEmpty()) {
            LOGGER.fine("view-meals requested with no recorded meals");
            return CommandResult.note(NO_MEALS_MESSAGE);
        }
        MealLogViewer viewer = new MealLogViewer();
        if (date == null) {
            LOGGER.fine(() -> "view-meals displayed " + entries.size() + " meals");
            return CommandResult.info(viewer.view(entries, mealLog.getTotals()));
        }
        if (entries.stream().noneMatch(entry -> date.equals(entry.date()))) {
            LOGGER.fine("view-meals found no meals on the requested date");
            return CommandResult.note("No meals recorded on " + date + ".");
        }
        return CommandResult.info(viewer.viewDate(entries, date, mealLog.getTotalsOn(date)));
    }
}
//@@author
