package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import mishmash.model.DietarySettings;
import mishmash.model.FoodInfo;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.MealReplacement;
import mishmash.model.NutritionTargets;
import mishmash.model.PlannedMeal;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.DietaryPreferencesParser;

//@@author neil0618
/**
 * Checks position validation, dietary safety, atomic plan replacement, and grocery invalidation.
 */
class ReplaceMealCommandTest {
    private final Recipe rice = new Recipe("Rice", 200, 4, 45, 1,
            List.of(new GroceryItem("Rice", new BigDecimal("100"), "g")), FoodInfo.parseField("none"));
    private final Recipe beans = new Recipe("Beans", 300, 20, 40, 5,
            List.of(new GroceryItem("Beans", new BigDecimal("50"), "g")), FoodInfo.parseField("legume"));
    private final Recipe tofu = new Recipe("Tofu", 150, 15, 3, 8,
            List.of(new GroceryItem("Tofu", new BigDecimal("80"), "g")), FoodInfo.parseField("soy"));
    private final Recipe unclassified = new Recipe("Mystery", 100, 1, 1, 1,
            List.of(new GroceryItem("Mystery", new BigDecimal("10"), "g")));
    private final RecipeBook recipeBook = new RecipeBook();
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final DietarySettings dietarySettings = new DietarySettings();

    @Test
    void execute_noPlan_returnsPromptWithoutChangingState() throws MishMashException {
        recipeBook.add(beans);

        CommandResult result = command(1, 1, 1).executeWithResult(recipeBook);

        assertEquals("No active meal plan. Use 'generate-plan' first.", result.feedback());
        assertEquals(CommandResult.Status.NOTE, result.status());
        assertTrue(activePlan.get().isEmpty());
    }

