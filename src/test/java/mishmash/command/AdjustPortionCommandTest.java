package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.PlannedMeal;
import mishmash.model.PortionAdjustment;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

/**
 * Checks position validation, atomic plan replacement, and grocery invalidation for portion adjustments.
 */
class AdjustPortionCommandTest {
    private final Recipe rice = new Recipe("Rice", 200, 4, 45, 1,
            List.of(new GroceryItem("Rice", new BigDecimal("100"), "g")));
    private final Recipe beans = new Recipe("Beans", 300, 20, 40, 5,
            List.of(new GroceryItem("Beans", new BigDecimal("50"), "g")));
    private final RecipeBook recipeBook = new RecipeBook();
    private final ActiveMealPlan activePlan = new ActiveMealPlan();

    @Test
    void execute_noPlan_returnsPromptWithoutChangingState() throws MishMashException {
        String result = command(1, 1, 2).execute(recipeBook);

        assertEquals("No active meal plan. Use 'generate-plan' first.", result);
        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_emptyPlan_returnsSamePromptWithoutChangingState() throws MishMashException {
        MealPlan emptyPlan = new MealPlan(List.of());
        activePlan.set(emptyPlan);

        assertEquals("No active meal plan. Use 'generate-plan' first.", command(1, 1, 2).execute(recipeBook));
        assertSame(emptyPlan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_emptyDay_reportsThatNoMealCanBeAdjusted() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of())));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, 2).execute(recipeBook));

        assertEquals("Day 1 has no meals to adjust.", error.getExplanation());
        assertEquals(AdjustPortionCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_invalidDayOrMeal_preservesPlanRequestAndGroceries() {
        MealPlan plan = createGeneratedPlan();
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        MishMashException dayError = assertThrows(MishMashException.class,
                () -> command(2, 1, 2).execute(recipeBook));
        MishMashException mealError = assertThrows(MishMashException.class,
                () -> command(1, 3, 2).execute(recipeBook));
        assertEquals(AdjustPortionCommand.USAGE, dayError.getUsage().orElseThrow());
        assertEquals(AdjustPortionCommand.USAGE, mealError.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_changedPortion_replacesOnlySelectedStructuresAndClearsGroceries() throws MishMashException {
        MealPlan originalPlan = createGeneratedPlan();
        DayPlan originalDay = originalPlan.getDays().get(0);
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();

        CommandResult result = command(1, 2, 3).executeWithResult(recipeBook);

        MealPlan updatedPlan = activePlan.get().orElseThrow();
        DayPlan updatedDay = updatedPlan.getDays().get(0);
        assertEquals("Meal 2 on Day 1 updated to 3 portions." + System.lineSeparator()
                + "The previous grocery list was cleared. Run 'generate-list' to update it.", result.feedback());
        assertEquals(CommandResult.Status.DONE, result.status());
        assertEquals(1, originalDay.getPlannedMeals().get(1).getPortionCount());
        assertEquals(3, updatedDay.getPlannedMeals().get(1).getPortionCount());
        assertSame(originalDay.getPlannedMeals().get(0), updatedDay.getPlannedMeals().get(0));
        assertEquals(1100, updatedDay.getTotalCalories());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_samePortion_isNoOpAndPreservesGrocerySnapshot() throws MishMashException {
        MealPlan plan = createGeneratedPlan();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        CommandResult result = command(1, 1, 1).executeWithResult(recipeBook);

        assertEquals("Meal 1 on Day 1 already has 1 portion. Nothing was changed.", result.feedback());
        assertEquals(CommandResult.Status.NOTE, result.status());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_nutritionOverflow_preservesPlanRequestAndGroceries() {
        Recipe huge = new Recipe("Huge", Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(rice.getIngredients().get(0)));
        DayPlan day = DayPlan.fromPlannedMeals(List.of(
                new PlannedMeal(huge, Integer.MAX_VALUE),
                new PlannedMeal(huge, Integer.MAX_VALUE),
                PlannedMeal.onePortionOf(huge)));
        MealPlan plan = new MealPlan(List.of(day));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice.getIngredients().get(0))));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 3, Integer.MAX_VALUE).execute(recipeBook));

        assertEquals("That portion count makes the day's nutrition totals too large.", error.getExplanation());
        assertEquals(AdjustPortionCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void constructor_nullDependencies_rejectsImmediately() {
        PortionAdjustment adjustment = new PortionAdjustment(1, 1, 1);
        assertThrows(NullPointerException.class, () -> new AdjustPortionCommand(null, activePlan));
        assertThrows(NullPointerException.class, () -> new AdjustPortionCommand(adjustment, null));
    }

    /**
     * Creates one generated day with a grocery snapshot and returns its original plan.
     */
    private MealPlan createGeneratedPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice, beans))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice.getIngredients().get(0))));
        return plan;
    }

    /**
     * Creates a portion command connected to this test's shared active plan.
     */
    private AdjustPortionCommand command(int dayNumber, int mealNumber, int portionCount) {
        return new AdjustPortionCommand(
                new PortionAdjustment(dayNumber, mealNumber, portionCount), activePlan);
    }
}
