package mishmash.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * The meals chosen for a single day of a meal plan, with their nutrition totals.
 */
public class DayPlan {
    private final List<PlannedMeal> plannedMeals;

    /**
     * Creates a day plan from the given meals.
     *
     * @param meals The recipes eaten on this day, in order; copied so later changes do not leak in.
     * @throws NullPointerException If the list or any meal is null.
     */
    public DayPlan(List<Recipe> meals) {
        Objects.requireNonNull(meals, "meals");
        plannedMeals = meals.stream().map(PlannedMeal::onePortionOf).toList();
    }

    /**
     * Creates a defensive snapshot of portioned meals and verifies that its totals are representable.
     */
    private DayPlan(List<PlannedMeal> plannedMeals, boolean isPortioned) {
        Objects.requireNonNull(plannedMeals, "plannedMeals");
        assert isPortioned : "The private constructor accepts portioned meals only";
        this.plannedMeals = List.copyOf(plannedMeals);
        calculateDailyNutritionTotals();
    }

    /**
     * Creates a day plan from already portioned meals.
     *
     * @param plannedMeals The portioned meals in their planned order.
     * @return A day plan that owns an unmodifiable copy of the supplied meals.
     * @throws NullPointerException If the list or any planned meal is null.
     * @throws ArithmeticException If the combined nutrition cannot be represented as long values.
     */
    public static DayPlan fromPlannedMeals(List<PlannedMeal> plannedMeals) {
        return new DayPlan(plannedMeals, true);
    }

    /**
     * Returns an unmodifiable list of this day's meals.
     */
    public List<Recipe> getMeals() {
        return plannedMeals.stream().map(PlannedMeal::getRecipe).toList();
    }

    /**
     * Returns an unmodifiable list of this day's portion-aware planned meals.
     */
    public List<PlannedMeal> getPlannedMeals() {
        return plannedMeals;
    }

    /**
     * Returns a new day plan with one selected meal's portion count changed.
     *
     * @param mealIndex The zero-based meal position to update.
     * @param portionCount The new positive whole-recipe portion count.
     * @return A new day plan, or this day when the portion count is unchanged.
     * @throws IndexOutOfBoundsException If the meal index does not exist.
     * @throws IllegalArgumentException If the portion count is not positive.
     * @throws ArithmeticException If the changed nutrition totals cannot be represented as long values.
     */
    public DayPlan withAdjustedPortion(int mealIndex, int portionCount) {
        PlannedMeal currentMeal = plannedMeals.get(mealIndex);
        PlannedMeal updatedMeal = currentMeal.withPortionCount(portionCount);
        if (updatedMeal == currentMeal) {
            return this;
        }

        List<PlannedMeal> updatedMeals = new ArrayList<>(plannedMeals);
        updatedMeals.set(mealIndex, updatedMeal);
        return DayPlan.fromPlannedMeals(updatedMeals);
    }

    //@@author neil0618
    /**
     * Returns a new day plan with one selected meal swapped for another planned meal.
     *
     * @param mealIndex The zero-based meal position to replace.
     * @param replacement The planned meal to put in that position.
     * @return A new day plan, or this day when the same planned meal is already there.
     * @throws IndexOutOfBoundsException If the meal index does not exist.
     * @throws NullPointerException If the replacement is null.
     * @throws ArithmeticException If the changed nutrition totals cannot be represented as long values.
     */
    public DayPlan withReplacedMeal(int mealIndex, PlannedMeal replacement) {
        Objects.requireNonNull(replacement, "replacement");
        PlannedMeal currentMeal = plannedMeals.get(mealIndex);
        if (currentMeal == replacement) {
            return this;
        }

        List<PlannedMeal> updatedMeals = new ArrayList<>(plannedMeals);
        updatedMeals.set(mealIndex, replacement);
        return DayPlan.fromPlannedMeals(updatedMeals);
    }
    //@@author

    /**
     * Returns a new day plan with one selected meal's complete ingredient replacement.
     *
     * @param mealIndex The zero-based meal position to update.
     * @param ingredients The nonempty one-portion ingredient replacement.
     * @return A new day plan, or this day when the effective ingredients are unchanged.
     * @throws IndexOutOfBoundsException If the meal index does not exist.
     * @throws NullPointerException If the list or any ingredient is null.
     * @throws IllegalArgumentException If the list is empty or a quantity is not positive.
     */
    public DayPlan withCustomizedIngredients(int mealIndex, List<GroceryItem> ingredients) {
        PlannedMeal currentMeal = plannedMeals.get(mealIndex);
        PlannedMeal updatedMeal = currentMeal.withIngredients(ingredients);
        if (updatedMeal == currentMeal) {
            return this;
        }

        List<PlannedMeal> updatedMeals = new ArrayList<>(plannedMeals);
        updatedMeals.set(mealIndex, updatedMeal);
        return DayPlan.fromPlannedMeals(updatedMeals);
    }

    /**
     * Calculates the nutrition totals for every meal occurrence in this day, including repeated recipes.
     * Returns zero for each nutrient when the day has no meals.
     *
     * @return The daily calories in kilocalories and protein, carbohydrates, and fats in grams.
     */
    public NutritionTotals calculateDailyNutritionTotals() {
        return new NutritionTotals(getTotalCalories(), getTotalProtein(), getTotalCarbs(), getTotalFats());
    }

    /**
     * Returns the total kilocalories of all meals in the day.
     */
    public long getTotalCalories() {
        return sumNutrition(PlannedMeal::getCalories);
    }

    /**
     * Returns the total grams of protein of all meals in the day.
     */
    public long getTotalProtein() {
        return sumNutrition(PlannedMeal::getProtein);
    }

    /**
     * Returns the total grams of carbohydrates of all meals in the day.
     */
    public long getTotalCarbs() {
        return sumNutrition(PlannedMeal::getCarbs);
    }

    /**
     * Returns the total grams of fat of all meals in the day.
     */
    public long getTotalFats() {
        return sumNutrition(PlannedMeal::getFats);
    }

    /**
     * Adds one nutrition value across all meals using exact arithmetic to reject overflow.
     */
    private long sumNutrition(ToLongFunction<PlannedMeal> valueSource) {
        long total = 0;
        for (PlannedMeal meal : plannedMeals) {
            total = Math.addExact(total, valueSource.applyAsLong(meal));
        }
        return total;
    }
}
