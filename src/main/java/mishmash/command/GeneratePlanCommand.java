package mishmash.command;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.logic.DietaryPlanner;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DietarySettings;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.MealPlan;
import mishmash.model.Nutrient;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.model.TargetSettings;

/**
 * Runs a parsed {@code generate-plan} command: generates a plan from the recipe book
 * and makes it the active plan.
 */
public class GeneratePlanCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(GeneratePlanCommand.class.getName());

    private final GeneratePlanRequest request;
    private final MealPlanGenerator generator;
    private final ActiveMealPlan activePlan;
    private final DietarySettings dietarySettings;
    //@@author neil0618
    private final TargetSettings targetSettings;
    //@@author

    /**
     * Creates a command for the given request.
     *
     * @param request The validated command arguments.
     * @param generator The generator used to pick the meals.
     * @param activePlan Where the generated plan is stored, shared with other commands such as {@code view-plan}.
     * @throws NullPointerException If any argument is null.
     */
    public GeneratePlanCommand(GeneratePlanRequest request, MealPlanGenerator generator,
                               ActiveMealPlan activePlan) {
        this(request, generator, activePlan, new DietarySettings());
    }

    /**
     * Creates a generation command sharing the session's current dietary requirements.
     *
     * <p>The dietary settings are read during execution, so later preference updates are respected.
     *
     * @param request The validated plan length and daily nutrition targets.
     * @param generator The generator used to select distinct daily recipes.
     * @param activePlan The destination for the generated plan and original settings.
     * @param dietarySettings The shared session preferences used to filter recipes.
     * @throws NullPointerException If any argument is null.
     */
    public GeneratePlanCommand(GeneratePlanRequest request, MealPlanGenerator generator,
                               ActiveMealPlan activePlan, DietarySettings dietarySettings) {
        this(request, generator, activePlan, dietarySettings, new TargetSettings());
    }

    //@@author neil0618
    /**
     * Creates a generation command that also falls back on the session's saved daily targets.
     *
     * <p>The saved targets are read during execution, so a later {@code set-targets} is respected.
     * A target typed in the command itself always replaces the saved one for that nutrient.
     *
     * @param request The validated plan length and the targets typed in the command (possibly none).
     * @param generator The generator used to select distinct daily recipes.
     * @param activePlan The destination for the generated plan and original settings.
     * @param dietarySettings The shared session preferences used to filter recipes.
     * @param targetSettings The shared targets saved with {@code set-targets}.
     * @throws NullPointerException If any argument is null.
     */
    public GeneratePlanCommand(GeneratePlanRequest request, MealPlanGenerator generator,
                               ActiveMealPlan activePlan, DietarySettings dietarySettings,
                               TargetSettings targetSettings) {
        this.request = Objects.requireNonNull(request, "request");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.activePlan = Objects.requireNonNull(activePlan, "activePlan");
        this.dietarySettings = Objects.requireNonNull(dietarySettings, "dietarySettings");
        this.targetSettings = Objects.requireNonNull(targetSettings, "targetSettings");
    }
    //@@author

    /**
     * Generates the plan from the recipes in the book and stores it as the active plan.
     * If generation fails, the previous active plan is left unchanged.
     * The plan itself is not printed; the confirmation points the user to {@code view-plan}.
     *
     * @param recipeBook The recipe book to choose meals from.
     * @return The confirmation naming the number of days and pointing to {@code view-plan}.
     * @throws NullPointerException If the recipe book is null.
     * @throws MishMashException If the plan cannot be generated, such as when there are too few recipes
     *         or no recipes meet the targets.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");

        //@@author neil0618
        // Typed targets win; nutrients left out of the command fall back on the saved ones.
        // With nothing saved the request is used as typed, so behaviour is unchanged.
        NutritionTargets saved = targetSettings.get();
        GeneratePlanRequest effective = saved.hasAnyTarget()
                ? new GeneratePlanRequest(request.days(), saved.overriddenBy(request.targets()))
                : request;
        boolean usesSavedTargets = usesSavedTarget(saved);
        //@@author

        MealPlan plan;
        try {
            plan = new DietaryPlanner(generator).generate(recipeBook.getRecipes(), effective, dietarySettings.get());
        } catch (IllegalArgumentException e) {
            // The generator reports user-fixable problems this way; show them like any other input error.
            LOGGER.fine(() -> "Meal-plan generation rejected for " + request.days() + " days");
            String message = e.getMessage();
            //@@author neil0618
            if (usesSavedTargets) {
                message += System.lineSeparator() + "Daily targets applied: " + effective.targets().summarise()
                        + " (including your saved targets). Change those with 'set-targets'.";
            }
            //@@author
            throw new MishMashException(message, e);
        }

        // Store the effective request, so view-plan and set-diet repair use the targets actually applied.
        activePlan.setGenerated(plan, effective, Optional.empty());
        LOGGER.fine(() -> "Stored generated meal plan for " + plan.getNumberOfDays() + " days");
        String confirmation = "Meal plan successfully generated for " + plan.getNumberOfDays() + " day(s)!";
        //@@author neil0618
        if (usesSavedTargets) {
            confirmation += System.lineSeparator() + "Daily targets used: "
                    + effective.targets().summarise() + ".";
        }
        //@@author
        return confirmation + System.lineSeparator() + "Use 'view-plan' to inspect your planned meals.";
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        return CommandResult.done(execute(recipeBook));
    }

    //@@author Rixte
    /**
     * Requests agreement before replacing an existing plan and discarding its groceries.
     * Reads the latest shared state without generating or publishing a replacement.
     */
    @Override
    public Optional<String> confirmationPrompt(RecipeBook recipeBook) {
        if (activePlan.get().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Replace the current meal plan and clear its groceries? (y/n)");
    }
    //@@author

    //@@author neil0618
    /**
     * Returns whether a saved target fills a nutrient that the command itself left without a target.
     */
    private boolean usesSavedTarget(NutritionTargets saved) {
        for (Nutrient nutrient : Nutrient.values()) {
            if (saved.get(nutrient) != null && request.targets().get(nutrient) == null) {
                return true;
            }
        }
        return false;
    }
    //@@author
}
