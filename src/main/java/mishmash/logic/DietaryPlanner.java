package mishmash.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import mishmash.model.DayPlan;
import mishmash.model.DietaryPreferences;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;

/**
 * Applies dietary filtering while retaining the existing generator's nutrition and variety rules.
 */
public final class DietaryPlanner {
    /** Preserves the existing daily variety and nutrition selection rules. */
    private final MealPlanGenerator generator;

    /**
     * Creates a dietary planner using the supplied meal-selection generator.
     *
     * @param generator The generator that enforces daily variety and nutrition targets.
     * @throws NullPointerException If the generator is null.
     */
    public DietaryPlanner(MealPlanGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    /**
     * Generates a plan using only recipes permitted by the dietary profile.
     *
     * <p>The existing generator continues to enforce three distinct recipes per day and all supplied
     * nutrition targets. This method does not change the recipe list or application state.
     *
     * @param recipes The available recipes, including any unclassified recipes.
     * @param request The validated number of days and daily nutrition targets.
     * @param preferences The complete dietary profile to enforce.
     * @return A generated plan satisfying the request and dietary requirements.
     * @throws NullPointerException If an argument or recipe is null.
     * @throws IllegalArgumentException If too few eligible recipes exist or no candidate day meets the targets.
     * @throws AssertionError If the underlying generator violates a planning invariant and assertions are enabled.
     */
    public MealPlan generate(List<Recipe> recipes, GeneratePlanRequest request,
            DietaryPreferences preferences) {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(preferences, "preferences");
        List<Recipe> eligibleRecipes = recipes.stream().filter(preferences::isAllowed).toList();
        if (preferences.isRestricted() && eligibleRecipes.size() < MealPlanGenerator.MEALS_PER_DAY) {
            throw new PlanGenerationException("At least " + MealPlanGenerator.MEALS_PER_DAY
                    + " compatible, classified recipes are required; found " + eligibleRecipes.size()
                    + ". Add or classify more suitable recipes.");
        }
        MealPlan plan = generator.generate(eligibleRecipes, request.days(), request.targets());
        assert plan.getNumberOfDays() == request.days() : "Generated plan length must match the request";
        assert isCompatible(plan, preferences) : "Every generated recipe must satisfy dietary preferences";
        assert plan.getDays().stream().allMatch(request.targets()::isSatisfiedBy)
                : "Generated days must satisfy the original nutrition targets";
        return plan;
    }

    /**
     * Checks whether every meal in a plan satisfies the dietary profile.
     *
     * @param plan The plan to evaluate.
     * @param preferences The dietary profile to apply.
     * @return True if every recipe is permitted.
     * @throws NullPointerException If the plan or preferences are null.
     */
    public boolean isCompatible(MealPlan plan, DietaryPreferences preferences) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(preferences, "preferences");
        return plan.getDays().stream().allMatch(day -> isCompatible(day, preferences));
    }

    /**
     * Checks whether all recipes in a day satisfy the dietary profile.
     *
     * @param day The day to evaluate.
     * @param preferences The requirements that each recipe must satisfy.
     * @return True if every recipe in the day is permitted.
     */
    private boolean isCompatible(DayPlan day, DietaryPreferences preferences) {
        return day.getPlannedMeals().stream().allMatch(preferences::isPlannedMealAllowed);
    }

    /**
     * Replaces conflicting days while preserving compatible days in their original positions.
     *
     * <p>Replacements use the original daily nutrition targets. The original plan is never modified;
     * if it already matches, the same plan object is returned without generating replacements.
     *
     * @param recipes The current pool of recipes available for replacement.
     * @param currentPlan The existing plan to inspect.
     * @param originalRequest The settings used to generate the existing plan.
     * @param preferences The newly requested dietary profile.
     * @return A compatible plan of the original length, reusing compatible day objects.
     * @throws NullPointerException If an argument or a recipe used for generation is null.
     * @throws IllegalArgumentException If the request length is inconsistent or replacement generation fails.
     * @throws AssertionError If a repair invariant fails and assertions are enabled.
     */
    public MealPlan repair(List<Recipe> recipes, MealPlan currentPlan,
            GeneratePlanRequest originalRequest, DietaryPreferences preferences) {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(currentPlan, "currentPlan");
        Objects.requireNonNull(originalRequest, "originalRequest");
        Objects.requireNonNull(preferences, "preferences");
        if (currentPlan.getNumberOfDays() != originalRequest.days()) {
            throw new IllegalArgumentException("Plan length must match its generation request.");
        }
        int affectedDayCount = 0;
        for (DayPlan day : currentPlan.getDays()) {
            if (!isCompatible(day, preferences)) {
                affectedDayCount++;
            }
        }
        if (affectedDayCount == 0) {
            return currentPlan;
        }
        GeneratePlanRequest request = new GeneratePlanRequest(affectedDayCount, originalRequest.targets());
        Iterator<DayPlan> replacements = generate(recipes, request, preferences).getDays().iterator();
        List<DayPlan> updatedDays = new ArrayList<>();
        for (DayPlan day : currentPlan.getDays()) {
            updatedDays.add(isCompatible(day, preferences) ? day : replacements.next());
        }
        assert !replacements.hasNext() : "Each generated day must replace exactly one conflicting day";
        MealPlan updatedPlan = new MealPlan(updatedDays);
        assert updatedPlan.getNumberOfDays() == currentPlan.getNumberOfDays()
                : "Repair must preserve plan length";
        assert isCompatible(updatedPlan, preferences) : "Repaired plans must satisfy dietary preferences";
        return updatedPlan;
    }
}