    @Test
    void execute_emptyPlan_returnsSamePromptWithoutChangingState() throws MishMashException {
        MealPlan emptyPlan = new MealPlan(List.of());
        activePlan.set(emptyPlan);

        assertEquals("No active meal plan. Use 'generate-plan' first.", command(1, 1, 1).execute(recipeBook));
        assertSame(emptyPlan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_emptyDay_reportsThatNoMealCanBeReplaced() {
        recipeBook.add(beans);
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of())));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, 1).execute(recipeBook));

        assertEquals("Day 1 has no meals to replace.", error.getExplanation());
        assertEquals(ReplaceMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_invalidDayMealOrRecipe_preservesPlanRequestAndGroceries() {
        recipeBook.add(beans);
        MealPlan plan = createGeneratedPlan();
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        MishMashException dayError = assertThrows(MishMashException.class,
                () -> command(2, 1, 1).execute(recipeBook));
        MishMashException mealError = assertThrows(MishMashException.class,
                () -> command(1, 3, 1).execute(recipeBook));
        MishMashException recipeError = assertThrows(MishMashException.class,
                () -> command(1, 1, 2).execute(recipeBook));

        assertEquals("Day number must be between 1 and 1.", dayError.getExplanation());
        assertEquals("Meal number for Day 1 must be between 1 and 2.", mealError.getExplanation());
        assertEquals("Recipe number must be between 1 and 1.", recipeError.getExplanation());
        for (MishMashException error : List.of(dayError, mealError, recipeError)) {
            assertEquals(ReplaceMealCommand.USAGE, error.getUsage().orElseThrow());
        }
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_emptyRecipeBook_reportsThatThereIsNothingToChooseFrom() {
        MealPlan plan = createGeneratedPlan();

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, 1).execute(recipeBook));

        assertEquals("There are no recipes to choose from. Use 'add-recipe' to add one.", error.getExplanation());
        assertSame(plan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_differentRecipe_replacesOnlySelectedMealAndClearsGroceries() throws MishMashException {
        recipeBook.add(rice);
        recipeBook.add(beans);
        MealPlan originalPlan = createGeneratedPlan();
        DayPlan originalDay = originalPlan.getDays().get(0);
        GeneratePlanRequest request = activePlan.getRequest().orElseThrow();

        CommandResult result = command(1, 1, 2).executeWithResult(recipeBook);

        DayPlan updatedDay = activePlan.get().orElseThrow().getDays().get(0);
        assertEquals("Meal 1 on Day 1 replaced with Beans (300 kcal | P: 20g, C: 40g, F: 5g)."
                + System.lineSeparator()
                + "The previous grocery list was cleared. Run 'generate-list' to update it.", result.feedback());
        assertEquals(CommandResult.Status.DONE, result.status());
        assertSame(beans, updatedDay.getPlannedMeals().get(0).getRecipe());
        assertEquals(1, updatedDay.getPlannedMeals().get(0).getPortionCount());
        assertSame(originalDay.getPlannedMeals().get(1), updatedDay.getPlannedMeals().get(1));
        assertEquals(600, updatedDay.getTotalCalories());
        assertSame(rice, originalDay.getPlannedMeals().get(0).getRecipe());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_noGroceryListYet_suggestsGeneratingOne() throws MishMashException {
        recipeBook.add(beans);
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(rice)))));

        CommandResult result = command(1, 1, 1).executeWithResult(recipeBook);

        assertEquals("Meal 1 on Day 1 replaced with Beans (300 kcal | P: 20g, C: 40g, F: 5g)."
                + System.lineSeparator() + "Run 'generate-list' to create groceries for the updated plan.",
                result.feedback());
    }

    @Test
    void execute_adjustedMeal_resetsToOnePortionWithoutCustomizations() throws MishMashException {
        recipeBook.add(beans);
        PlannedMeal heavyRice = new PlannedMeal(rice, 3);
        activePlan.set(new MealPlan(List.of(DayPlan.fromPlannedMeals(List.of(heavyRice)))));

        command(1, 1, 1).execute(recipeBook);

        PlannedMeal replaced = activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().get(0);
        assertSame(beans, replaced.getRecipe());
        assertEquals(1, replaced.getPortionCount());
        assertFalse(replaced.hasCustomizedIngredients());
    }

    @Test
    void execute_sameRecipeAlreadyAtOnePortion_isNoOpAndPreservesGrocerySnapshot() throws MishMashException {
        recipeBook.add(rice);
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice))));
        activePlan.setGenerated(plan, new GeneratePlanRequest(1, NutritionTargets.none()),
                Optional.of(List.of(rice.getIngredients().get(0))));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        CommandResult result = command(1, 1, 1).executeWithResult(recipeBook);

        assertEquals("Meal 1 on Day 1 is already Rice. Nothing was changed.", result.feedback());
        assertEquals(CommandResult.Status.NOTE, result.status());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_sameRecipeWithExtraPortions_isReplacedToResetPortions() throws MishMashException {
        recipeBook.add(rice);
        activePlan.set(new MealPlan(List.of(DayPlan.fromPlannedMeals(List.of(new PlannedMeal(rice, 3))))));

        CommandResult result = command(1, 1, 1).executeWithResult(recipeBook);

        assertEquals(CommandResult.Status.DONE, result.status());
        assertEquals(1, activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().get(0)
                .getPortionCount());
    }

    @Test
    void execute_recipeConflictsWithDiet_isRejectedWithoutChangingState() {
        recipeBook.add(tofu);
        MealPlan plan = createGeneratedPlan();
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        dietarySettings.set(new DietaryPreferencesParser().parse("avoid/soy"));

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 1, 1).execute(recipeBook));

        assertEquals("Recipe 1 (Tofu) conflicts with your current dietary preferences.", error.getExplanation());
        assertEquals(ReplaceMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_unclassifiedRecipeWhileRestricted_isRejectedButClassifiedRecipeIsAllowed()
            throws MishMashException {
        recipeBook.add(unclassified);
        recipeBook.add(beans);
        createGeneratedPlan();
        dietarySettings.set(new DietaryPreferencesParser().parse("avoid/soy"));

        assertThrows(MishMashException.class, () -> command(1, 1, 1).execute(recipeBook));
        CommandResult allowed = command(1, 1, 2).executeWithResult(recipeBook);

        assertEquals(CommandResult.Status.DONE, allowed.status());
        assertSame(beans, activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().get(0).getRecipe());
    }

    @Test
    void execute_nutritionOverflow_preservesPlanRequestAndGroceries() {
        Recipe huge = new Recipe("Huge", Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(rice.getIngredients().get(0)));
        recipeBook.add(huge);
        // Zero-nutrition filler: three huge meals leave only 1 spare unit below the long limit.
        Recipe water = new Recipe("Water", 0, 0, 0, 0, List.of(rice.getIngredients().get(0)));
        DayPlan day = DayPlan.fromPlannedMeals(List.of(
                new PlannedMeal(huge, Integer.MAX_VALUE),
                new PlannedMeal(huge, Integer.MAX_VALUE),
                new PlannedMeal(huge, 4),
                PlannedMeal.onePortionOf(water)));
        MealPlan plan = new MealPlan(List.of(day));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice.getIngredients().get(0))));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();

        MishMashException error = assertThrows(MishMashException.class,
                () -> command(1, 4, 1).execute(recipeBook));

        assertEquals("That recipe makes the day's nutrition totals too large.", error.getExplanation());
        assertEquals(ReplaceMealCommand.USAGE, error.getUsage().orElseThrow());
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void execute_nullRecipeBook_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> command(1, 1, 1).execute(null));
    }

    @Test
    void constructor_nullDependencies_rejectsImmediately() {
        MealReplacement replacement = new MealReplacement(1, 1, 1);
        assertThrows(NullPointerException.class, () -> new ReplaceMealCommand(null, activePlan, dietarySettings));
        assertThrows(NullPointerException.class,
                () -> new ReplaceMealCommand(replacement, null, dietarySettings));
        assertThrows(NullPointerException.class, () -> new ReplaceMealCommand(replacement, activePlan, null));
    }

    /**
     * Creates one generated day (Rice, Beans) with a grocery snapshot and returns its original plan.
     */
    private MealPlan createGeneratedPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice, beans))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice.getIngredients().get(0))));
        return plan;
    }

    /**
     * Creates a replacement command connected to this test's shared plan and dietary settings.
     */
    private ReplaceMealCommand command(int dayNumber, int mealNumber, int recipeNumber) {
        return new ReplaceMealCommand(new MealReplacement(dayNumber, mealNumber, recipeNumber),
                activePlan, dietarySettings);
    }
}
//@@author
