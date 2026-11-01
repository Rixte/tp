package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import mishmash.model.FoodInfo;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;

//@@author neil0618
/**
 * Checks the parsed replace-meal workflow through plan viewing, groceries, and dietary settings.
 */
class ReplaceMealIntegrationTest {
    private final Recipe rice = new Recipe("Rice", 200, 4, 45, 1,
            List.of(new GroceryItem("Rice", new BigDecimal("100"), "g")), FoodInfo.parseField("none"));
    private final Recipe beans = new Recipe("Beans", 300, 20, 40, 5,
            List.of(new GroceryItem("Beans", new BigDecimal("50"), "g")), FoodInfo.parseField("legume"));
    private final Recipe tofu = new Recipe("Tofu", 150, 15, 3, 8,
            List.of(new GroceryItem("Tofu", new BigDecimal("80"), "g")), FoodInfo.parseField("soy"));
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(1)), activePlan);
    private final RecipeBook recipeBook = new RecipeBook();

    @Test
    void parsedCommand_replaceThenViewAndGenerate_usesTheNewRecipe() throws MishMashException {
        recipeBook.add(rice);
        recipeBook.add(beans);
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(rice)))));

        Command replacement = parser.parseCommand("replace-meal r/2 m/1 d/1");
        String confirmation = replacement.execute(recipeBook);
        String plan = parser.parseCommand("view-plan").execute(recipeBook);
        parser.parseCommand("generate-list").execute(recipeBook);

        assertInstanceOf(ReplaceMealCommand.class, replacement);
        assertTrue(confirmation.startsWith("Meal 1 on Day 1 replaced with Beans"));
        assertTrue(plan.contains("Beans"));
        assertTrue(plan.contains("Daily Totals: 300 kcal"));
        List<GroceryItem> groceries = activePlan.getGroceryList().orElseThrow();
        assertEquals(1, groceries.size());
        assertEquals("Beans", groceries.get(0).getName());
        assertEquals(new BigDecimal("50"), groceries.get(0).getQuantity());
    }

    @Test
    void parseInvalidReplacement_reportsSpecificUsageWithoutChangingPlan() {
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(rice))));
        activePlan.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("replace-meal d/1 m/1 r/0"));

        assertTrue(error.getMessage().contains(Parser.REPLACE_MEAL_USAGE));
        assertSame(plan, activePlan.get().orElseThrow());
    }

    @Test
    void replaceAfterSetDiet_conflictingRecipeIsRejectedAndAllowedRecipeIsAccepted() throws MishMashException {
        recipeBook.add(tofu);
        recipeBook.add(beans);
        activePlan.set(new MealPlan(List.of(new DayPlan(List.of(rice)))));
        parser.parseCommand("set-diet avoid/soy").execute(recipeBook);
        MealPlan plan = activePlan.get().orElseThrow();

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("replace-meal d/1 m/1 r/1").execute(recipeBook));
        parser.parseCommand("replace-meal d/1 m/1 r/2").execute(recipeBook);

        assertEquals("Recipe 1 (Tofu) conflicts with your current dietary preferences.", error.getExplanation());
        assertSame(rice, plan.getDays().get(0).getPlannedMeals().get(0).getRecipe());
        assertSame(beans, activePlan.get().orElseThrow().getDays().get(0).getPlannedMeals().get(0).getRecipe());
    }

    @Test
    void replaceMeal_isListedInHelpAndGeneralUsage() throws MishMashException {
        String help = parser.parseCommand("help").execute(recipeBook);

        assertTrue(help.contains(Parser.REPLACE_MEAL_USAGE));
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.REPLACE_MEAL_USAGE));
    }
}
//@@author
