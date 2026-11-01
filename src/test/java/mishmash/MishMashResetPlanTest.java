package mishmash;

import static mishmash.testutil.CliAssertions.assertInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.command.ResetPlanCommand;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealLog;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.LogCapture;

//@@author Rixte
/**
 * Checks the reset command's complete console workflow, preservation of session data, and error recovery.
 */
class MishMashResetPlanTest {
    @Test
    void run_resetGeneratedPlan_preservesOtherSessionDataAndAllowsRegeneration() {
        String output = runCli(
                "add-recipe n/A cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/B cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/C cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "set-diet diet/vegetarian", "record-meal 1", "generate-plan d/2 cal/1500", "generate-list",
                "view-list", "reset-plan", "yes", "view-plan", "view-list", "view-meals", "view-diet", "list-recipes",
                "generate-plan d/1", "generate-list", "view-list", "bye");
        int resetPosition = output.indexOf("Meal plan reset.");
        int regenerationPosition = output.indexOf("Meal plan successfully generated for 1 day(s)!", resetPosition);

        assertTrue(resetPosition >= 0 && regenerationPosition > resetPosition, output);
        String afterReset = output.substring(resetPosition, regenerationPosition);
        assertTrue(afterReset.contains("No active meal plan. Use 'generate-plan' first."), afterReset);
        assertTrue(afterReset.contains("No grocery list generated. Use 'generate-list' first."), afterReset);
        assertTrue(afterReset.contains("Meal 1: A"), afterReset);
        assertTrue(afterReset.contains("1. A (500 kcal)"), afterReset);
        assertTrue(afterReset.contains("vegetarian"), afterReset);
        assertFalse(afterReset.contains("[ ] Rice:"), afterReset);
        assertTrue(output.substring(regenerationPosition).contains("[ ] Rice: 300g"), output);
        assertEquals(0, countOccurrences(output, "Error: "));
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
        assertInOrder(output, "Meal plan successfully generated for 2 day(s)!", "[ ] Rice: 600g",
                "Meal plan reset.", "No active meal plan. Use 'generate-plan' first.",
                "No grocery list generated. Use 'generate-list' first.", "Meal 1: A", "vegetarian",
                "1. A (500 kcal)", "Meal plan successfully generated for 1 day(s)!", "[ ] Rice: 300g", "Goodbye!");
    }

