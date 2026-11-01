package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.command.Command;
import mishmash.command.HelpCommand;
import mishmash.command.ResetPlanCommand;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author Rixte
/**
 * Checks reset syntax, deferred execution, error recovery, and discovery through help and general usage.
 */
class ParserResetPlanTest {
    @TestFactory
    Stream<DynamicTest> parseCommand_whitespace_acceptsResetWithoutChangingStateUntilExecution() {
        return Stream.of("reset-plan", " \treset-plan \t ", "reset-plan\n\t")
                .map(input -> DynamicTest.dynamicTest(input, () -> {
                    ActiveMealPlan active = new ActiveMealPlan();
                    Recipe meal = TestRecipes.recipe("Meal", 100, 10, 20, 5);
                    MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(meal))));
                    active.set(plan);
                    Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

                    Command reset = parser.parseCommand(input);

                    assertInstanceOf(ResetPlanCommand.class, reset);
                    assertSame(plan, active.get().orElseThrow());
                    reset.execute(new RecipeBook());
                    assertTrue(active.get().isEmpty());
                }));
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_arguments_rejectsWithoutChangingStateAndAllowsRetry() {
        return Stream.of("extra", "d/1", "cal/2000", "yes", "--force", "reset-plan", "1", "d/", "\nextra",
                "p/100", "c/200", "f/50", "n/Meal", "i/Rice:1g", "food/none", "diet/vegetarian",
                "avoid/milk", "exclude/Rice", "--help", "?", "\tno", "\r\nyes", "\"\"")
                .map(arguments -> DynamicTest.dynamicTest(arguments, () -> verifyRejectedReset(arguments)));
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_lookalikeCommands_remainsCaseSensitive() {
        return Stream.of("Reset-plan", "RESET-PLAN", "reset", "reset-plans", "reset-planx", "reset_plan",
                "reset-plan/", "reset–plan", "ｒｅｓｅｔ-plan")
                .map(input -> DynamicTest.dynamicTest(input, () -> {
                    MishMashException error = assertThrows(MishMashException.class,
                            () -> new Parser().parseCommand(input));
                    assertEquals("Unknown command: " + input, error.getMessage());
                }));
    }

    @Test
    void helpAndUsage_resetPlan_listsSyntaxOnceAndExplainsPreservedData() {
        String help = new HelpCommand().execute(new RecipeBook());

        assertEquals(1, help.lines().filter(ResetPlanCommand.USAGE::equals).count());
        assertEquals(1, Parser.COMMAND_USAGE.lines().filter(ResetPlanCommand.USAGE::equals).count());
        assertTrue(help.contains("Recipes, dietary preferences, saved daily targets, and recorded meals are kept."));
    }

    /**
     * Checks each rejected argument against existing snapshots before executing a valid retry.
     */
    private void verifyRejectedReset(String arguments) throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        Recipe recipe = TestRecipes.recipe("Meal", 100, 10, 20, 5);
        RecipeBook book = new RecipeBook();
        book.add(recipe);
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, new NutritionTargets(100, 10, 20, 5));
        active.setGenerated(plan, request, Optional.empty());
        active.setGroceryList(List.of(new GroceryItem("Rice", BigDecimal.ONE, "g")));
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("reset-plan " + arguments));

        assertEquals("reset-plan does not accept arguments." + System.lineSeparator() + ResetPlanCommand.USAGE,
                error.getMessage());
        assertEquals("reset-plan does not accept arguments.", error.getExplanation());
        assertEquals(Optional.of(ResetPlanCommand.USAGE), error.getUsage());
        assertSame(plan, active.get().orElseThrow());
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        assertEquals(List.of(recipe), book.getRecipes());
        parser.parseCommand("reset-plan").execute(book);
        assertTrue(active.get().isEmpty());
        assertTrue(active.getRequest().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
    }
}
//@@author
