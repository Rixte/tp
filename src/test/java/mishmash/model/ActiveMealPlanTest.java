package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Checks grocery snapshot ownership and invalidation when the active plan changes.
 */
class ActiveMealPlanTest {
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");

    @Test
    void setGroceryList_mutableInput_storesIndependentReadOnlySnapshot() {
        List<GroceryItem> items = new ArrayList<>(List.of(rice));
        activePlan.setGroceryList(items);
        items.clear();

        assertEquals(List.of(rice), activePlan.getGroceryList().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> activePlan.getGroceryList().orElseThrow().clear());
    }

    @Test
    void set_invalidInput_preservesPlanAndGroceries() {
        MealPlan plan = new MealPlan(List.of());
        activePlan.set(plan);
        activePlan.setGroceryList(List.of(rice));

        assertThrows(NullPointerException.class, () -> activePlan.set(null));
        assertThrows(NullPointerException.class, () -> activePlan.setGroceryList(null));
        assertThrows(NullPointerException.class, () -> activePlan.setGroceryList(Arrays.asList(rice, null)));
        assertEquals(plan, activePlan.get().orElseThrow());
        assertEquals(List.of(rice), activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void set_newPlan_clearsGroceriesWhileOldSnapshotRemainsReadable() {
        activePlan.setGroceryList(List.of(rice));
        List<GroceryItem> snapshot = activePlan.getGroceryList().orElseThrow();
        activePlan.set(new MealPlan(List.of()));

        assertTrue(activePlan.getGroceryList().isEmpty());
        assertEquals(List.of(rice), snapshot);
    }

    @Test
    void update_validReplacement_preservesRequestAndClearsGroceries() {
        MealPlan original = new MealPlan(List.of(new DayPlan(List.of(createRecipe()))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(original, request, Optional.of(List.of(rice)));
        MealPlan replacement = original.withAdjustedPortion(0, 0, 2);

        activePlan.update(replacement);

        assertEquals(replacement, activePlan.get().orElseThrow());
        assertEquals(request, activePlan.getRequest().orElseThrow());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void update_invalidReplacement_preservesAllState() {
        MealPlan original = new MealPlan(List.of(new DayPlan(List.of(createRecipe()))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(original, request, Optional.of(List.of(rice)));

        assertThrows(NullPointerException.class, () -> activePlan.update(null));
        assertThrows(IllegalArgumentException.class, () -> activePlan.update(new MealPlan(List.of())));
        assertEquals(original, activePlan.get().orElseThrow());
        assertEquals(request, activePlan.getRequest().orElseThrow());
        assertEquals(List.of(rice), activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void update_samePlan_preservesExistingGroceries() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(createRecipe()))));
        activePlan.set(plan);
        activePlan.setGroceryList(List.of(rice));

        activePlan.update(plan);

        assertEquals(List.of(rice), activePlan.getGroceryList().orElseThrow());
    }

    //@@author GauthamNandi
    @Test
    void update_noActivePlan_isRejectedAndLeavesNoPlan() {
        MealPlan replacement = new MealPlan(List.of(new DayPlan(List.of(createRecipe()))));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> activePlan.update(replacement));

        assertEquals("An active meal plan is required before it can be updated.", error.getMessage());
        assertTrue(activePlan.get().isEmpty());
    }

    /**
     * Normal plans cannot change length, so this uses {@link ResizedPlan} to reach the guard that protects
     * the stored request if the plan and its request ever disagree.
     */
    @Test
    void update_lengthDiffersFromRequest_isRejectedAndPreservesAllState() {
        ResizedPlan original = new ResizedPlan();
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(original, request, Optional.of(List.of(rice)));
        original.reportedDays = 2;
        DayPlan day = new DayPlan(List.of(createRecipe()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> activePlan.update(new MealPlan(List.of(day, day))));

        assertEquals("Updated plan length must match its generation request.", error.getMessage());
        assertEquals(original, activePlan.get().orElseThrow());
        assertEquals(request, activePlan.getRequest().orElseThrow());
        assertEquals(List.of(rice), activePlan.getGroceryList().orElseThrow());
    }

    /**
     * A one-day plan whose reported length a test can change after it has been published.
     */
    private static final class ResizedPlan extends MealPlan {
        private int reportedDays = 1;

        private ResizedPlan() {
            super(List.of(new DayPlan(List.of())));
        }

        @Override
        public int getNumberOfDays() {
            return reportedDays;
        }
    }
    //@@author

    /**
     * Creates a minimal recipe using the shared grocery item.
     */
    private Recipe createRecipe() {
        return new Recipe("Rice", 100, 2, 20, 1, List.of(rice));
    }
}
