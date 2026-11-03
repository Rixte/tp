package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.FoodInfo;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealLog;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;

/**
 * Checks clear-diet through shared parser state while preserving every unrelated session domain.
 */
class ClearDietIntegrationTest {
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final MealLog mealLog = new MealLog();
    private final Parser parser = new Parser(mealLog, new MealPlanGenerator(new Random(1)), activePlan);
    private final RecipeBook recipeBook = new RecipeBook();

    @Test
    void parsedCommand_executionClearsOnlyDietarySettings() throws MishMashException {
        Recipe recipe = recipe("Rice", "none");
        recipeBook.add(recipe);
        TestRecipes.recordToday(mealLog, recipe);
        List<GroceryItem> customizedIngredients = List.of(
                new GroceryItem("Lentils", BigDecimal.TEN, "g"));
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))))
                .withAdjustedPortion(0, 0, 2)
                .withCustomizedIngredients(0, 0, customizedIngredients);
        GeneratePlanRequest request = new GeneratePlanRequest(1, new NutritionTargets(500, null, null, null));
        List<GroceryItem> groceries = List.of(recipe.getIngredients().get(0));
        activePlan.setGenerated(plan, request, Optional.of(groceries));
        List<GroceryItem> storedGroceries = activePlan.getGroceryList().orElseThrow();
        execute("set-diet diet/vegetarian avoid/milk exclude/Peanut");
        execute("set-targets cal/2000 p/100");
        String targets = execute("view-targets");
        Command pending = parser.parseCommand("clear-diet");

        assertInstanceOf(ClearDietCommand.class, pending);
        assertTrue(execute("view-diet").contains("Diet: vegetarian"));
        String result = pending.execute(recipeBook);

        assertTrue(result.startsWith("Dietary preferences and restrictions cleared."));
        assertEquals(String.join(System.lineSeparator(), "Diet: none",
                "Avoided food categories: none", "Excluded ingredients: none"), execute("view-diet"));
        assertSame(plan, activePlan.get().orElseThrow());
        assertEquals(2, activePlan.get().orElseThrow().getDays().get(0)
                .getPlannedMeals().get(0).getPortionCount());
        assertEquals(customizedIngredients, activePlan.get().orElseThrow().getDays().get(0)
                .getPlannedMeals().get(0).getIngredients());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(storedGroceries, activePlan.getGroceryList().orElseThrow());
        assertEquals(targets, execute("view-targets"));
        assertEquals(List.of(recipe), recipeBook.getRecipes());
        assertEquals(List.of(recipe), TestRecipes.recordedRecipes(mealLog));
    }

    @Test
    void invalidArguments_preserveDietAndAllPlanningState() throws MishMashException {
        Recipe recipe = recipe("Rice", "none");
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(recipe.getIngredients().get(0))));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        execute("set-diet diet/vegetarian");
        String diet = execute("view-diet");

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("clear-diet diet/none"));

        assertTrue(error.getMessage().contains("clear-diet does not accept arguments."));
        assertTrue(error.getMessage().contains(Parser.CLEAR_DIET_USAGE));
        assertEquals(diet, execute("view-diet"));
        assertSame(plan, activePlan.get().orElseThrow());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertSame(groceries, activePlan.getGroceryList().orElseThrow());
    }

    @Test
    void clearDiet_beforeGeneration_allowsPreviouslyRestrictedRecipes() throws MishMashException {
        recipeBook.add(recipe("Milk", "milk"));
        recipeBook.add(recipe("Egg", "egg"));
        recipeBook.add(recipe("Unknown", null));
        execute("set-diet diet/vegan");

        assertThrows(MishMashException.class, () -> execute("generate-plan d/1"));
        assertTrue(activePlan.get().isEmpty());

        execute("clear-diet");

        execute("generate-plan d/1");

        assertEquals(3, activePlan.get().orElseThrow().getDays().get(0).getMeals().size());
    }

    @Test
    void help_listsClearDietPurposeAndSyntax() throws MishMashException {
        String help = execute("help");

        assertTrue(help.contains("clear-diet - Remove all dietary preferences and restrictions."));
        assertTrue(help.contains(Parser.CLEAR_DIET_USAGE));
    }

    /**
     * Executes one command through the shared parser and recipe book.
     */
    private String execute(String input) throws MishMashException {
        return parser.parseCommand(input).execute(recipeBook);
    }

    /**
     * Creates a consistently sized recipe with explicit or unknown classification.
     */
    private Recipe recipe(String name, String foodTags) {
        return new Recipe(name, 500, 20, 50, 10,
                List.of(new GroceryItem(name, BigDecimal.ONE, "g")), FoodInfo.parseField(foodTags));
    }
}
