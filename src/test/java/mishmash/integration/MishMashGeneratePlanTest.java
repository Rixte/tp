package mishmash.integration;

import static mishmash.TestLauncher.runMain;
import static mishmash.testutil.CliAssertions.assertInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import mishmash.parser.GeneratePlanParser;
import mishmash.parser.Parser;

/**
 * Checks that generate-plan is reachable from the CLI entry point and reports errors with its own usage.
 */
class MishMashGeneratePlanTest {
    @Test
    void main_targetedPlan_displaysWarningsOnlyForViewedDays() {
        String output = runCli(String.join(System.lineSeparator(), "Tester",
                "add-recipe n/A cal/500 p/20 c/50 f/10 i/Rice:100g",
                "add-recipe n/B cal/600 p/30 c/60 f/15 i/Rice:100g",
                "add-recipe n/C cal/700 p/40 c/70 f/20 i/Rice:100g",
                "generate-plan d/2 cal/1700 p/85 c/180 f/45",
                "view-plan d/2", "generate-plan d/1", "yes", "view-plan", "bye") + System.lineSeparator());

        assertEquals(1, countOccurrences(output,
                "Warning: Calories exceed target by 100 kcal (1800 kcal actual; 1700 kcal target)."), output);
        assertEquals(1, countOccurrences(output,
                "Warning: Protein exceeds target by 5 g (90 g actual; 85 g target)."), output);
        assertEquals(2, countOccurrences(output, "Warning:"), output);
        assertEquals(1, countOccurrences(output, "================ Day 2 ================"), output);
        assertEquals(1, countOccurrences(output, "================ Day 1 ================"), output);
        assertFalse(output.contains("Error:"), output);
        assertInOrder(output, "Meal plan successfully generated for 2 day(s)!", "================ Day 2",
                "Warning: Calories exceed target by 100 kcal", "Warning: Protein exceeds target by 5 g",
                "Meal plan successfully generated for 1 day(s)!", "================ Day 1", "Goodbye!");
    }

    @Test
    void main_groceryGeneration_connectsToViewingAndRecoversFromInvalidArguments() {
        String output = runCli(String.join(System.lineSeparator(), "Tester",
                "generate-list",
                "add-recipe n/A cal/500 p/20 c/50 f/10 i/Rice:0.1g",
                "add-recipe n/B cal/500 p/20 c/50 f/10 i/Rice:0.2g",
                "add-recipe n/C cal/500 p/20 c/50 f/10 i/Rice:1kg",
                "generate-plan d/2", "generate-list", "generate-list extra", "view-list",
                "generate-plan d/1", "yes", "view-list", "generate-list", "view-list", "bye"));

        assertTrue(output.contains("No active meal plan. Use 'generate-plan' first."), output);
        assertTrue(output.contains("Grocery list generated. Use 'view-list' to view it."), output);
        assertTrue(output.contains("Error: generate-list does not accept arguments."), output);
        assertEquals(1, countOccurrences(output, Parser.GENERATE_LIST_USAGE), output);
        assertFalse(output.contains(Parser.ADD_RECIPE_USAGE), output);
        assertTrue(output.contains("[ ] Rice: 0.6g"), output);
        assertTrue(output.contains("[ ] Rice: 2kg"), output);
        assertTrue(output.contains("No grocery list generated. Use 'generate-list' first."), output);
        assertTrue(output.contains("[ ] Rice: 0.3g"), output);
        assertTrue(output.contains("[ ] Rice: 1kg"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
        assertInOrder(output, "No active meal plan.", "Meal plan successfully generated for 2 day(s)!",
                "Grocery list generated.", "Error: generate-list does not accept arguments.",
                "================ Grocery List", "Total items to purchase: 2",
                "Meal plan successfully generated for 1 day(s)!", "No grocery list generated.",
                "Grocery list generated.", "================ Grocery List", "Total items to purchase: 2", "Goodbye!");
    }

    @Test
    void main_invalidGeneratePlan_showsGeneratePlanUsageOnly() {
        String output = runCli("Tester" + System.lineSeparator()
                + "generate-plan" + System.lineSeparator()
                + "bye" + System.lineSeparator());

        assertTrue(output.contains("Error: "), output);
        assertTrue(output.contains(GeneratePlanParser.USAGE), output);
        assertFalse(output.contains(Parser.ADD_RECIPE_USAGE), output);
        assertEquals(2, countOccurrences(output,
                "mishmash >"), output);
        assertEquals(1, output.split("Error: ", -1).length - 1, output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void main_generateThenViewPlan_showsGeneratedPlanAndRecovers() {
        String output = runCli(String.join(System.lineSeparator(), "Tester",
                "view-plan",
                "generate-plan d/2",
                "add-recipe n/Oats cal/450 p/35 c/60 f/8 i/Oats:80g",
                "add-recipe n/Chicken cal/650 p/50 c/70 f/15 i/Chicken:200g",
                "add-recipe n/Salad cal/550 p/42 c/20 f/32 i/Lettuce:100g",
                "generate-plan d/2 cal/1650 p/127 c/150 f/55",
                "view-plan d/2",
                "generate-plan d/3 cal/100", "yes",
                "view-plan d/3",
                "bye") + System.lineSeparator());

        assertTrue(output.contains("No active meal plan."), output);
        assertTrue(output.contains("Error: Your recipe book is empty."), output);
        assertTrue(output.contains("Meal plan successfully generated for 2 day(s)!"), output);
        assertTrue(output.contains("Use 'view-plan' to inspect your planned meals."), output);
        // Only one combination fits the targets, and only view-plan d/2 prints a day.
        assertEquals(1, countOccurrences(output,
                "Daily Totals: 1650 kcal | Protein: 127g | Carbs: 150g | Fats: 55g"), output);
        assertTrue(output.contains("Error: Could not find 3 recipes that meet your daily targets"), output);
        // The failed generation must keep the earlier 2-day plan, so day 3 is still out of range.
        assertTrue(output.contains("Error: Day number must be between 1 and 2."), output);
        assertInOrder(output, "No active meal plan.", "Error: Your recipe book is empty.",
                "New recipe added!", "New recipe added!", "New recipe added!",
                "Meal plan successfully generated for 2 day(s)!", "================ Day 2",
                "Daily Totals: 1650 kcal", "Error: Could not find 3 recipes that meet your daily targets",
                "Error: Day number must be between 1 and 2.", "Goodbye!");
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    private static int countOccurrences(String output, String message) {
        return output.split(Pattern.quote(message), -1).length - 1;
    }

    /**
     * Runs a CLI session and restores the global streams even if execution fails.
     *
     * @param input The simulated console input.
     * @return The captured output.
     */
    private String runCli(String input) {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capturedOutput = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(capturedOutput);
            runMain("mishmash.%g.log");
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
