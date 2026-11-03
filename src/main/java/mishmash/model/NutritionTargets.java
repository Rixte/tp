package mishmash.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Optional daily nutrition targets for a generated meal plan.
 *
 * <p>Each target may be {@code null}, meaning "no target". A day's meals satisfy the
 * targets when every supplied target is matched within {@link #TOLERANCE} (±15%).</p>
 */
public class NutritionTargets {
    /** Allowed relative deviation from each target, e.g. 0.15 means ±15%. */
    public static final double TOLERANCE = 0.15;

    private final Integer calories;
    private final Integer protein;
    private final Integer carbs;
    private final Integer fats;

    /**
     * Creates a set of daily targets. Pass {@code null} for any target that is not set.
     *
     * @param calories Daily calories in kcal, or null.
     * @param protein Daily protein in grams, or null.
     * @param carbs Daily carbohydrates in grams, or null.
     * @param fats Daily fats in grams, or null.
     * @throws IllegalArgumentException If a supplied target is not positive.
     */
    public NutritionTargets(Integer calories, Integer protein, Integer carbs, Integer fats) {
        if (isNotPositive(calories) || isNotPositive(protein) || isNotPositive(carbs) || isNotPositive(fats)) {
            throw new IllegalArgumentException("Nutrition targets must be positive.");
        }
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fats = fats;
    }

    /**
     * Returns targets with nothing set, which means a purely random plan.
     */
    public static NutritionTargets none() {
        return new NutritionTargets(null, null, null, null);
    }

    /**
     * Returns true if at least one target was supplied.
     */
    public boolean hasAnyTarget() {
        return calories != null || protein != null || carbs != null || fats != null;
    }

    /**
     * Returns true if the given day's meals are within tolerance of every supplied target.
     *
     * @param day The day to check.
     * @return Whether every supplied target is matched within {@link #TOLERANCE}.
     * @throws NullPointerException If the day is null.
     */
    public boolean isSatisfiedBy(DayPlan day) {
        Objects.requireNonNull(day, "day");
        return isSatisfiedBy(day.calculateDailyNutritionTotals());
    }

    //@@author GauthamNandi
    /**
     * Returns true if the given totals are within tolerance of every supplied target.
     * Taking totals lets the generator test a combination without building a {@link DayPlan} for it.
     *
     * @param totals The nutrition totals to check.
     * @return Whether every supplied target is matched within {@link #TOLERANCE}.
     * @throws NullPointerException If the totals are null.
     */
    public boolean isSatisfiedBy(NutritionTotals totals) {
        Objects.requireNonNull(totals, "totals");
        return meets(calories, totals.calories()) && meets(protein, totals.protein())
                && meets(carbs, totals.carbs()) && meets(fats, totals.fats());
    }

    /**
     * Returns whether a total is within {@link #TOLERANCE} of a target. This is the one definition of
     * the rule, so generation and the failure advice cannot disagree about what counts as a match.
     *
     * @param target The positive target.
     * @param actual The total to compare with it.
     * @return True if the relative distance from the target is at most {@link #TOLERANCE}.
     */
    public static boolean isWithinTolerance(int target, long actual) {
        return Math.abs(actual - target) / (double) target <= TOLERANCE;
    }

    /**
     * Returns whether a total meets a target, which is always true when no target was supplied.
     */
    private static boolean meets(Integer target, long actual) {
        return target == null || isWithinTolerance(target, actual);
    }
    //@@author

    /**
     * Calculates how far each daily total exceeds its exact target, without applying generation tolerance.
     *
     * @param totals The day's nutrition totals.
     * @return The positive excess for each nutrient, or zero when its target is unset or not exceeded.
     * @throws NullPointerException If the totals are null.
     */
    public NutritionTotals calculateExcesses(NutritionTotals totals) {
        Objects.requireNonNull(totals, "totals");
        return new NutritionTotals(excess(calories, totals.calories()), excess(protein, totals.protein()),
                excess(carbs, totals.carbs()), excess(fats, totals.fats()));
    }

    /**
     * Returns the positive excess over a supplied target, or zero if no excess exists.
     */
    private static long excess(Integer target, long actual) {
        if (target == null || actual <= target) {
            return 0;
        }
        return actual - target;
    }

    //@@author neil0618
    /**
     * Returns the target for one nutrient, or null if none was set.
     *
     * @param nutrient The nutrient to look up.
     * @return The daily target in kcal or grams, or null.
     * @throws NullPointerException If the nutrient is null.
     */
    public Integer get(Nutrient nutrient) {
        return switch (Objects.requireNonNull(nutrient, "nutrient")) {
            case CALORIES -> calories;
            case PROTEIN -> protein;
            case CARBS -> carbs;
            case FATS -> fats;
        };
    }

    /**
     * Combines these targets with explicit ones, where an explicit target wins for the same nutrient.
     *
     * <p>{@code generate-plan} uses this with the saved targets as the base: a target typed in the
     * command replaces the saved one, and a nutrient the command leaves out keeps the saved target.</p>
     *
     * @param explicit The targets that take priority; a null target in it means "keep this object's".
     * @return New targets; neither this object nor the argument is modified.
     * @throws NullPointerException If the explicit targets are null.
     */
    public NutritionTargets overriddenBy(NutritionTargets explicit) {
        Objects.requireNonNull(explicit, "explicit");
        return new NutritionTargets(
                explicit.calories != null ? explicit.calories : calories,
                explicit.protein != null ? explicit.protein : protein,
                explicit.carbs != null ? explicit.carbs : carbs,
                explicit.fats != null ? explicit.fats : fats);
    }

    /**
     * Lists the targets that are set on one line, e.g. {@code "2000 kcal, 150 g protein"}.
     *
     * @return The set targets in the order calories, protein, carbs, fats, or {@code "none"} if nothing is set.
     */
    public String summarise() {
        StringBuilder summary = new StringBuilder();
        for (Nutrient nutrient : Nutrient.values()) {
            Integer target = get(nutrient);
            if (target == null) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(target).append(' ').append(nutrient.getUnit());
            if (nutrient != Nutrient.CALORIES) {
                summary.append(' ').append(nutrient.getLabel().toLowerCase(Locale.ROOT));
            }
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }
    //@@author

    /**
     * Returns the daily calorie target in kcal, or null if none was set.
     */
    public Integer getCalories() {
        return calories;
    }

    /**
     * Returns the daily protein target in grams, or null if none was set.
     */
    public Integer getProtein() {
        return protein;
    }

    /**
     * Returns the daily carbohydrate target in grams, or null if none was set.
     */
    public Integer getCarbs() {
        return carbs;
    }

    /**
     * Returns the daily fat target in grams, or null if none was set.
     */
    public Integer getFats() {
        return fats;
    }

    /**
     * Returns whether a supplied target is invalid; an omitted target is allowed.
     */
    private static boolean isNotPositive(Integer target) {
        return target != null && target <= 0;
    }
}
