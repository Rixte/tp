package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.FoodInfo;
import mishmash.model.FoodTag;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.IngredientCustomization;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

/**
 * Checks atomic ingredient customization, no-op behavior, positions, and dietary safety.
 */
class CustomizeMealCommandTest {
    private final GroceryItem rice = new GroceryItem("Rice", new BigDecimal("100"), "g");
    private final Recipe recipe = new Recipe("Rice bowl", 500, 20, 80, 10,
            List.of(rice), FoodInfo.parseField("none"));
    private final RecipeBook recipeBook = new RecipeBook();
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final DietarySettings dietarySettings = new DietarySettings();

    @Test
    void execute_noOrEmptyPlan_returnsPromptWithoutMutation() throws MishMashException {
        assertEquals("No active meal plan. Use 'generate-plan' first.", command(1, 1, rice).execute(recipeBook));
        MealPlan emptyPlan = new MealPlan(List.of());
        activePlan.set(emptyPlan);

        assertEquals("No active meal plan. Use 'generate-plan' first.", command(1, 1, rice).execute(recipeBook));
        assertSame(emptyPlan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_invalidPositions_preservesAllState() {
        MealPlan plan = createGeneratedPlan();
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        assertThrows(MishMashException.class, () -> command(2, 1, rice).execute(recipeBook));
        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 2, rice).execute(recipeBook));
        assertEquals(CustomizeMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_emptyDay_reportsRecoverableErrorWithoutChangingPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of())));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, rice).execute(recipeBook));

        assertEquals("Day 1 has no meals to customize.", error.getExplanation());
        assertEquals(CustomizeMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_newIngredients_changesOnlyOccurrenceAndClearsGroceries() throws MishMashException {
        MealPlan originalPlan = createGeneratedPlan();
        GroceryItem tofu = new GroceryItem("Tofu", new BigDecimal("150"), "g");

        CommandResult result = command(1, 1, tofu).executeWithResult(recipeBook);

        MealPlan updatedPlan = activePlan.get().orElseThrow();
        assertTrue(result.feedback().startsWith("Meal 1 on Day 1 ingredients customized."));
        assertEquals(CommandResult.Status.DONE, result.status());
        assertEquals(List.of(tofu), updatedPlan.getDays().get(0).getPlannedMeals().get(0).getIngredients());
        assertEquals(List.of(rice), originalPlan.getDays().get(0).getPlannedMeals().get(0).getIngredients());
        assertEquals(List.of(rice), recipe.getIngredients());
        assertTrue(activePlan.getGroceryList().isEmpty());
        assertEquals(500, updatedPlan.getDays().get(0).getTotalCalories());
    }

    @Test
    void execute_sameEffectiveIngredients_isNoOpAndPreservesGroceries() throws MishMashException {
        MealPlan plan = createGeneratedPlan();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        GroceryItem numericallyEqualRice = new GroceryItem("Rice", new BigDecimal("100.0"), "g");

        CommandResult result = command(1, 1, numericallyEqualRice).executeWithResult(recipeBook);

        assertEquals("Meal 1 on Day 1 already uses those ingredients. Nothing was changed.", result.feedback());
        assertEquals(CommandResult.Status.NOTE, result.status());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_reorderedAndRecasedIngredients_changesGroceriesBecauseAggregationIsExact()
            throws MishMashException {
        GroceryItem tofu = new GroceryItem("Tofu", new BigDecimal("50"), "g");
        Recipe bowl = new Recipe("Bowl", 500, 20, 80, 10,
                List.of(rice, tofu), FoodInfo.parseField("none"));
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(bowl))));
        List<GroceryItem> groceries = List.of(rice, tofu);
        activePlan.setGenerated(plan, new GeneratePlanRequest(1, NutritionTargets.none()),
                Optional.of(groceries));
        CommandResult result = command(1, 1,
                new GroceryItem("tofu", new BigDecimal("50.0"), "G"),
                new GroceryItem("RICE", new BigDecimal("100.00"), "g"))
                .executeWithResult(recipeBook);

        assertEquals(CommandResult.Status.DONE, result.status());
        assertTrue(activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().get(0)
                .hasCustomizedIngredients());
        assertTrue(activePlan.getGroceryList().isEmpty());

        new GenerateListCommand(activePlan).execute(recipeBook);
        List<GroceryItem> regenerated = activePlan.getGroceryList().orElseThrow();
        assertEquals(List.of("tofu", "RICE"), regenerated.stream().map(GroceryItem::getName).toList());
        assertEquals(List.of("G", "g"), regenerated.stream().map(GroceryItem::getUnit).toList());
    }

    @Test
    void execute_categoryRestrictions_warnsThatCustomizedNamesAreNotClassified() throws MishMashException {
        createGeneratedPlan();
        dietarySettings.set(new DietaryPreferences(Diet.VEGETARIAN, Set.of(FoodTag.SOY), Set.of()));
        GroceryItem chicken = new GroceryItem("Chicken", BigDecimal.ONE, "g");

        CommandResult result = command(1, 1, chicken).executeWithResult(recipeBook);

        assertEquals(CommandResult.Status.WARNING, result.status());
        assertTrue(result.feedback().contains("not classified for named-diet or avoided-category rules"));
        assertEquals(List.of(chicken), activePlan.get().orElseThrow().getDays().get(0)
                .getPlannedMeals().get(0).getIngredients());
    }

    @Test
    void execute_sourceIngredientsAfterCustomization_removesOverrideAndClearsGroceries()
            throws MishMashException {
        createGeneratedPlan();
        GroceryItem tofu = new GroceryItem("Tofu", new BigDecimal("150"), "g");
        command(1, 1, tofu).execute(recipeBook);
        activePlan.setGroceryList(List.of(tofu));

        dietarySettings.set(new DietaryPreferences(Diet.VEGETARIAN, Set.of(), Set.of()));

        CommandResult result = command(1, 1, rice).executeWithResult(recipeBook);

        assertEquals(CommandResult.Status.DONE, result.status());
        assertTrue(result.feedback().startsWith(
                "Meal 1 on Day 1 ingredients restored from its source recipe."));
        assertFalse(result.feedback().contains("not classified"));
        assertTrue(activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().stream()
                .noneMatch(meal -> meal.hasCustomizedIngredients()));
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_excludedIngredient_rejectsAndPreservesAllState() {
        MealPlan plan = createGeneratedPlan();
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        dietarySettings.set(new DietaryPreferences(Diet.NONE, Set.of(), Set.of("Peanut")));
        GroceryItem peanut = new GroceryItem(" peanut ", BigDecimal.ONE, "g");

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, peanut).execute(recipeBook));

        assertEquals("Those ingredients conflict with your current dietary preferences.", error.getExplanation());
        assertEquals(CustomizeMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void constructor_nullDependencies_rejectsImmediately() {
        IngredientCustomization customization = new IngredientCustomization(1, 1, List.of(rice));
        assertThrows(NullPointerException.class,
                () -> new CustomizeMealCommand(null, activePlan, dietarySettings));
        assertThrows(NullPointerException.class,
                () -> new CustomizeMealCommand(customization, null, dietarySettings));
        assertThrows(NullPointerException.class,
                () -> new CustomizeMealCommand(customization, activePlan, null));
    }

    /**
     * Creates one generated planned meal with retained request and grocery state.
     */
    private MealPlan createGeneratedPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice)));
        return plan;
    }

    /**
     * Creates a customization command connected to this test's shared state.
     */
    private CustomizeMealCommand command(int dayNumber, int mealNumber, GroceryItem... ingredients) {
        IngredientCustomization customization =
                new IngredientCustomization(dayNumber, mealNumber, List.of(ingredients));
        return new CustomizeMealCommand(customization, activePlan, dietarySettings);
    }
}
