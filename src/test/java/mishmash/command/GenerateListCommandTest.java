package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.PlannedMeal;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.LogCapture;

/**
 * Checks ingredient aggregation from planned meals and safe replacement of stored groceries.
 */
class GenerateListCommandTest {
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final RecipeBook book = new RecipeBook();
    private final GenerateListCommand command = new GenerateListCommand(activePlan);

    @Test
    void execute_generationFailure_preservesAllStateAndDoesNotLogSuccess() {
        GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");
        Recipe invalid = new Recipe("Broken recipe", 100, 10, 20, 5, List.of(rice)) {
            @Override
            public List<GroceryItem> getIngredients() {
                return Arrays.asList(rice, null);
            }
        };
        book.add(invalid);
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(invalid))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice)));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        List<Recipe> originalRecipes = book.getRecipes();
        try (LogCapture logs = new LogCapture(GenerateListCommand.class)) {
            assertThrows(NullPointerException.class, () -> command.execute(book));

            assertSame(plan, activePlan.get().orElseThrow());
            assertSame(request, activePlan.getRequest().orElseThrow());
            assertSame(groceries, activePlan.getGroceryList().orElseThrow());
            assertEquals(originalRecipes, book.getRecipes());
            assertTrue(logs.getRecords().isEmpty());
        }
    }

    @Test
    void execute_noPlan_returnsPromptWithoutStoringGroceries() {
        assertEquals("No active meal plan. Use 'generate-plan' first.", command.execute(book));
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_repeatedMeals_combinesDecimalsAndKeepsUnitsAndCaseSeparate() {
        Recipe rice = createRecipe(new GroceryItem("Rice", new BigDecimal("0.1"), "g"));
        Recipe other = createRecipe(new GroceryItem("Rice", new BigDecimal("0.2"), "g"),
                new GroceryItem("Rice", BigDecimal.ONE, "kg"),
                new GroceryItem("rice", BigDecimal.ONE, "g"));
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice, other)), new DayPlan(List.of(rice))));
        activePlan.set(plan);
        book.add(rice);

        assertEquals("Grocery list generated. Use 'view-list' to view it.", command.execute(book));
        String expected = String.join(System.lineSeparator(),
                "================ Grocery List ================",
                "[ ] Rice: 0.4g", "[ ] Rice: 1kg", "[ ] rice: 1g",
                "=============================================", "Total items to purchase: 3");
        ViewListCommand viewer = new ViewListCommand(activePlan::getGroceryList);
        assertEquals(expected, viewer.execute(book));
        command.execute(book);
        assertEquals(expected, viewer.execute(book));
        assertEquals(plan, activePlan.get().orElseThrow());
        assertEquals(List.of(rice), book.getRecipes());
        assertEquals(new BigDecimal("0.1"), rice.getIngredients().get(0).getQuantity());
    }

    @Test
    void execute_emptyPlan_storesPresentEmptyList() {
        activePlan.set(new MealPlan(List.of()));
        command.execute(book);

        assertEquals(List.of(), activePlan.getGroceryList().orElseThrow());
        assertTrue(new ViewListCommand(activePlan::getGroceryList).execute(book)
                .endsWith("Total items to purchase: 0"));
    }

    @Test
    void execute_planChangedAfterConstruction_readsLatestMeals() {
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(
                createRecipe(new GroceryItem("Beans", BigDecimal.ONE, "g")))))));
        command.execute(book);

        assertEquals("Beans", activePlan.getGroceryList().orElseThrow().get(0).getName());
    }

    @Test
    void execute_adjustedPortions_scalesBeforeCombiningIngredients() {
        Recipe meal = createRecipe(new GroceryItem("Rice", new BigDecimal("12.5"), "g"));
        DayPlan day = DayPlan.fromPlannedMeals(List.of(new PlannedMeal(meal, 4)));
        activePlan.set(new MealPlan(List.of(day)));

        command.execute(book);

        assertEquals(new BigDecimal("50.0"), activePlan.getGroceryList().orElseThrow().get(0).getQuantity());
        assertEquals(new BigDecimal("12.5"), meal.getIngredients().get(0).getQuantity());
    }

    @Test
    void execute_customizedIngredients_usesOverrideWithoutChangingRecipe() {
        Recipe meal = createRecipe(new GroceryItem("Rice", new BigDecimal("100"), "g"));
        PlannedMeal customized = new PlannedMeal(meal, 2).withIngredients(
                List.of(new GroceryItem("Tofu", new BigDecimal("75.5"), "g")));
        activePlan.set(new MealPlan(List.of(DayPlan.fromPlannedMeals(List.of(customized)))));

        command.execute(book);

        assertEquals("Tofu", activePlan.getGroceryList().orElseThrow().get(0).getName());
        assertEquals(new BigDecimal("151.0"), activePlan.getGroceryList().orElseThrow().get(0).getQuantity());
        assertEquals("Rice", meal.getIngredients().get(0).getName());
    }

    @Test
    void constructor_nullPlanHolder_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new GenerateListCommand(null));
    }

    /**
     * Creates a recipe with the supplied ingredients for aggregation tests.
     */
    private Recipe createRecipe(GroceryItem... ingredients) {
        return new Recipe("Meal", 0, 0, 0, 0, List.of(ingredients));
    }
}
