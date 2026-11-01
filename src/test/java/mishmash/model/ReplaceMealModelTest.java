package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.testutil.TestRecipes;

//@@author neil0618
/**
 * Checks position validation and copy-on-write meal replacement in the plan model.
 */
class ReplaceMealModelTest {
    private static final Recipe OATMEAL = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);
    private static final Recipe CHICKEN = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private static final Recipe SALMON = TestRecipes.recipe("Salmon Salad", 550, 42, 20, 32);

    @Test
    void mealReplacement_positivePositions_keepsEveryValue() {
        MealReplacement replacement = new MealReplacement(2, 3, 4);

        assertEquals(2, replacement.dayNumber());
        assertEquals(3, replacement.mealNumber());
        assertEquals(4, replacement.recipeNumber());
    }

    @Test
    void mealReplacement_nonPositivePosition_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MealReplacement(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MealReplacement(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MealReplacement(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MealReplacement(1, 1, -1));
    }

    @Test
    void dayPlanWithReplacedMeal_differentMeal_replacesOnlySelectedMealAndUpdatesTotals() {
        DayPlan original = new DayPlan(List.of(OATMEAL, CHICKEN));
        PlannedMeal replacement = PlannedMeal.onePortionOf(SALMON);

        DayPlan updated = original.withReplacedMeal(1, replacement);

        assertSame(original.getPlannedMeals().get(0), updated.getPlannedMeals().get(0));
        assertSame(replacement, updated.getPlannedMeals().get(1));
        assertEquals(new NutritionTotals(1000, 77, 80, 40), updated.calculateDailyNutritionTotals());
        assertSame(CHICKEN, original.getPlannedMeals().get(1).getRecipe());
        assertEquals(new NutritionTotals(1100, 85, 130, 23), original.calculateDailyNutritionTotals());
    }

    @Test
    void dayPlanWithReplacedMeal_sameMeal_returnsSameDay() {
        DayPlan day = new DayPlan(List.of(OATMEAL, CHICKEN));

        assertSame(day, day.withReplacedMeal(0, day.getPlannedMeals().get(0)));
    }

    @Test
    void dayPlanWithReplacedMeal_invalidArguments_areRejected() {
        DayPlan day = new DayPlan(List.of(OATMEAL));
        PlannedMeal replacement = PlannedMeal.onePortionOf(SALMON);

        assertThrows(IndexOutOfBoundsException.class, () -> day.withReplacedMeal(1, replacement));
        assertThrows(IndexOutOfBoundsException.class, () -> day.withReplacedMeal(-1, replacement));
        assertThrows(NullPointerException.class, () -> day.withReplacedMeal(0, null));
    }

    @Test
    void dayPlanWithReplacedMeal_overflowingTotals_throwsAndLeavesDayUnchanged() {
        Recipe huge = TestRecipes.recipe("Huge", Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        Recipe empty = TestRecipes.recipe("Water", 0, 0, 0, 0);
        DayPlan day = DayPlan.fromPlannedMeals(List.of(
                new PlannedMeal(huge, Integer.MAX_VALUE),
                new PlannedMeal(huge, Integer.MAX_VALUE),
                new PlannedMeal(huge, 4),
                PlannedMeal.onePortionOf(empty)));

        assertThrows(ArithmeticException.class, () -> day.withReplacedMeal(3, PlannedMeal.onePortionOf(huge)));
        assertSame(empty, day.getPlannedMeals().get(3).getRecipe());
    }

    @Test
    void mealPlanWithReplacedMeal_differentMeal_replacesOnlySelectedDay() {
        MealPlan original = new MealPlan(List.of(
                new DayPlan(List.of(OATMEAL, CHICKEN)),
                new DayPlan(List.of(SALMON, SALMON))));
        PlannedMeal replacement = PlannedMeal.onePortionOf(OATMEAL);

        MealPlan updated = original.withReplacedMeal(1, 0, replacement);

        assertSame(original.getDays().get(0), updated.getDays().get(0));
        assertSame(replacement, updated.getDays().get(1).getPlannedMeals().get(0));
        assertSame(SALMON, updated.getDays().get(1).getPlannedMeals().get(1).getRecipe());
        assertSame(SALMON, original.getDays().get(1).getPlannedMeals().get(0).getRecipe());
        assertEquals(2, updated.getNumberOfDays());
    }

    @Test
    void mealPlanWithReplacedMeal_sameMeal_returnsSamePlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(OATMEAL))));

        assertSame(plan, plan.withReplacedMeal(0, 0, plan.getDays().get(0).getPlannedMeals().get(0)));
    }

    @Test
    void mealPlanWithReplacedMeal_invalidDayIndex_isRejected() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(OATMEAL))));
        PlannedMeal replacement = PlannedMeal.onePortionOf(SALMON);

        assertThrows(IndexOutOfBoundsException.class, () -> plan.withReplacedMeal(1, 0, replacement));
    }
}
//@@author
