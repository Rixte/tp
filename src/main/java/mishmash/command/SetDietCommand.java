package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import mishmash.logic.DietaryPlanner;
import mishmash.logic.GroceryListGenerator;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.RecipeBook;

//@@author peilingggg
/**
 * Saves dietary requirements, repairs conflicting days, and refreshes existing groceries.
 */
public final class SetDietCommand implements Command {
    private final DietaryPreferences preferences;
    private final DietarySettings settings;
    private final ActiveMealPlan activePlan;
    private final DietaryPlanner planner;

    /**
     * Creates a command connected to shared dietary settings and active planning state.
     *
     * @param preferences The validated replacement profile.
     * @param settings The session's current dietary settings.
     * @param activePlan The plan and generated groceries to update.
     * @param generator The generator used to replace conflicting days.
     * @throws NullPointerException If any argument is null.
     */
    public SetDietCommand(DietaryPreferences preferences, DietarySettings settings,
            ActiveMealPlan activePlan, MealPlanGenerator generator) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.activePlan = Objects.requireNonNull(activePlan, "activePlan");
        planner = new DietaryPlanner(generator);
    }

    //@@author
    //@@author Rixte
    /**
     * Warns before a dietary change can repair or clear the existing plan and groceries.
     * Compatible plans need no confirmation because their meals are preserved.
     */
    @Override
    public Optional<String> confirmationPrompt(RecipeBook recipeBook) {
        Optional<MealPlan> current = activePlan.get();
        if (current.isPresent() && !planner.isCompatible(current.get(), preferences)) {
            return Optional.of("Apply dietary preferences? Conflicting meals will be replaced, or the plan and "
                    + "groceries cleared if repair fails. (y/n)");
        }
        return Optional.empty();
    }
    //@@author

    //@@author peilingggg
    /**
     * Saves the new profile and updates days that conflict with its requirements.
     *
     * <p>Compatible days and an already compatible plan are preserved. An existing grocery list is
     * rebuilt after repair. If replacement generation fails or its original settings are unavailable,
     * the new profile is still saved and both old lists are cleared, with an explanation returned.
     *
     * @param recipeBook The current recipe pool used to generate replacements.
     * @return A confirmation or a clear explanation that the lists were cleared.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        Optional<MealPlan> currentPlan = activePlan.get();
        if (currentPlan.isEmpty()) {
            activePlan.clear();
            settings.set(preferences);
            return CommandResult.done("Dietary preferences saved. Future meal plans will use them.");
        }
        if (planner.isCompatible(currentPlan.get(), preferences)) {
            settings.set(preferences);
            return CommandResult.done("Dietary preferences saved. Your existing meal plan already matches.");
        }
        return repairPlan(recipeBook, currentPlan.get());
    }

    /**
     * Attempts to repair conflicting days before publishing the new plan and dietary profile.
     */
    private CommandResult repairPlan(RecipeBook recipeBook, MealPlan currentPlan) {
        try {
            GeneratePlanRequest request = activePlan.getRequest().orElseThrow(
                    () -> new IllegalArgumentException("The original meal-plan settings are unavailable."));
            MealPlan updatedPlan = planner.repair(recipeBook.getRecipes(), currentPlan, request, preferences);
            Optional<List<GroceryItem>> groceries = Optional.empty();
            if (activePlan.getGroceryList().isPresent()) {
                groceries = Optional.of(new GroceryListGenerator().generateForPlan(updatedPlan));
            }
            activePlan.setGenerated(updatedPlan, request, groceries);
            settings.set(preferences);
            String groceryMessage = groceries.isPresent()
                    ? "Your grocery list has also been updated."
                    : "Use 'generate-list' when you need a grocery list.";
            return CommandResult.done("Dietary preferences saved. Conflicting days were replaced."
                    + System.lineSeparator() + groceryMessage);
        } catch (IllegalArgumentException e) {
            return clearConflictingPlan(e);
        }
    }

    /**
     * Saves the requested profile while removing a plan that could not be repaired to satisfy it.
     */
    private CommandResult clearConflictingPlan(IllegalArgumentException failure) {
        settings.set(preferences);
        activePlan.clear();
        return CommandResult.warning("Dietary preferences saved, but a replacement plan could not be generated."
                + System.lineSeparator() + failure.getMessage() + System.lineSeparator()
                + "The previous meal plan and grocery list have been cleared."
                + System.lineSeparator()
                + "Add suitable recipes or adjust your targets, then use 'generate-plan'.");
    }
}
//@@author
