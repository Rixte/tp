package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;

/**
 * Checks parsed customization together with portions, viewing, and grocery generation.
 */
class CustomizeMealIntegrationTest {
    private final GroceryItem rice = new GroceryItem("Rice", new BigDecimal("100"), "g");
    private final Recipe recipe = new Recipe("Rice bowl", 500, 20, 80, 10, List.of(rice));
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(1)), activePlan);
    private final RecipeBook recipeBook = new RecipeBook();

    @Test
    void parsedCommands_adjustCustomizeViewAndGenerate_composeWithoutEditingRecipe() throws MishMashException {
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(recipe)))));
        parser.parseCommand("adjust-portion d/1 m/1 portions/2").execute(recipeBook);

        Command customization = parser.parseCommand(
                "customize-meal m/1 i/Brown Rice:60.5g i/Tofu:150g d/1");
        customization.execute(recipeBook);
        String display = parser.parseCommand("view-plan").execute(recipeBook);
        parser.parseCommand("generate-list").execute(recipeBook);

        assertInstanceOf(CustomizeMealCommand.class, customization);
        assertTrue(display.contains("Rice bowl [2 portions] [custom ingredients] (1000 kcal"));
        assertEquals(List.of("Brown Rice", "Tofu"), activePlan.getGroceryList().orElseThrow().stream()
                .map(GroceryItem::getName).toList());
        assertEquals(new BigDecimal("121.0"),
                activePlan.getGroceryList().orElseThrow().get(0).getQuantity());
        assertEquals(new BigDecimal("300"),
                activePlan.getGroceryList().orElseThrow().get(1).getQuantity());
        assertEquals(List.of(rice), recipe.getIngredients());
    }

    @Test
    void parsedInvalidCustomization_reportsSpecificUsageWithoutChangingPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("customize-meal d/1 m/1 i/Rice:0g"));

        assertTrue(error.getMessage().contains(Parser.CUSTOMIZE_MEAL_USAGE));
        assertEquals(plan, activePlan.get().orElseThrow());
    }
}
