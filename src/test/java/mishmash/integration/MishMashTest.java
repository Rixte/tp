package mishmash.integration;

import static mishmash.TestLauncher.runMain;
import static mishmash.testutil.CliAssertions.assertInOrder;
import static mishmash.testutil.CliAssertions.stripAnsi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.MishMash;
import mishmash.exception.MishMashException;
import mishmash.parser.Parser;
import mishmash.testutil.AddRecipeTestData;
import mishmash.testutil.LogCapture;
import mishmash.ui.CliFormatter;

/**
 * Checks CLI output and recovery after an invalid command.
 */
class MishMashTest {
    private static final String ADD_RICE = AddRecipeTestData.VALID_COMMAND;
    private static final String ADD_OATS = "add-recipe n/Oats cal/150 p/5 c/27 f/3 i/Oats:40g";
    private static final String CONTINUE_PROMPT = "mishmash >";

    //@@author Rixte
    @Test
    void main_viewingErrors_logsRecoverableRejectionsAndContinues() {
        try (LogCapture logs = new LogCapture(MishMash.class)) {
            String output = runCli(createSessionInput(
                    "view-list extra", "view-plan d/0", "view-plan", "view-list", "bye"));
            List<LogRecord> rejections = logs.getRecords().stream()
                    .filter(record -> record.getMessage().equals("Command rejected")).toList();

            assertEquals(2, rejections.size());
            assertTrue(rejections.stream().allMatch(record -> record.getLevel() == Level.FINE
                    && record.getThrown() instanceof MishMashException));
            assertEquals(1, logs.getRecords().stream()
                    .filter(record -> record.getMessage().equals("Completed ViewPlanCommand")).count());
            assertEquals(1, logs.getRecords().stream()
                    .filter(record -> record.getMessage().equals("Completed ViewListCommand")).count());
            assertTrue(logs.getRecords().stream().noneMatch(record -> record.getLevel() == Level.SEVERE));
            assertEquals(2, countOccurrences(output, "Error: "), output);
            assertTrue(output.contains("Hello Tester"), output);
            assertFalse(output.contains("Command rejected"), output);
            assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
        }
    }

    @Test
    void main_newSession_greetsAgainWithoutPreviousPlanOrGroceries() {
        String firstSession = runCli(createSessionInput(
                "add-recipe n/A cal/100 p/10 c/20 f/5 i/Rice:1g",
                "add-recipe n/B cal/100 p/10 c/20 f/5 i/Rice:1g",
                "add-recipe n/C cal/100 p/10 c/20 f/5 i/Rice:1g",
                "generate-plan d/1", "generate-list", "view-plan", "view-list", "bye"));

        String nextSession = runCli(createSessionInput("view-plan", "view-list", "bye"));

        assertTrue(firstSession.contains("Daily Totals: 300 kcal | Protein: 30g | Carbs: 60g | Fats: 15g"));
        assertTrue(firstSession.contains("[ ] Rice: 3g"), firstSession);
        assertTrue(nextSession.contains("No active meal plan. Use 'generate-plan' first."), nextSession);
        assertTrue(nextSession.contains("No grocery list generated. Use 'generate-list' first."), nextSession);
        assertFalse(nextSession.contains("Daily Totals:"), nextSession);
        assertFalse(nextSession.contains("[ ] Rice:"), nextSession);
        for (String output : List.of(firstSession, nextSession)) {
            assertTrue(output.contains("Hello Tester"), output);
            assertEquals(0, countOccurrences(output, "Error: "), output);
            assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
        }
    }

