package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.DietarySettings;
import mishmash.model.MealPlan;
import mishmash.model.MealReplacement;
import mishmash.model.PlannedMeal;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

//@@author neil0618
/**
 * Replaces one planned meal in the active plan with a recipe chosen from the recipe book.
 */
public final class ReplaceMealCommand implements Command {
    /** Command word shared by parsing and user guidance. */
    public static final String COMMAND_WORD = "replace-meal";

    /** Describes every required field of a meal replacement. */
    public static final String USAGE = "Format: " + COMMAND_WORD + " d/DAY m/MEAL r/RECIPE_INDEX";

    private static final Logger LOGGER = Logger.getLogger(ReplaceMealCommand.class.getName());

    private final MealReplacement replacement;
    private final ActiveMealPlan activeMealPlan;
    private final DietarySettings dietarySettings;

    /**
     * Creates a command that resolves its one-based positions against the plan and recipe book on execution.
     *
     * @param replacement The validated day, meal, and recipe positions.
     * @param activeMealPlan The shared active plan to read and update.
     * @param dietarySettings The current restrictions the chosen recipe must satisfy.
     * @throws NullPointerException If any argument is null.
     */
    public ReplaceMealCommand(MealReplacement replacement, ActiveMealPlan activeMealPlan,
            DietarySettings dietarySettings) {
        this.replacement = Objects.requireNonNull(replacement, "replacement");
        this.activeMealPlan = Objects.requireNonNull(activeMealPlan, "activeMealPlan");
        this.dietarySettings = Objects.requireNonNull(dietarySettings, "dietarySettings");
    }

    /**
     * Swaps one planned meal for a one-portion, uncustomized copy of the chosen recipe.
     * Every check runs before the plan changes, so a rejected replacement leaves all state untouched.
     *
     * @param recipeBook The recipe book the recipe index refers to.
     * @return A summary of the new meal or a prompt to generate a plan first.
     * @throws MishMashException If a position does not exist, the recipe conflicts with the current
     *         dietary preferences, or the day's nutrition totals would overflow.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");
        MealPlan currentPlan = activeMealPlan.get().orElse(null);
        if (currentPlan == null || currentPlan.getNumberOfDays() == 0) {
            LOGGER.fine("Meal replacement skipped: no active meal plan");
            return CommandResult.note("No active meal plan. Use 'generate-plan' first.");
        }

        validateDayNumber(currentPlan);
        int dayIndex = replacement.dayNumber() - 1;
        DayPlan selectedDay = currentPlan.getDays().get(dayIndex);
        validateMealNumber(selectedDay);
        int mealIndex = replacement.mealNumber() - 1;
        Recipe chosenRecipe = findRecipe(recipeBook);

        PlannedMeal currentMeal = selectedDay.getPlannedMeals().get(mealIndex);
        if (isUnchanged(currentMeal, chosenRecipe)) {
            return CommandResult.note("Meal " + replacement.mealNumber() + " on Day " + replacement.dayNumber()
                    + " is already " + chosenRecipe.getName() + ". Nothing was changed.");
        }
        if (!dietarySettings.get().isAllowed(chosenRecipe)) {
            throw new MishMashException("Recipe " + replacement.recipeNumber() + " ("
                    + chosenRecipe.getName() + ") conflicts with your current dietary preferences.", USAGE, null);
        }

        PlannedMeal newMeal = PlannedMeal.onePortionOf(chosenRecipe);
        MealPlan updatedPlan = replaceMeal(currentPlan, dayIndex, mealIndex, newMeal);
        return publishReplacement(updatedPlan, newMeal, chosenRecipe);
    }

    /**
     * Builds a replacement plan while keeping overflow failures recoverable and the current plan untouched.
     */
    private MealPlan replaceMeal(MealPlan currentPlan, int dayIndex, int mealIndex, PlannedMeal newMeal)
            throws MishMashException {
        try {
            return currentPlan.withReplacedMeal(dayIndex, mealIndex, newMeal);
        } catch (ArithmeticException e) {
            throw new MishMashException("That recipe makes the day's nutrition totals too large.", USAGE, e);
        }
    }

    /**
     * Publishes a validated recipe replacement and reports how to obtain its groceries.
     */
    private CommandResult publishReplacement(MealPlan updatedPlan, PlannedMeal newMeal, Recipe chosenRecipe) {
        boolean hadGroceryList = activeMealPlan.getGroceryList().isPresent();
        activeMealPlan.update(updatedPlan);
        LOGGER.fine(() -> "Replaced Day " + replacement.dayNumber() + ", Meal " + replacement.mealNumber()
                + " with recipe " + replacement.recipeNumber());
        String result = "Meal " + replacement.mealNumber() + " on Day " + replacement.dayNumber()
                + " replaced with " + chosenRecipe.getName() + " (" + newMeal.getCalories() + " kcal | P: "
                + newMeal.getProtein() + "g, C: " + newMeal.getCarbs() + "g, F: " + newMeal.getFats() + "g).";
        if (hadGroceryList) {
            return CommandResult.done(result + System.lineSeparator()
                    + "The previous grocery list was cleared. Run 'generate-list' to update it.");
        }
        return CommandResult.done(result + System.lineSeparator()
                + "Run 'generate-list' to create groceries for the updated plan.");
    }

    /**
     * Rejects a day position outside the plan without exposing zero-based model positions.
     */
    private void validateDayNumber(MealPlan plan) throws MishMashException {
        if (replacement.dayNumber() > plan.getNumberOfDays()) {
            throw new MishMashException("Day number must be between 1 and " + plan.getNumberOfDays() + ".",
                    USAGE, null);
        }
    }

    /**
     * Rejects a meal position outside the selected day.
     */
    private void validateMealNumber(DayPlan day) throws MishMashException {
        int mealCount = day.getPlannedMeals().size();
        if (mealCount == 0) {
            throw new MishMashException("Day " + replacement.dayNumber() + " has no meals to replace.",
                    USAGE, null);
        }
        if (replacement.mealNumber() > mealCount) {
            throw new MishMashException("Meal number for Day " + replacement.dayNumber()
                    + " must be between 1 and " + mealCount + ".", USAGE, null);
        }
    }

    /**
     * Finds the recipe at the displayed index, rejecting an empty book or an index past its end.
     */
    private Recipe findRecipe(RecipeBook recipeBook) throws MishMashException {
        List<Recipe> recipes = recipeBook.getRecipes();
        if (recipes.isEmpty()) {
            throw new MishMashException("There are no recipes to choose from. Use 'add-recipe' to add one.",
                    USAGE, null);
        }
        if (replacement.recipeNumber() > recipes.size()) {
            throw new MishMashException("Recipe number must be between 1 and " + recipes.size() + ".",
                    USAGE, null);
        }
        return recipes.get(replacement.recipeNumber() - 1);
    }

    /**
     * Returns true when the slot already holds this exact recipe at one portion with no ingredient changes.
     */
    private boolean isUnchanged(PlannedMeal currentMeal, Recipe chosenRecipe) {
        return currentMeal.getRecipe() == chosenRecipe
                && currentMeal.getPortionCount() == 1
                && !currentMeal.hasCustomizedIngredients();
    }
}
//@@author
