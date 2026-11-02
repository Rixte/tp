package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Checks planning identity, atomic failures, exact ingredient changes, and independent nutrition targets. */
class OwnedPlanningCoverageTest {
    private final Recipe recipe = new Recipe("Rice", 100, 10, 20, 5,
            List.of(new GroceryItem("Rice", BigDecimal.ONE, "g")));

    @Test
    void withAdjustedPortion_existingCount_preservesDayAndPlanIdentity() {
        DayPlan day = new DayPlan(List.of(recipe));
        MealPlan plan = new MealPlan(List.of(day));
        assertSame(day, day.withAdjustedPortion(0, 1));
        assertSame(plan, plan.withAdjustedPortion(0, 0, 1));
    }

    @Test
    void withIngredients_quantityAndUnitChangesRemainSignificant() {
        PlannedMeal meal = PlannedMeal.onePortionOf(recipe);
        GroceryItem changedQuantity = new GroceryItem("Rice", BigDecimal.TEN, "g");
        GroceryItem changedUnit = new GroceryItem("Rice", BigDecimal.ONE, "G");
        assertEquals(List.of(changedQuantity), meal.withIngredients(List.of(changedQuantity)).getIngredients());
        assertEquals(List.of(changedUnit), meal.withIngredients(List.of(changedUnit)).getIngredients());
    }

    @Test
    void update_changedLength_rejectsWithoutInvalidatingGroceries() {
        ActiveMealPlan active = new ActiveMealPlan();
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        assertThrows(IllegalStateException.class, () -> active.update(plan));
        assertTrue(active.get().isEmpty());
        active.setGenerated(plan, new GeneratePlanRequest(1, NutritionTargets.none()), Optional.of(List.of()));
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> active.update(new MealPlan(List.of())));
        assertSame(plan, active.get().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        active.set(plan);
        active.update(plan.withAdjustedPortion(0, 0, 2));
        assertTrue(active.getRequest().isEmpty());
    }

    @Test
    void targets_lastNutrientMismatchAndOverrides_checkAllNutrientsIndependently() {
        DayPlan day = new DayPlan(List.of(recipe));
        assertFalse(new NutritionTargets(100, 10, 20, 100).isSatisfiedBy(day));
        NutritionTargets original = new NutritionTargets(100, 10, 20, 5);
        NutritionTargets updated = original.overriddenBy(new NutritionTargets(null, null, 50, 25));
        assertEquals(100, updated.get(Nutrient.CALORIES));
        assertEquals(10, updated.get(Nutrient.PROTEIN));
        assertEquals(50, updated.get(Nutrient.CARBS));
        assertEquals(25, updated.get(Nutrient.FATS));
        assertEquals(20, original.get(Nutrient.CARBS));
    }

    @Test
    void nutrition_brokenRecipe_assertsBeforeReportingNegativeTotals() {
        Recipe broken = new Recipe("Broken", 0, 0, 0, 0, recipe.getIngredients()) {
            @Override
            public int getCalories() {
                return -1;
            }
        };
        assertThrows(AssertionError.class, () -> PlannedMeal.onePortionOf(broken).getCalories());
    }

    @Test
    void dayConstructor_wrongInternalMode_assertsInvariant() throws Exception {
        var constructor = DayPlan.class.getDeclaredConstructor(List.class, boolean.class);
        constructor.setAccessible(true);
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance(List.of(PlannedMeal.onePortionOf(recipe)), false));
        assertTrue(error.getCause() instanceof AssertionError);
    }

    @Test
    void update_inconsistentReplacementLength_rejectsWithoutPublishingBrokenPlan() {
        ActiveMealPlan active = new ActiveMealPlan();
        MealPlan original = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        active.setGenerated(original, new GeneratePlanRequest(1, NutritionTargets.none()), Optional.empty());
        MealPlan inconsistent = new MealPlan(original.getDays()) {
            private int reads;

            @Override
            public int getNumberOfDays() {
                return ++reads == 1 ? 1 : 2;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> active.update(inconsistent));
        assertSame(original, active.get().orElseThrow());
    }
}