    @Test
    void run_invalidReset_recoversWithoutClearingDataOrConsumingNextCommand() {
        String output = runCli("reset-plan extra", "reset-plan", "reset-plan", "help", "bye");

        assertEquals(1, countOccurrences(output, "Error: reset-plan does not accept arguments."));
        assertEquals(2, countOccurrences(output, ResetPlanCommand.USAGE));
        assertEquals(2, countOccurrences(output, "No active meal plan to reset."));
        assertEquals(1, countOccurrences(output, "Available commands (names are case-sensitive):"));
        assertFalse(output.contains("Are you sure"), output);
        assertTrue(output.indexOf("Error: ") < output.indexOf("No active meal plan to reset."), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void run_invalidResetWithActivePlan_keepsMealsAndGroceriesUntilValidReset() {
        String output = runCli(
                "add-recipe n/A cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/B cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/C cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "generate-plan d/1", "generate-list", "reset-plan d/1", "view-plan", "view-list",
                "reset-plan", "yes", "view-plan", "view-list", "bye");
        int failure = output.indexOf("Error: reset-plan does not accept arguments.");
        int reset = output.indexOf("Meal plan reset.", failure);

        assertTrue(failure >= 0 && reset > failure, output);
        String preserved = output.substring(failure, reset);
        assertTrue(preserved.contains("Daily Totals: 1500 kcal"), preserved);
        assertTrue(preserved.contains("[ ] Rice: 300g"), preserved);
        String cleared = output.substring(reset);
        assertTrue(cleared.contains("No active meal plan."), cleared);
        assertTrue(cleared.contains("No grocery list generated."), cleared);
        assertFalse(cleared.contains("[ ] Rice:"), cleared);
        assertEquals(1, countOccurrences(output, "Error: "));
        assertInOrder(output, "Error: reset-plan does not accept arguments.", ResetPlanCommand.USAGE,
                "Daily Totals: 1500 kcal", "[ ] Rice: 300g", "Meal plan reset.",
                "No active meal plan.", "No grocery list generated.", "Goodbye!");
    }

    @TestFactory
    Stream<DynamicTest> run_endOfInputAfterResetCommand_finishesWithoutRequestingAnotherAnswer() {
        return Stream.of("reset-plan", "reset-plan extra").map(input -> DynamicTest.dynamicTest(input, () -> {
            String output = runCli(input);

            assertTrue(output.contains(input.endsWith("extra") ? "Error: reset-plan does not accept arguments."
                    : "No active meal plan to reset."), output);
            assertFalse(output.contains("Are you sure"), output);
            assertFalse(output.contains("Goodbye!"), output);
            assertEquals(2, countOccurrences(output,
                    "mishmash >"));
        }));
    }

    @TestFactory
    Stream<DynamicTest> run_resetFailure_preservesSessionDataAndAllowsRetry() {
        return Stream.of("incomplete clear", "null-message failure", "detailed failure")
                .map(scenario -> DynamicTest.dynamicTest(scenario, () -> verifyRecoverableReset(scenario)));
    }

    @Test
    void run_resetWithSavedTargets_preservesPersonalSettingsAndUsesThemForNextPlan() {
        String output = runCli(
                "add-recipe n/A cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/B cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/C cal/500 p/20 c/50 f/10 i/Rice:100g food/none",
                "set-targets cal/1500 p/60", "generate-plan d/1 cal/1450", "generate-list",
                "reset-plan extra", "view-targets", "reset-plan", "yes", "view-plan", "view-list", "view-targets",
                "generate-plan d/2", "generate-list", "view-list", "bye");

        assertInOrder(output, "Daily targets used: 1450 kcal, 60 g protein.",
                "Error: reset-plan does not accept arguments.", ResetPlanCommand.USAGE,
                "Calories: 1500 kcal", "Protein: 60 g", "Meal plan reset.",
                "No active meal plan.", "No grocery list generated.", "Calories: 1500 kcal", "Protein: 60 g",
                "Daily targets used: 1500 kcal, 60 g protein.", "[ ] Rice: 600g", "Goodbye!");
        assertEquals(1, countOccurrences(output, "Error: "));
        assertEquals(1, countOccurrences(output, ResetPlanCommand.USAGE));
    }

    /**
     * Checks state immediately after a failed reset, then retries with the same session dependencies.
     */
    private void verifyRecoverableReset(String scenario) throws MishMashException {
        ActiveMealPlan active = failsFirstReset(scenario);
        MealLog meals = new MealLog();
        RecipeBook book = new RecipeBook();
        Parser parser = new Parser(meals, new MealPlanGenerator(new Random(1)), active);
        for (String name : List.of("A", "B", "C")) {
            parser.parseCommand("add-recipe n/" + name + " cal/500 p/20 c/50 f/10 i/Rice:100g food/none").execute(book);
        }
        parser.parseCommand("record-meal 1").execute(book);
        parser.parseCommand("generate-plan d/1 cal/1500").execute(book);
        parser.parseCommand("generate-list").execute(book);
        parser.parseCommand("set-diet diet/vegetarian").execute(book);
        parser.parseCommand("set-targets cal/1600 p/60").execute(book);
        String savedTargets = parser.parseCommand("view-targets").execute(book);
        MealPlan plan = active.get().orElseThrow();
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        List<Recipe> recipes = book.getRecipes();
        List<Recipe> recordedMeals = meals.getMeals();

        try (LogCapture logs = new LogCapture(MishMash.class)) {
            String failed = runSession(parser, book, "reset-plan", "yes", "view-plan", "view-list", "view-meals",
                    "view-diet", "view-targets", "list-recipes", "bye");

            assertInOrder(failed, "Error: The meal plan could not be reset. Please try again.", ResetPlanCommand.USAGE,
                    "Daily Totals: 1500 kcal", "[ ] Rice: 300g", "Meal 1: A", "vegetarian",
                    "Calories: 1600 kcal", "Protein: 60 g", "1. A (500 kcal)", "Goodbye!");
            assertEquals(1, countOccurrences(failed, ResetPlanCommand.USAGE));
            assertFalse(failed.contains("Private model details"), failed);
            assertSame(plan, active.get().orElseThrow());
            assertSame(request, active.getRequest().orElseThrow());
            assertSame(groceries, active.getGroceryList().orElseThrow());
            assertEquals(recipes, book.getRecipes());
            assertEquals(recordedMeals, meals.getMeals());
            assertEquals(savedTargets, parser.parseCommand("view-targets").execute(book));

            String retried = runSession(parser, book, "reset-plan", "yes", "view-plan", "view-list", "bye");

            assertInOrder(retried, "Meal plan reset.", "No active meal plan.",
                    "No grocery list generated.", "Goodbye!");
            assertTrue(active.get().isEmpty());
            assertTrue(active.getRequest().isEmpty());
            assertTrue(active.getGroceryList().isEmpty());
            assertEquals(recipes, book.getRecipes());
            assertEquals(recordedMeals, meals.getMeals());
            assertEquals(savedTargets, parser.parseCommand("view-targets").execute(book));
            assertEquals(0, logs.getRecords().stream().filter(record -> record.getLevel() == Level.SEVERE).count());
        }
    }

    /**
     * Simulates either a failed clear or an incomplete clear once, followed by a normal retry.
     */
    private ActiveMealPlan failsFirstReset(String scenario) {
        return new ActiveMealPlan() {
            private boolean firstReset = true;

            @Override
            public void clear() {
                if (!firstReset) {
                    super.clear();
                    return;
                }
                firstReset = false;
                if (!scenario.equals("incomplete clear")) {
                    throw new IllegalStateException(scenario.equals("null-message failure")
                            ? null : "Private model details");
                }
            }
        };
    }

    /**
     * Runs a session with borrowed streams, leaving global console and logging settings untouched.
     */
    private String runCli(String... commands) {
        return runSession(new Parser(), new RecipeBook(), commands);
    }

    /**
     * Runs commands against supplied dependencies so tests can inspect retained session data directly.
     */
    private String runSession(Parser parser, RecipeBook book, String... commands) {
        String input = "Tester" + System.lineSeparator() + String.join(System.lineSeparator(), commands)
                + System.lineSeparator();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
                PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new MishMash(scanner, output, parser, book).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * Counts literal occurrences, including syntax text containing regular-expression characters.
     */
    private int countOccurrences(String output, String fragment) {
        return output.split(Pattern.quote(fragment), -1).length - 1;
    }
}
//@@author