    @Test
    void main_editRecipe_recoversFromErrorsAndShowsUpdatedDetails() {
        String output = runCli(createSessionInput("edit-recipe 1 p/10", ADD_RICE, "edit-recipe 1 n/Changed p/bad",
                "list-recipes", "edit-recipe 2 p/10", "edit-recipe 1 n/Brown Rice p/10 i/Brown Rice:175g",
                "list-recipes 1", "help", "bye"));

        assertTrue(output.contains("There are no recipes to edit."), output);
        assertTrue(output.contains("Recipe index must be between 1 and 1."), output);
        assertTrue(output.contains("1. Rice (200 kcal)"), output);
        assertTrue(output.contains("Recipe updated!"), output);
        assertTrue(output.contains("Brown Rice: 175g"), output);
        assertTrue(output.contains("Protein: 10g"), output);
        assertTrue(output.contains("edit-recipe - Update selected fields"), output);
        assertEquals(3, countOccurrences(output, "Error: "), output);
        assertEquals(9, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_editAfterDeletion_usesNewIndexWithoutAnotherConfirmation() {
        String output = runCli(createSessionInput(ADD_RICE, ADD_OATS, "delete-recipe 1", "y",
                "edit-recipe 1 n/Protein Oats p/12", "list-recipes 1", "bye"));

        String editedDetails = String.join(System.lineSeparator(), "Protein Oats",
                "150 kcal | Protein: 12g | Carbs: 27g | Fats: 3g", "Ingredients:", "- Oats: 40g");
        assertEquals(2, countOccurrences(output, editedDetails), output);
        assertEquals(1, countOccurrences(output, "Are you sure"), output);
        assertEquals(1, countOccurrences(output, "Recipe updated!"), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_lateEditErrors_preserveDetailsAndAllowSuccessfulRetry() {
        String output = runCli(createSessionInput(ADD_RICE, "edit-recipe 1 n/Changed i/Water:100ml food/milk,",
                "edit-recipe 1 n/Changed cal/250 i/Rice:0g", "list-recipes 1",
                "edit-recipe 1 p/10", "list-recipes 1", "bye"));

        String originalDetails = "Rice" + System.lineSeparator()
                + "200 kcal | Protein: 4g | Carbs: 45g | Fats: 1g";
        String editedDetails = "Rice" + System.lineSeparator()
                + "200 kcal | Protein: 10g | Carbs: 45g | Fats: 1g";
        assertEquals(2, countOccurrences(output, originalDetails), output);
        assertEquals(2, countOccurrences(output, editedDetails), output);
        assertEquals(2, countOccurrences(output, "Error: "), output);
        assertEquals(0, countOccurrences(output, "Changed"), output);
        assertEquals(0, countOccurrences(output, "Are you sure"), output);
        assertEquals(1, countOccurrences(output, "Recipe updated!"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_editBetweenGenerations_keepsOldWarningsAndGroceriesUntilNewPlan() {
        String output = runCli(createSessionInput(
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/B cal/600 p/30 c/50 f/10 i/Rice:100g food/none",
                "add-recipe n/C cal/600 p/30 c/50 f/10 i/Rice:100g food/none",
                "generate-plan d/1 cal/1700", "generate-list", "view-plan", "view-list",
                "edit-recipe 1 n/Updated A cal/800 i/Tofu:200g", "view-plan", "view-list",
                "generate-plan d/1 cal/2000", "yes", "view-plan", "view-list", "generate-list", "view-list", "bye"));

        assertEquals(2, countOccurrences(output,
                "Daily Totals: 1800 kcal | Protein: 90g | Carbs: 150g | Fats: 30g"), output);
        assertEquals(2, countOccurrences(output,
                "Warning: Calories exceed target by 100 kcal (1800 kcal actual; 1700 kcal target)."), output);
        assertEquals(2, countOccurrences(output, "[ ] Rice: 300g"), output);
        assertEquals(1, countOccurrences(output,
                "Daily Totals: 2000 kcal | Protein: 90g | Carbs: 150g | Fats: 30g"), output);
        assertEquals(1, countOccurrences(output, "[ ] Rice: 200g"), output);
        assertEquals(1, countOccurrences(output, "[ ] Tofu: 200g"), output);
        assertEquals(1, countOccurrences(output, "No grocery list generated. Use 'generate-list' first."), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
        assertInOrder(output, "Meal plan successfully generated for 1 day(s)!",
                "Daily Totals: 1800 kcal", "Warning: Calories exceed target by 100 kcal", "[ ] Rice: 300g",
                "Recipe updated!", "Daily Totals: 1800 kcal", "Warning: Calories exceed target by 100 kcal",
                "[ ] Rice: 300g", "Meal plan successfully generated for 1 day(s)!", "Daily Totals: 2000 kcal",
                "No grocery list generated.", "Grocery list generated.", "[ ] Tofu: 200g", "Goodbye!");
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    @Test
    void main_helpAndRecovery_promptAfterEveryCommand() {
        String output = runCli(createSessionInput("help", "list-recipes", "view-plan", "view-list",
                "generate-list", "generate-plan d/1", "unknown", "", "help extra", "bye"));

        for (String word : List.of("add-recipe", "list-recipes", "delete-recipe", "generate-plan",
                "view-plan", "generate-list", "view-list", "help", "bye")) {
            assertTrue(output.contains("Format: " + word), output);
        }
        assertTrue(output.contains("help does not accept arguments."), output);
        assertEquals(10, countOccurrences(output, CONTINUE_PROMPT), output);
        assertEquals(0, countOccurrences(output,
                "------------------------------------------------------------"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    //@@author Rixte
    @Test
    void main_checkShortfall_handlesMissingPlanInvalidTargetsAndSuccessfulCheck() {
        String output = runCli(createSessionInput("check-shortfall cal/2000", "check-shortfall cal/0",
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g",
                "add-recipe n/B cal/600 p/30 c/50 f/10 i/Rice:100g",
                "add-recipe n/C cal/600 p/30 c/50 f/10 i/Rice:100g",
                "generate-plan d/1", "check-shortfall cal/2000 p/100", "view-plan", "help", "bye"));

        assertTrue(output.contains("No active meal plan. Use 'generate-plan' first."), output);
        assertEquals(1, countOccurrences(output, "Error: "), output);
        assertTrue(output.contains("- Calories: 1800/2000 kcal (short by 200 kcal)."), output);
        assertTrue(output.contains("- Protein: 90/100 g (short by 10 g)."), output);
        assertTrue(output.contains("Daily Totals: 1800 kcal | Protein: 90g | Carbs: 150g | Fats: 30g"), output);
        assertTrue(output.contains("check-shortfall - Check each day's planned nutrition"), output);
        assertEquals(10, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_shortfallMissingValues_showsSpecificErrorsAndContinuesWithoutConfirmation() {
        String output = runCli(createSessionInput("check-shortfall cal/", "check-shortfall p/",
                "check-shortfall c/", "check-shortfall f/", "check-shortfall cal/2000", "help", "bye"));

        for (String prefix : List.of("cal", "p", "c", "f")) {
            assertTrue(output.contains("Error: Missing value after " + prefix + "/."
                    + System.lineSeparator() + "[TRY] " + Parser.CHECK_SHORTFALL_USAGE), output);
        }
        assertEquals(4, countOccurrences(output, "Error: "), output);
        // Four rejected commands show usage, and the later help command shows it once more.
        assertEquals(5, countOccurrences(output, Parser.CHECK_SHORTFALL_USAGE), output);
        assertEquals(1, countOccurrences(output, "No active meal plan. Use 'generate-plan' first."), output);
        assertEquals(0, countOccurrences(output, "Are you sure"), output);
        assertEquals(7, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_shortfallErrorsAndRepeatedChecks_keepPlanAndGroceriesUsable() {
        String output = runCli(createSessionInput(
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g",
                "add-recipe n/B cal/600 p/30 c/50 f/10 i/Rice:100g",
                "add-recipe n/C cal/600 p/30 c/50 f/10 i/Rice:100g",
                "generate-plan d/2 cal/2000", "generate-list", "check-shortfall cal/2000",
                "check-shortfall cal/", "check-shortfall p/", "check-shortfall cal/2000 p/100",
                "check-shortfall cal/1800", "view-list", "view-plan", "bye"));

        assertEquals(2, countOccurrences(output, "Error: Missing value after"), output);
        assertEquals(2, countOccurrences(output, "Error: "), output);
        assertEquals(4, countOccurrences(output, "- Calories: 1800/2000 kcal (short by 200 kcal)."), output);
        assertEquals(2, countOccurrences(output, "- Protein: 90/100 g (short by 10 g)."), output);
        assertEquals(1, countOccurrences(output, "Day 1: No shortfalls for the supplied targets."), output);
        assertEquals(1, countOccurrences(output, "Day 2: No shortfalls for the supplied targets."), output);
        assertTrue(output.contains("[ ] Rice: 600g"), output);
        assertEquals(2, countOccurrences(output,
                "Daily Totals: 1800 kcal | Protein: 90g | Carbs: 150g | Fats: 30g"), output);
        assertEquals(13, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_endOfInputAfterInvalidShortfall_exitsAfterDisplayingRecoverableError() {
        String output = runCli(createSessionInput("check-shortfall cal/"));

        assertEquals(1, countOccurrences(output, "Error: Missing value after cal/."), output);
        assertEquals(1, countOccurrences(output, Parser.CHECK_SHORTFALL_USAGE), output);
        assertEquals(2, countOccurrences(output, CONTINUE_PROMPT), output);
        assertEquals(0, countOccurrences(output, "No active meal plan."), output);
    }
    //@@author

    @Test
    void main_cancelledDelete_promptsOnceAfterCancellation() {
        String output = runCli(createSessionInput(ADD_RICE, "delete-recipe 1", "n", "list-recipes", "bye"));
        assertEquals(4, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.contains("Cancelled. Nothing was changed." + System.lineSeparator()
                + CONTINUE_PROMPT), output);
        assertTrue(output.contains("1. Rice (200 kcal)"), output);
    }

    //@@author peilingggg
    @TestFactory
    Stream<DynamicTest> main_invalidRecipe_recoversForNextCommand() throws IOException {
        return AddRecipeTestData.invalidCases().stream().map(testCase ->
                DynamicTest.dynamicTest(testCase[0], () -> {
                    String output = runCli("Tester" + System.lineSeparator()
                            + testCase[2] + System.lineSeparator()
                            + AddRecipeTestData.VALID_COMMAND + System.lineSeparator()
                            + "bye" + System.lineSeparator());
                    assertTrue(output.contains(testCase[1]), output);
                    assertTrue(output.contains(Parser.ADD_RECIPE_USAGE), output);
                    assertTrue(output.contains(CONTINUE_PROMPT), output);
                    assertEquals(1, countOccurrences(output, "Error: "), output);
                    assertEquals(1, countOccurrences(output, "New recipe added!"), output);
                    assertTrue(output.contains("Rice" + System.lineSeparator()
                            + "200 kcal | Protein: 4g | Carbs: 45g | Fats: 1g"), output);
                    assertTrue(output.contains("- Rice: 150g"), output);
                    assertTrue(output.indexOf("Error: ") < output.indexOf("New recipe added!"), output);
                    assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
                }));
    }

    @TestFactory
    Stream<DynamicTest> main_emptyOrUnknownInput_recoversForNextCommand() {
        return List.of("", "   ", "\t", "unknown", "add-recipes").stream()
                .map(input -> DynamicTest.dynamicTest("Input: [" + input + "]", () -> {
                    String output = runCli("Tester" + System.lineSeparator()
                            + input + System.lineSeparator()
                            + AddRecipeTestData.VALID_COMMAND + System.lineSeparator()
                            + "bye" + System.lineSeparator());
                    String diagnostic = input.isBlank() ? "Enter a command." : "Unknown command: " + input;
                    assertTrue(output.contains("Error: " + diagnostic), output);
                    assertEquals(1, countOccurrences(output, "Format: add-recipe"), output);
                    assertTrue(output.contains(CONTINUE_PROMPT), output);
                    assertEquals(1, countOccurrences(output, "New recipe added!"), output);
                    assertTrue(output.indexOf("Error: ") < output.indexOf("New recipe added!"), output);
                    assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
                }));
    }

    @Test
    void main_invalidRecipeAtEndOfInput_exitsWithoutCrashing() {
        String output = runCli("Tester" + System.lineSeparator() + "add-recipe");
        assertTrue(output.contains("Error: Expected prefixed recipe arguments."), output);
        assertTrue(output.contains(Parser.ADD_RECIPE_USAGE), output);
        assertEquals(0, countOccurrences(output, "New recipe added!"), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_viewingWithoutGeneratedData_displaysPromptsAndContinues() {
        String output = runCli(String.join(System.lineSeparator(),
                "Tester", "view-plan", "view-plan d/2", "view-list", "bye"));

        assertEquals(2, countOccurrences(output, "No active meal plan."), output);
        assertTrue(output.contains("No grocery list generated. Use 'generate-list' first."), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_invalidViewingArguments_recoversWithRelevantUsage() {
        String output = runCli(String.join(System.lineSeparator(), "Tester",
                "view-list extra", "view-plan d/0", "view-list",
                AddRecipeTestData.VALID_COMMAND, "bye"));

        assertEquals(2, countOccurrences(output, "Error: "), output);
        assertEquals(1, countOccurrences(output, "Format: view-list"), output);
        assertEquals(1, countOccurrences(output, "Format: view-plan"), output);
        assertEquals(0, countOccurrences(output, "Format: add-recipe"), output);
        assertTrue(output.contains("No grocery list generated. Use 'generate-list' first."), output);
        assertTrue(output.contains("New recipe added!"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }
    //@@author

    @Test
    void main_invalidThenValid_displaysExpectedOutput() {
        String input = String.join(System.lineSeparator(),
                "Tester",
                "add-recipe",
                "add-recipe n/Rice cal/200 p/4 c/45 f/1 i/Rice:150g",
                "bye") + System.lineSeparator();

        String output = runCli(input);

        String expected = String.join(System.lineSeparator(),
                        stripAnsi(CliFormatter.banner()),
                        stripAnsi(CliFormatter.namePrompt()),
                        stripAnsi(CliFormatter.greeting("Tester")),
                        stripAnsi(CliFormatter.welcome()),
                        CONTINUE_PROMPT,
                        "[ERROR] Error: Expected prefixed recipe arguments.",
                        "[TRY] " + Parser.ADD_RECIPE_USAGE,
                        CONTINUE_PROMPT,
                        "[DONE] New recipe added!",
                        "Rice",
                        "200 kcal | Protein: 4g | Carbs: 45g | Fats: 1g",
                        "Ingredients:",
                        "- Rice: 150g",
                        "Food tags: Unclassified",
                        CONTINUE_PROMPT,
                        "[KITCHEN CLOSED] Goodbye!") + System.lineSeparator();

        assertEquals(expected, output);
    }

    //@@author Rixte
    @Test
    void main_repeatedSessions_logsSubpackagesAndRestoresConfiguration() {
        Logger appLogger = Logger.getLogger("mishmash");
        Level originalLevel = appLogger.getLevel();
        boolean wasUsingParentHandlers = appLogger.getUseParentHandlers();
        List<Handler> originalHandlers = Arrays.asList(appLogger.getHandlers());
        RecordingHandler recordingHandler = new RecordingHandler();
        recordingHandler.setLevel(Level.ALL);
        appLogger.addHandler(recordingHandler);
        // A non-default level checks that application setup restores the caller's configuration.
        appLogger.setLevel(Level.WARNING);
        try {
            for (int session = 0; session < 2; session++) {
                String output = runCli(String.join(System.lineSeparator(),
                        "Tester", "add-recipe", AddRecipeTestData.VALID_COMMAND, "view-list", "bye"));
                assertTrue(output.contains("New recipe added!"), output);
                assertEquals(Level.WARNING, appLogger.getLevel());
                assertEquals(wasUsingParentHandlers, appLogger.getUseParentHandlers());
                List<Handler> expectedHandlers = new ArrayList<>(originalHandlers);
                expectedHandlers.add(recordingHandler);
                assertEquals(expectedHandlers, Arrays.asList(appLogger.getHandlers()));
            }

            List<LogRecord> records = recordingHandler.records;
            assertEquals(2, records.stream().filter(record -> record.getLevel() == Level.INFO
                    && record.getMessage().equals("MishMash started")).count());
            assertEquals(2, records.stream().filter(record -> record.getLevel() == Level.INFO
                    && record.getMessage().equals("MishMash stopped")).count());
            assertEquals(2, records.stream().filter(record -> record.getMessage().equals("Command rejected")
                    && record.getThrown() != null).count());
            assertEquals(2, records.stream().filter(record -> record.getLoggerName().equals(Parser.class.getName())
                    && record.getLevel() == Level.FINE
                    && record.getMessage().startsWith("Parsed add-recipe with ")).count());
            assertEquals(2, records.stream().filter(record -> record.getLoggerName()
                    .equals("mishmash.command.ViewListCommand")).count());
        } finally {
            appLogger.removeHandler(recordingHandler);
            appLogger.setLevel(originalLevel);
            appLogger.setUseParentHandlers(wasUsingParentHandlers);
        }
    }
    //@@author

    //@@author Rixte
    @Test
    void main_runtimeFailure_logsCauseAndRethrowsAfterCleanup() {
        IllegalStateException failure = new IllegalStateException("Broken application dependency");
        InputStream failingInput = new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        };

        assertFailureIsLoggedAndRethrown(failingInput, failure);
    }
    //@@author

    //@@author Rixte
    @Test
    void main_assertionFailure_logsCauseAndRethrowsAfterCleanup() {
        AssertionError failure = new AssertionError("Broken internal invariant");
        InputStream failingInput = new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        };

        assertFailureIsLoggedAndRethrown(failingInput, failure);
    }
    //@@author

    //@@author Rixte
    /**
     * Checks that an unexpected failure is logged once and rethrown with logging configuration restored.
     *
     * @param failingInput The console stream that throws the expected failure when read.
     * @param expectedFailure The original exception or assertion error that must reach the caller.
     */
    private void assertFailureIsLoggedAndRethrown(InputStream failingInput, Throwable expectedFailure) {
        Logger appLogger = Logger.getLogger("mishmash");
        Level originalLevel = appLogger.getLevel();
        boolean wasUsingParentHandlers = appLogger.getUseParentHandlers();
        List<Handler> expectedHandlers = new ArrayList<>(Arrays.asList(appLogger.getHandlers()));
        RecordingHandler recordingHandler = new RecordingHandler();
        expectedHandlers.add(recordingHandler);
        appLogger.addHandler(recordingHandler);

        try {
            Throwable thrown = assertThrows(expectedFailure.getClass(), () -> runCli(failingInput));
            assertSame(expectedFailure, thrown);
            List<LogRecord> failures = recordingHandler.records.stream()
                    .filter(record -> record.getLevel() == Level.SEVERE).toList();
            assertEquals(1, failures.size());
            assertSame(expectedFailure, failures.get(0).getThrown());
            assertEquals(originalLevel, appLogger.getLevel());
            assertEquals(wasUsingParentHandlers, appLogger.getUseParentHandlers());
            assertEquals(expectedHandlers, Arrays.asList(appLogger.getHandlers()));
        } finally {
            appLogger.removeHandler(recordingHandler);
            appLogger.setLevel(originalLevel);
            appLogger.setUseParentHandlers(wasUsingParentHandlers);
        }
    }
    //@@author

    //@@author Rixte
    @Test
    void main_startupFailure_logsCauseOnceAndRestoresLogging() {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        Logger appLogger = Logger.getLogger("mishmash");
        Level originalLevel = appLogger.getLevel();
        boolean wereParentHandlersEnabled = appLogger.getUseParentHandlers();
        List<Handler> originalHandlers = Arrays.asList(appLogger.getHandlers());
        RecordingHandler recordingHandler = new RecordingHandler();
        appLogger.addHandler(recordingHandler);
        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            // A missing output stream makes session construction fail before run() can log anything.
            System.setOut(null);
            NullPointerException error = assertThrows(NullPointerException.class,
                    () -> runMain("mishmash.%g.log"));

            assertEquals("output", error.getMessage());
            List<LogRecord> failures = recordingHandler.records.stream()
                    .filter(record -> record.getLevel() == Level.SEVERE).toList();
            assertEquals(1, failures.size());
            assertSame(error, failures.getFirst().getThrown());
            assertTrue(failures.getFirst().getMessage().contains("startup"));
            assertEquals(originalLevel, appLogger.getLevel());
            assertEquals(wereParentHandlersEnabled, appLogger.getUseParentHandlers());
            List<Handler> expectedHandlers = new ArrayList<>(originalHandlers);
            expectedHandlers.add(recordingHandler);
            assertEquals(expectedHandlers, Arrays.asList(appLogger.getHandlers()));
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
            appLogger.removeHandler(recordingHandler);
        }
    }
    //@@author

    /**
     * Captures records propagated from application subpackages for logging integration checks.
     */
    private static class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // Records are held in memory, so no flushing is needed.
        }

        @Override
        public void close() {
            // This handler owns no external resources.
        }
    }

    /**
     * Runs a CLI session and restores the global streams even if execution fails.
     *
     * @param input The simulated console input.
     * @return The captured output.
     */
    private String runCli(String input) {
        return runCli(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    //@@author peilingggg
    /**
     * Runs a CLI session with a supplied input stream, including one that simulates a failure.
     *
     * @param input The simulated console stream.
     * @return The captured output.
     */
    private String runCli(InputStream input) {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capturedOutput = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(input);
            System.setOut(capturedOutput);
            runMain("mishmash.%g.log");
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        return stripAnsi(output.toString(StandardCharsets.UTF_8));
    }
    //@@author

    /**
     * Counts literal message occurrences without interpreting punctuation as regular expressions.
     *
     * @param output The captured console text.
     * @param message The non-empty message to count.
     * @return The number of occurrences.
     */
    private int countOccurrences(String output, String message) {
        return output.split(Pattern.quote(message), -1).length - 1;
    }

    @Test
    void main_addThenConfirmedDelete_displaysExpectedTranscript() {
        String output = runCli(createSessionInput(ADD_RICE, "delete-recipe 1", "y", "bye"));

        String expected = String.join(System.lineSeparator(),
                "New recipe added!",
                "Rice",
                "200 kcal | Protein: 4g | Carbs: 45g | Fats: 1g",
                "Ingredients:",
                "- Rice: 150g",
                "Food tags: Unclassified",
                CONTINUE_PROMPT,
                "[CONFIRM] Are you sure you want to delete recipe 1: Rice? [y/N]:",
                "[DONE] Recipe deleted!",
                "Rice",
                "Recipes remaining: 0",
                CONTINUE_PROMPT,
                "[KITCHEN CLOSED] Goodbye!") + System.lineSeparator();
        assertTrue(output.endsWith(expected), output);
    }

    @TestFactory
    Stream<DynamicTest> main_deleteConfirmedWithYes_removesTheRecipe() {
        return List.of("y", "Y", "yes", "YES", "Yes", "  yes  ").stream()
                .map(answer -> DynamicTest.dynamicTest("Answer: [" + answer + "]", () -> {
                    // After Rice is deleted only Oats remains, so index 2 must no longer exist.
                    String output = runCli(createSessionInput(ADD_RICE, ADD_OATS, "delete-recipe 1", answer,
                            "delete-recipe 2", "bye"));

                    assertEquals(1, countOccurrences(output, "Recipe deleted!"), output);
                    assertTrue(output.contains("Recipes remaining: 1"), output);
                    assertTrue(output.contains("Error: Recipe index must be between 1 and 1."), output);
                    assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
                }));
    }

    @TestFactory
    Stream<DynamicTest> main_deleteNotConfirmed_keepsTheRecipe() {
        // "bye" at the prompt only cancels the delete; it does not exit the program.
        return List.of("n", "N", "no", "", "   ", "maybe", "yes please", "yy", "bye").stream()
                .map(answer -> DynamicTest.dynamicTest("Answer: [" + answer + "]", () -> {
                    // The second delete only works if the first one left the recipe in place.
                    String output = runCli(createSessionInput(ADD_RICE, "delete-recipe 1", answer,
                            "delete-recipe 1", "y", "bye"));

                    assertEquals(2, countOccurrences(output,
                            "Are you sure you want to delete recipe 1: Rice? [y/N]:"), output);
                    assertEquals(1, countOccurrences(output, "Cancelled. Nothing was changed."), output);
                    assertEquals(1, countOccurrences(output, "Recipe deleted!"), output);
                    assertTrue(output.indexOf("Cancelled.") < output.indexOf("Recipe deleted!"), output);
                    assertEquals(1, countOccurrences(output, "Goodbye!"), output);
                    assertEquals(0, countOccurrences(output, "Error: "), output);
                }));
    }

    @Test
    void main_deleteThenEndOfInput_cancelsInsteadOfDeleting() {
        String output = runCli(createSessionInput(ADD_RICE, "delete-recipe 1"));

        assertTrue(output.contains("Are you sure you want to delete recipe 1: Rice? [y/N]:"), output);
        assertTrue(output.contains("Cancelled. Nothing was changed."), output);
        assertEquals(0, countOccurrences(output, "Recipe deleted!"), output);
    }

    @Test
    void main_deleteMissingRecipe_showsErrorWithoutAsking() {
        String output = runCli(createSessionInput("delete-recipe 1", ADD_RICE, "delete-recipe 2", "bye"));

        assertEquals(0, countOccurrences(output, "Are you sure"), output);
        assertEquals(2, countOccurrences(output, "Error: "), output);
        assertEquals(4, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.contains("Error: There are no recipes to delete."), output);
        assertTrue(output.contains("Error: Recipe index must be between 1 and 1."), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @TestFactory
    Stream<DynamicTest> main_invalidDeleteArguments_showsOnlyDeleteUsageAndRecovers() {
        return List.of("delete-recipe", "delete-recipe 0", "delete-recipe -1", "delete-recipe abc",
                        "delete-recipe 1 2", "delete-recipe 2147483648").stream()
                .map(badCommand -> DynamicTest.dynamicTest(badCommand, () -> {
                    String output = runCli(createSessionInput(ADD_RICE, badCommand, "delete-recipe 1", "y", "bye"));

                    assertEquals(1, countOccurrences(output, "Error: "), output);
                    assertEquals(1, countOccurrences(output, "Format: delete-recipe INDEX"), output);
                    assertEquals(0, countOccurrences(output, "Format: add-recipe"), output);
                    // Only the later, valid command reaches the confirmation question.
                    assertEquals(1, countOccurrences(output, "Are you sure"), output);
                    assertEquals(1, countOccurrences(output, "Recipe deleted!"), output);
                    assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
                }));
    }

    @Test
    void main_unknownCommand_listsEveryCommandUsageOnce() {
        String output = runCli(createSessionInput("unknown", "bye"));

        assertTrue(output.contains("Error: Unknown command: unknown"), output);
        assertEquals(1, countOccurrences(output, Parser.ADD_RECIPE_USAGE), output);
        assertEquals(1, countOccurrences(output, Parser.DELETE_RECIPE_USAGE), output);
    }

    @Test
    void main_listRecipes_showsListThenFullRecipeWithoutAsking() {
        String output = runCli(createSessionInput(ADD_RICE, ADD_OATS, "list-recipes", "list-recipes 2", "bye"));

        String expected = String.join(System.lineSeparator(),
                "Recipes:",
                "1. Rice (200 kcal)",
                "2. Oats (150 kcal)",
                CONTINUE_PROMPT,
                "Oats",
                "150 kcal | Protein: 5g | Carbs: 27g | Fats: 3g",
                "Ingredients:",
                "- Oats: 40g",
                "Food tags: Unclassified",
                CONTINUE_PROMPT,
                "[KITCHEN CLOSED] Goodbye!") + System.lineSeparator();
        assertTrue(output.endsWith(expected), output);
        assertEquals(0, countOccurrences(output, "Are you sure"), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
    }

    @Test
    void main_listRecipeWithNoRecipes_continuesWithoutError() {
        String output = runCli(createSessionInput("list-recipes", ADD_RICE, "list-recipes", "bye"));

        assertEquals(1, countOccurrences(output, "No recipes yet. Use 'add-recipe' to add one."), output);
        assertTrue(output.contains("1. Rice (200 kcal)"), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void main_listRecipeBadIndex_showsErrorAndRecovers() {
        String output = runCli(createSessionInput("list-recipes 1", ADD_RICE, "list-recipes 2", "list-recipes abc",
                "list-recipes 1", "bye"));

        assertEquals(3, countOccurrences(output, "Error: "), output);
        assertTrue(output.contains("Error: There are no recipes to show. Use 'add-recipe' to add one."), output);
        assertTrue(output.contains("Error: Recipe index must be between 1 and 1."), output);
        assertTrue(output.contains("Error: Recipe index must be a single positive whole number."), output);
        assertEquals(6, countOccurrences(output, CONTINUE_PROMPT), output);
        // The last command still works after the errors.
        assertTrue(output.contains("- Rice: 150g" + System.lineSeparator()
                + "Food tags: Unclassified" + System.lineSeparator()
                + CONTINUE_PROMPT + System.lineSeparator() + "[KITCHEN CLOSED] Goodbye!"), output);
    }

    @Test
    void main_listRecipeThenDelete_showsTheNumbersDeleteUses() {
        String output = runCli(createSessionInput(ADD_RICE, ADD_OATS, "list-recipes", "delete-recipe 2", "y",
                "list-recipes", "bye"));

        assertTrue(output.contains("Are you sure you want to delete recipe 2: Oats? [y/N]:"), output);
        assertTrue(output.endsWith("Recipes:" + System.lineSeparator() + "1. Rice (200 kcal)"
                + System.lineSeparator() + CONTINUE_PROMPT
                + System.lineSeparator() + "[KITCHEN CLOSED] Goodbye!" + System.lineSeparator()), output);
    }

    //@@author GauthamNandi
    @Test
    void main_recordAndViewMeals_showsFeedbackAndDailyTotals() {
        String today = " | 1 serving | " + LocalDate.now();
        String output = runCli(createSessionInput(
                "add-recipe n/Chicken and Rice cal/650 p/50 c/70 f/15 i/Chicken:200g",
                "add-recipe n/Protein Oatmeal cal/450 p/35 c/60 f/8 i/Oats:50g",
                "record-meal 1", "record-meal 2", "view-meals", "bye"));

        assertTrue(output.contains("Meal recorded!" + System.lineSeparator()
                + "Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)" + today + System.lineSeparator()
                + "Total for " + LocalDate.now() + ": 650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g"), output);
        assertTrue(output.contains("Total for " + LocalDate.now()
                + ": 1100 kcal | Protein: 85g | Carbs: 130g | Fats: 23g"), output);
        assertTrue(output.contains("================ Recorded Meals ================" + System.lineSeparator()
                + "Meal 1: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)" + today + System.lineSeparator()
                + "Meal 2: Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g)" + today + System.lineSeparator()
                + "------------------------------------------------" + System.lineSeparator()
                + "Total: 1100 kcal | Protein: 85g | Carbs: 130g | Fats: 23g" + System.lineSeparator()
                + "================================================"), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
        assertEquals(6, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void main_mealCommandErrors_showMessagesAndKeepSessionRunning() {
        String output = runCli(createSessionInput("view-meals", "record-meal 1",
                "add-recipe n/Chicken and Rice cal/650 p/50 c/70 f/15 i/Chicken:200g",
                "record-meal 9", "record-meal 0", "record-meal x", "record-meal", "view-meals extra",
                "view-meals", "record-meal 1", "view-meals", "bye"));

        assertEquals(2, countOccurrences(output, "No meals recorded yet."), output);
        assertTrue(output.contains("Error: There are no recipes to record. Use 'add-recipe' to add one."), output);
        assertTrue(output.contains("Error: Recipe index must be between 1 and 1."), output);
        assertTrue(output.contains("Error: Recipe index must be at least 1."), output);
        assertTrue(output.contains("Error: Recipe index is required."), output);
        assertTrue(output.contains("Error: Recipe index must be a single positive whole number."), output);
        assertTrue(output.contains("Error: view-meals accepts only an optional date/YYYY-MM-DD."), output);
        assertEquals(6, countOccurrences(output, "Error: "), output);
        // The failed commands record nothing; only the final successful recording appears in the last view.
        assertEquals(1, countOccurrences(output, "Meal recorded!"), output);
        assertEquals(1, countOccurrences(output, "Meal 1: Chicken and Rice"), output);
        assertEquals(12, countOccurrences(output, CONTINUE_PROMPT), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void main_recordedMealsAndPlan_areIndependent() {
        String output = runCli(createSessionInput(
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g",
                "record-meal 1", "view-plan", "view-meals", "bye"));

        assertTrue(output.contains("No active meal plan. Use 'generate-plan' first."), output);
        assertTrue(output.contains("Meal 1: A (600 kcal | P: 30g, C: 50g, F: 10g)"), output);
    }

    @Test
    void main_helpAfterMealCommands_listsThem() {
        String output = runCli(createSessionInput("help", "bye"));

        assertTrue(output.contains("record-meal - Record a recipe you have eaten"), output);
        assertTrue(output.contains("Format: record-meal INDEX"), output);
        assertTrue(output.contains("view-meals - View the meals you have recorded"), output);
        assertTrue(output.contains("Format: view-meals"), output);
    }
    //@@author

    /**
     * Builds console input: the answer to the name prompt, then each line, ending with a newline.
     */
    @Test
    void main_editAndDeleteAfterRecording_keepMealAsRecorded() {
        String output = runCli(createSessionInput(
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g",
                "record-meal 1", "edit-recipe 1 p/99", "delete-recipe 1", "y", "view-meals", "bye"));

        String recorded = "Meal 1: A (600 kcal | P: 30g, C: 50g, F: 10g)";
        assertEquals(1, countOccurrences(output, recorded), output);
        assertFalse(output.contains("P: 99g"), output);
        assertTrue(output.contains("Total: 600 kcal | Protein: 30g | Carbs: 50g | Fats: 10g"), output);
        assertTrue(output.endsWith("Goodbye!" + System.lineSeparator()), output);
    }

    @Test
    void main_generatePlanThenRecordMeal_recordsOnlyTheChosenRecipe() {
        String output = runCli(createSessionInput(
                "add-recipe n/A cal/600 p/30 c/50 f/10 i/Rice:100g",
                "add-recipe n/B cal/400 p/20 c/40 f/5 i/Oats:50g",
                "add-recipe n/C cal/200 p/5 c/20 f/8 i/Kale:50g",
                "generate-plan d/1", "record-meal 2", "view-meals", "bye"));

        assertEquals(1, countOccurrences(output, "Meal recorded!"), output);
        assertTrue(output.contains("B (400 kcal | P: 20g, C: 40g, F: 5g)"), output);
        assertTrue(output.contains("Recorded Meals"), output);
        assertEquals(0, countOccurrences(output, "Error: "), output);
    }

    private static String createSessionInput(String... lines) {
        return "Tester" + System.lineSeparator()
                + String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }
}
