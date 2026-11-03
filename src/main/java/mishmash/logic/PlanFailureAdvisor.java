package mishmash.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import mishmash.model.DietaryPreferences;
import mishmash.model.Nutrient;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;

//@@author GauthamNandi
/**
 * Explains why a meal plan could not be generated and suggests what to change.
 *
 * <p>This runs only after generation has failed, so it stays separate from the generator's selection
 * rules. For each target it compares the three lowest and three highest values available, which is a
 * cheap way to prove a target cannot be reached. A target can pass that check and still fail in
 * combination with another target; finding the closest combination would be a more advanced alternative,
 * but a general suggestion is enough to tell the user what to change.</p>
 */
public final class PlanFailureAdvisor {
    private static final String LINE_PREFIX = "- ";

    /**
     * Builds the explanation for a failed generation.
     *
     * @param recipes Every recipe in the book.
     * @param targets The daily targets that were applied.
     * @param preferences The dietary preferences that were applied.
     * @return The lines to show after the error, or an empty list if nothing useful can be added.
     * @throws NullPointerException If an argument or recipe is null.
     */
    public List<String> explain(List<Recipe> recipes, NutritionTargets targets, DietaryPreferences preferences) {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(preferences, "preferences");
        List<Recipe> eligible = recipes.stream().filter(preferences::isAllowed).toList();
        // Diet advice only helps if the diet actually removed recipes.
        boolean hasDietaryExclusions = eligible.size() < recipes.size();
        long unclassified = recipes.stream().filter(recipe -> !recipe.getFoodInfo().isClassified()).count();

        List<String> reasons = new ArrayList<>();
        List<String> nextSteps = new ArrayList<>();
        if (hasDietaryExclusions) {
            reasons.add(describeDietFilter(recipes.size(), eligible.size(), unclassified));
        }
        if (eligible.size() < MealPlanGenerator.MEALS_PER_DAY) {
            int missing = MealPlanGenerator.MEALS_PER_DAY - eligible.size();
            nextSteps.add("Add " + missing + " more suitable recipe(s) with 'add-recipe'.");
            if (hasDietaryExclusions && unclassified > 0) {
                nextSteps.add("Declare food tags on existing recipes with 'edit-recipe INDEX food/TAG'.");
            }
        } else if (targets.hasAnyTarget()) {
            addTargetAdvice(eligible, targets, reasons, nextSteps);
        }
        if (hasDietaryExclusions) {
            nextSteps.add("Widen your diet with 'set-diet' or 'clear-diet' to allow more recipes.");
        }
        return format(reasons, nextSteps);
    }

    /**
     * Says how many recipes the dietary preferences left, and how many were only unclassified.
     */
    private String describeDietFilter(int recipeCount, int eligibleCount, long unclassified) {
        String summary = "Your dietary preferences allow " + eligibleCount + " of your " + recipeCount + " recipes";
        return unclassified == 0 ? summary + "."
                : summary + " (" + unclassified + " excluded for having no food tags).";
    }

    /**
     * Reports each target that no three recipes can reach. Otherwise each target lies between the lowest
     * and highest totals, which does not prove any combination meets it, so the wording claims only that.
     */
    private void addTargetAdvice(List<Recipe> eligible, NutritionTargets targets,
            List<String> reasons, List<String> nextSteps) {
        for (Nutrient nutrient : Nutrient.values()) {
            Integer target = targets.get(nutrient);
            if (target != null) {
                addUnreachableTarget(eligible, nutrient, target, reasons, nextSteps);
            }
        }
        if (nextSteps.isEmpty()) {
            reasons.add("Each target is within the range your recipes can reach, but no "
                    + MealPlanGenerator.MEALS_PER_DAY + " recipes were found that meet all of your targets.");
            nextSteps.add("Relax or remove one target, or add recipes that fit all of them.");
            if (!MealPlanGenerator.checksEveryCombination(eligible.size())) {
                nextSteps.add("Try again: with this many recipes only a random sample of combinations is checked.");
            }
        }
    }

    /**
     * Adds a reason and a next step if the three most extreme recipes still miss the target.
     * The tolerance check is the same one generation uses, {@link NutritionTargets#isWithinTolerance}.
     */
    private void addUnreachableTarget(List<Recipe> eligible, Nutrient nutrient, int target,
            List<String> reasons, List<String> nextSteps) {
        List<Integer> sorted = eligible.stream().map(recipe -> valueOf(recipe, nutrient))
                .sorted(Comparator.naturalOrder()).toList();
        int meals = MealPlanGenerator.MEALS_PER_DAY;
        long lowest = sorted.subList(0, meals).stream().mapToLong(Integer::longValue).sum();
        long highest = sorted.subList(sorted.size() - meals, sorted.size()).stream()
                .mapToLong(Integer::longValue).sum();
        String prefix = nutrient.getPrefix() + "/";
        String unit = " " + nutrient.getUnit();

        if (highest < target && !NutritionTargets.isWithinTolerance(target, highest)) {
            reasons.add(nutrient.getLabel() + ": your target is " + target + unit + ", but your " + meals
                    + " highest recipes total only " + highest + unit + ".");
            nextSteps.add("Lower " + prefix + " or add recipes higher in " + nutrient.getLabel() + ".");
        } else if (lowest > target && !NutritionTargets.isWithinTolerance(target, lowest)) {
            reasons.add(nutrient.getLabel() + ": your target is " + target + unit + ", but your " + meals
                    + " lowest recipes total " + lowest + unit + ".");
            nextSteps.add("Raise " + prefix + " or add recipes lower in " + nutrient.getLabel() + ".");
        }
    }

    /**
     * Returns a recipe's value for one nutrient.
     */
    private static int valueOf(Recipe recipe, Nutrient nutrient) {
        return switch (nutrient) {
            case CALORIES -> recipe.getCalories();
            case PROTEIN -> recipe.getProtein();
            case CARBS -> recipe.getCarbs();
            case FATS -> recipe.getFats();
        };
    }

    /**
     * Lays out the reasons and next steps under their headings, leaving out an empty section.
     */
    private List<String> format(List<String> reasons, List<String> nextSteps) {
        List<String> lines = new ArrayList<>();
        if (!reasons.isEmpty()) {
            lines.add("Why this happened:");
            reasons.forEach(reason -> lines.add(LINE_PREFIX + reason));
        }
        if (!nextSteps.isEmpty()) {
            lines.add("What to try next:");
            nextSteps.forEach(step -> lines.add(LINE_PREFIX + step));
        }
        return lines;
    }
}
//@@author
