package mishmash.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;

//@@author Rixte
/**
 * Reports daily nutrition shortfalls in the active plan without changing meals or targets.
 * A shortfall is any amount below a supplied target, independently of generation's tolerance.
 */
public class CheckShortfallCommand implements Command {
    /**
     * Command word shared by parsing, help, and usage instructions.
     */
    public static final String COMMAND_WORD = "check-shortfall";

    /**
     * At least one of these daily targets must be supplied.
     */
    public static final String USAGE = "Format: " + COMMAND_WORD + " [cal/DAILY_CALORIES] [p/DAILY_PROTEIN]"
            + " [c/DAILY_CARBS] [f/DAILY_FATS]";

    private static final Logger LOGGER = Logger.getLogger(CheckShortfallCommand.class.getName());

    /**
     * Reads the latest plan when the command executes, rather than capturing an earlier plan.
     */
    private final ActiveMealPlan activePlan;

    /**
     * Request-specific targets; a null nutrient value means that nutrient is not checked.
     */
    private final NutritionTargets targets;

    /**
     * Creates a check against explicit daily targets, leaving omitted nutrients unchecked.
     *
     * @param activePlan The shared holder of the current meal plan.
     * @param targets The daily targets to compare with each day's planned totals.
     * @throws NullPointerException If either argument is null.
     * @throws IllegalArgumentException If no target is supplied.
     */
    public CheckShortfallCommand(ActiveMealPlan activePlan, NutritionTargets targets) {
        this.activePlan = Objects.requireNonNull(activePlan, "activePlan");
        this.targets = Objects.requireNonNull(targets, "targets");
        if (!targets.hasAnyTarget()) {
            throw new IllegalArgumentException("Supply at least one daily nutrition target.");
        }
    }

    /**
     * Compares every day separately so a surplus on one day cannot hide another day's shortfall.
     * Values at or above a target are not shortfalls; this command does not check excesses.
     *
     * @param recipeBook The shared command context; the check does not modify recipes.
     * @return Each day's shortfalls, or a prompt to generate a plan first.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        MealPlan plan = activePlan.get().orElse(null);
        if (plan == null || plan.getNumberOfDays() == 0) {
            LOGGER.fine("Shortfall check skipped: no active days to check");
            return "No active meal plan. Use 'generate-plan' first.";
        }

        List<String> outputLines = new ArrayList<>();
        outputLines.add("Daily nutrition shortfalls (compared with the supplied targets):");
        int daysWithShortfalls = 0;
        for (int index = 0; index < plan.getNumberOfDays(); index++) {
            List<String> shortfalls = findShortfalls(plan.getDays().get(index));
            if (shortfalls.isEmpty()) {
                outputLines.add("Day " + (index + 1) + ": No shortfalls for the supplied targets.");
            } else {
                daysWithShortfalls++;
                outputLines.add("Day " + (index + 1) + ":");
                outputLines.addAll(shortfalls);
            }
        }

        LOGGER.fine("Checked nutrition shortfalls for " + plan.getNumberOfDays()
                + " days; days with shortfalls: " + daysWithShortfalls);
        return String.join(System.lineSeparator(), outputLines);
    }

    /**
     * Returns deficits for the supplied nutrients whose planned totals are below their daily targets.
     *
     * @param day The planned day whose nutrition totals are checked.
     * @return Deficit lines in calorie, protein, carbohydrate, and fat order.
     */
    private List<String> findShortfalls(DayPlan day) {
        List<String> shortfalls = new ArrayList<>();
        addShortfall(shortfalls, "Calories", "kcal", day.getTotalCalories(), targets.getCalories());
        addShortfall(shortfalls, "Protein", "g", day.getTotalProtein(), targets.getProtein());
        addShortfall(shortfalls, "Carbs", "g", day.getTotalCarbs(), targets.getCarbs());
        addShortfall(shortfalls, "Fats", "g", day.getTotalFats(), targets.getFats());
        return shortfalls;
    }

    /**
     * Adds a deficit line when a supplied daily target exceeds the planned total.
     * Long arithmetic prevents large recipe totals from wrapping around into false deficits.
     *
     * @param shortfalls The output lines for the current day.
     * @param nutrientName The nutrient's display name.
     * @param unit The displayed measurement unit, either kcal or g.
     * @param plannedTotal The total amount supplied by that day's planned meals.
     * @param dailyTarget The amount to compare against, or null to leave the nutrient unchecked.
     */
    private void addShortfall(List<String> shortfalls, String nutrientName, String unit,
            long plannedTotal, Integer dailyTarget) {
        // Recipes contain nonnegative nutrition; a negative total indicates a broken model invariant.
        assert plannedTotal >= 0 : "Planned nutrition totals must not be negative.";
        if (dailyTarget != null && plannedTotal < dailyTarget) {
            shortfalls.add("- " + nutrientName + ": " + plannedTotal + "/" + dailyTarget + " " + unit
                    + " (short by " + (dailyTarget - plannedTotal) + " " + unit + ").");
        }
    }
}
//@@author
