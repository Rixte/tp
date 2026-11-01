package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.ui.MealPlanViewer;
import mishmash.ui.MealPlanViewer.MealSummary;

//@@author Rixte
/**
 * Displays all days or one selected day from the current meal plan without generating meals.
 */
public class ViewPlanCommand implements Command {
    /**
     * Describes the command's optional day argument.
     */
    public static final String USAGE = "Format: view-plan [d/DAY_NUMBER]";

    private static final Logger LOGGER = Logger.getLogger(ViewPlanCommand.class.getName());

    /**
     * Reads the current plan at execution time; an empty list means there is no active plan.
     */
    private final Supplier<List<List<MealSummary>>> mealPlanSource;
    //@@author

    /** Reads daily targets belonging to the current plan at execution time. */
    private final Supplier<NutritionTargets> targetsSource;

    //@@author Rixte
    /**
     * The selected one-based day number, or null when all days were requested.
     */
    private final Integer dayNumber;
    //@@author

    /**
     * Creates a command to display every day of the active plan.
     *
     * @param mealPlanSource A function returning the current plan's meals grouped by day, or an empty list
     *         if no plan is active. The function must never return {@code null}.
     * @throws NullPointerException If the source is null.
     */
    public ViewPlanCommand(Supplier<List<List<MealSummary>>> mealPlanSource) {
        this(mealPlanSource, NutritionTargets::none, null);
    }

    /**
     * Creates a command to display one day, checking its upper bound against the plan at execution time.
     *
     * @param mealPlanSource A function returning the current plan's meals grouped by day, or an empty list
     *         if no plan is active. The function must never return {@code null}.
     * @param dayNumber The day to display, starting at 1.
     * @throws IllegalArgumentException If the day number is not positive.
     * @throws NullPointerException If the source is null.
     */
    public ViewPlanCommand(Supplier<List<List<MealSummary>>> mealPlanSource, int dayNumber) {
        this(mealPlanSource, NutritionTargets::none, dayNumber);
    }

    /**
     * Creates a command that reads meals and their daily targets when executed.
     *
     * @param mealPlanSource The current plan's meals, or an empty list if no plan is active.
     * @param targetsSource The current plan's targets, or unset targets if no request is retained.
     * @param dayNumber The selected one-based day, or null to display all days.
     * @throws IllegalArgumentException If a supplied day number is not positive.
     * @throws NullPointerException If either source is null.
     */
    public ViewPlanCommand(Supplier<List<List<MealSummary>>> mealPlanSource,
            Supplier<NutritionTargets> targetsSource, Integer dayNumber) {
        this.mealPlanSource = Objects.requireNonNull(mealPlanSource, "mealPlanSource");
        this.targetsSource = Objects.requireNonNull(targetsSource, "targetsSource");
        if (dayNumber != null && dayNumber < 1) {
            throw new IllegalArgumentException("Day number must be a positive integer.");
        }
        this.dayNumber = dayNumber;
    }

    /**
     * Displays the latest supplied plan, checking the selected day against its current size.
     *
     * @param recipeBook The shared command context; viewing does not read or modify recipes.
     * @return The formatted plan or a prompt when no plan is available.
     * @throws MishMashException If the selected day exceeds the active plan's size.
     * @throws IllegalStateException If the source returns {@code null} instead of a list.
     * @throws NullPointerException If a displayed day, meal, or supplied targets are null.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        //@@author Rixte
        List<List<MealSummary>> mealsByDay = mealPlanSource.get();
        if (mealsByDay == null) {
            throw new IllegalStateException("Meal plan source returned null; use an empty list for no active plan.");
        }
        MealPlanViewer viewer = new MealPlanViewer();
        if (mealsByDay.isEmpty()) {
            LOGGER.fine("view-plan requested with no active meal plan");
            return CommandResult.note(viewer.view(mealsByDay));
        }
        //@@author
        if (dayNumber == null) {
            String result = viewer.view(mealsByDay, targetsSource.get());
            LOGGER.fine(() -> "view-plan displayed all " + mealsByDay.size() + " days");
            return CommandResult.info(result);
        }
        //@@author Rixte
        if (dayNumber > mealsByDay.size()) {
            LOGGER.fine(() -> "view-plan rejected day " + dayNumber
                    + ": active plan contains " + mealsByDay.size() + " days");
            throw new MishMashException("Day number must be between 1 and " + mealsByDay.size() + ".", USAGE, null);
        }
        //@@author
        String result = viewer.view(mealsByDay, dayNumber, targetsSource.get());
        LOGGER.fine(() -> "view-plan displayed day " + dayNumber);
        return CommandResult.info(result);
    }
}
