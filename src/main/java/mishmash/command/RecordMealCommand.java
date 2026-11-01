package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.MealLog;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.ui.MealLogViewer;

//@@author GauthamNandi
/**
 * Records the recipe at a position chosen by the user as a meal that has been eaten.
 */
public class RecordMealCommand implements Command {
    /**
     * The word the user types to record a meal.
     */
    public static final String COMMAND_WORD = "record-meal";

    /**
     * Describes the command's single index argument.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD + " INDEX";

    private static final Logger LOGGER = Logger.getLogger(RecordMealCommand.class.getName());

    private final int index;
    private final MealLog mealLog;

    /**
     * Creates a command that records the recipe at the supplied position.
     *
     * @param index The position as typed by the user, where the first recipe is 1.
     * @param mealLog The log the meal is added to.
     * @throws IllegalArgumentException If the index is less than 1.
     * @throws NullPointerException If the meal log is null.
     */
    public RecordMealCommand(int index, MealLog mealLog) {
        if (index < 1) {
            throw new IllegalArgumentException("Recipe index must be at least 1.");
        }
        this.index = index;
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
    }

    /**
     * Adds the chosen recipe to the meal log, leaving the log unchanged if the index does not exist.
     * Recording needs no confirmation, so the inherited confirmation question is not overridden.
     *
     * @param recipeBook The recipe book to look the recipe up in; it is not modified.
     * @return The recorded meal's nutrition and the totals of every recorded meal so far.
     * @throws MishMashException If the book is empty or the index is larger than the number of recipes.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");

        List<Recipe> recipes = recipeBook.getRecipes();
        if (recipes.isEmpty()) {
            LOGGER.fine("record-meal rejected: no recipes");
            throw new MishMashException("There are no recipes to record. Use 'add-recipe' to add one.");
        }
        if (index > recipes.size()) {
            LOGGER.fine(() -> "record-meal rejected: index " + index + " exceeds " + recipes.size() + " recipes");
            throw new MishMashException("Recipe index must be between 1 and " + recipes.size() + ".");
        }

        // The user counts from 1, but the list counts from 0.
        Recipe meal = recipes.get(index - 1);
        mealLog.record(meal);
        LOGGER.fine(() -> "record-meal recorded meal " + mealLog.getMeals().size());
        return new MealLogViewer().viewRecorded(meal, mealLog.getTotals());
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        return CommandResult.done(execute(recipeBook));
    }
}
//@@author
