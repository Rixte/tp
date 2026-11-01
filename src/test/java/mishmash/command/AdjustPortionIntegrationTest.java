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
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;

/**
 * Checks the parsed portion workflow through plan viewing, shortfalls, and grocery generation.
 */
class AdjustPortionIntegrationTest {
    private final Recipe rice = new Recipe("Rice", 200, 4, 45, 1,
            List.of(new GroceryItem("Rice", new BigDecimal("100"), "g")), FoodInfo.parseField("none"));
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(1)), activePlan);
    private final RecipeBook recipeBook = new RecipeBook();

    @Test
    void parsedCommand_adjustThenViewCheckAndGenerate_usesScaledValues() throws MishMashException {
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(rice)))));

        Command adjustment = parser.parseCommand("adjust-portion portions/3 m/1 d/1");
        String confirmation = adjustment.execute(recipeBook);
        String plan = parser.parseCommand("view-plan").execute(recipeBook);
        String shortfall = parser.parseCommand("check-shortfall cal/700").execute(recipeBook);
        parser.parseCommand("generate-list").execute(recipeBook);

        assertInstanceOf(AdjustPortionCommand.class, adjustment);
        assertTrue(confirmation.startsWith("Meal 1 on Day 1 updated to 3 portions."));
        assertTrue(plan.contains("Meal 1: Rice [3 portions] (600 kcal | P: 12g, C: 135g, F: 3g)"));
        assertTrue(plan.contains("Daily Totals: 600 kcal"));
        assertTrue(shortfall.contains("Calories: 600/700 kcal (short by 100 kcal)."));
        assertEquals(new BigDecimal("300"), activePlan.getGroceryList().orElseThrow().get(0).getQuantity());
    }

    @Test
    void parseInvalidAdjustment_reportsSpecificUsageWithoutChangingPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice))));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("adjust-portion d/1 m/1 portions/0"));

        assertTrue(error.getMessage().contains(Parser.ADJUST_PORTION_USAGE));
        assertEquals(plan, activePlan.get().orElseThrow());
    }

    @Test
    void adjustOneOfRepeatedMeals_thenSetDietAndGenerateGroceries_preservesOccurrenceCounts()
            throws MishMashException {
        MealPlan plan = new MealPlan(List.of(
                new DayPlan(List.of(rice)),
                new DayPlan(List.of(rice))));
        GeneratePlanRequest request = new GeneratePlanRequest(2, NutritionTargets.none());
        activePlan.setGenerated(plan, request, Optional.of(List.of(rice.getIngredients().get(0))));

        execute("adjust-portion d/1 m/1 portions/2");
        execute("set-diet diet/vegetarian");
        execute("generate-list");

        MealPlan updated = activePlan.get().orElseThrow();
        assertEquals(2, updated.getDays().get(0).getPlannedMeals().get(0).getPortionCount());
        assertEquals(1, updated.getDays().get(1).getPlannedMeals().get(0).getPortionCount());
        assertSame(request, activePlan.getRequest().orElseThrow());
        assertEquals(new BigDecimal("300"),
                activePlan.getGroceryList().orElseThrow().get(0).getQuantity());
    }

    /** Executes one command through the shared parser and recipe book. */
    private String execute(String input) throws MishMashException {
        return parser.parseCommand(input).execute(recipeBook);
    }
}
