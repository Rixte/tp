package mishmash.command;

import java.util.Objects;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.IngredientCustomization;
import mishmash.model.MealPlan;
import mishmash.model.PlannedMeal;
import mishmash.model.RecipeBook;

/**
 * Replaces the ingredients of one planned meal occurrence without editing its source recipe.
 */
public final class CustomizeMealCommand implements Command {
    /** Command word shared by parsing and user guidance. */
    public static final String COMMAND_WORD = "customize-meal";

    /** Describes the selected meal and repeated replacement ingredient fields. */
    public static final String USAGE = "Format: " + COMMAND_WORD
            + " d/DAY m/MEAL i/INGREDIENT:QUANTITYUNIT [i/INGREDIENT:QUANTITYUNIT ...]";

    private static final Logger LOGGER = Logger.getLogger(CustomizeMealCommand.class.getName());

    private final IngredientCustomization customization;
    private final ActiveMealPlan activeMealPlan;
    private final DietarySettings dietarySettings;

    /**
     * Creates a command connected to the latest plan and dietary settings.
     *
     * @param customization The validated selected positions and replacement ingredients.
     * @param activeMealPlan The shared plan to update atomically.
     * @param dietarySettings The current restrictions the effective ingredients must satisfy.
     * @throws NullPointerException If any argument is null.
     */
    public CustomizeMealCommand(IngredientCustomization customization, ActiveMealPlan activeMealPlan,
            DietarySettings dietarySettings) {
        this.customization = Objects.requireNonNull(customization, "customization");
        this.activeMealPlan = Objects.requireNonNull(activeMealPlan, "activeMealPlan");
        this.dietarySettings = Objects.requireNonNull(dietarySettings, "dietarySettings");
    }

    /**
     * Publishes one copy-on-write ingredient replacement after all position and dietary checks pass.
     *
     * @param recipeBook The shared command context; source recipes are not read or changed.
     * @return A customization summary or a prompt to generate a plan first.
     * @throws MishMashException If a position does not exist or the replacement violates current restrictions.
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
            LOGGER.fine("Meal customization skipped: no active meal plan");
            return CommandResult.note("No active meal plan. Use 'generate-plan' first.");
        }

        validateDayNumber(currentPlan);
        DayPlan selectedDay = currentPlan.getDays().get(customization.dayNumber() - 1);
        validateMealNumber(selectedDay);
        int mealIndex = customization.mealNumber() - 1;
        MealPlan updatedPlan = currentPlan.withCustomizedIngredients(
                customization.dayNumber() - 1, mealIndex, customization.ingredients());
        if (updatedPlan == currentPlan) {
            return CommandResult.note("Meal " + customization.mealNumber() + " on Day "
                    + customization.dayNumber() + " already uses those ingredients. Nothing was changed.");
        }

        PlannedMeal updatedMeal = updatedPlan.getDays().get(customization.dayNumber() - 1)
                .getPlannedMeals().get(mealIndex);
        DietaryPreferences preferences = dietarySettings.get();
        if (!preferences.isPlannedMealAllowed(updatedMeal)) {
            throw new MishMashException("Those ingredients conflict with your current dietary preferences.",
                    USAGE, null);
        }

        boolean hadGroceryList = activeMealPlan.getGroceryList().isPresent();
        activeMealPlan.update(updatedPlan);
        LOGGER.fine(() -> "Customized ingredients for Day " + customization.dayNumber()
                + ", Meal " + customization.mealNumber() + "; ingredient count="
                + customization.ingredients().size());
        String action = updatedMeal.hasCustomizedIngredients()
                ? "ingredients customized"
                : "ingredients restored from its source recipe";
        String result = "Meal " + customization.mealNumber() + " on Day " + customization.dayNumber()
                + " " + action + "." + System.lineSeparator()
                + "Its source recipe and nutrition values are unchanged.";
        if (hadGroceryList) {
            result += System.lineSeparator()
                    + "The previous grocery list was cleared. Run 'generate-list' to update it.";
        } else {
            result += System.lineSeparator() + "Run 'generate-list' to create groceries for the updated plan.";
        }
        if (updatedMeal.hasCustomizedIngredients() && preferences.hasCategoryRestrictions()) {
            return CommandResult.warning(result + System.lineSeparator()
                    + "Customized ingredient names are not classified for named-diet or avoided-category rules."
                    + System.lineSeparator()
                    + "This meal keeps its source recipe's food categories; verify the replacement yourself.");
        }
        return CommandResult.done(result);
    }

    /**
     * Rejects a day position outside the current plan.
     */
    private void validateDayNumber(MealPlan plan) throws MishMashException {
        if (customization.dayNumber() > plan.getNumberOfDays()) {
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
            throw new MishMashException("Day " + customization.dayNumber() + " has no meals to customize.",
                    USAGE, null);
        }
        if (customization.mealNumber() > mealCount) {
            throw new MishMashException("Meal number for Day " + customization.dayNumber()
                    + " must be between 1 and " + mealCount + ".", USAGE, null);
        }
    }
}
