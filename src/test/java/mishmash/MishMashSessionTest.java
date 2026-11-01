package mishmash;

import static mishmash.testutil.CliAssertions.assertInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import mishmash.command.Command;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.AddRecipeTestData;
import mishmash.testutil.LogCapture;

/**
 * Checks isolated application execution, borrowed resource ownership, structured help, and failure diagnostics.
 */
class MishMashSessionTest {
    //@@author Rixte
    @Test
    void main_missingLogDirectory_warnsAndKeepsSessionUsable(@TempDir Path directory) {
        Logger logger = Logger.getLogger("mishmash");
        Level originalLevel = logger.getLevel();
        boolean wereParentHandlersEnabled = logger.getUseParentHandlers();
        List<Handler> originalHandlers = Arrays.asList(logger.getHandlers());
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        try (LogCapture logs = new LogCapture(MishMash.class)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (PrintStream output = new PrintStream(bytes)) {
                System.setIn(new ByteArrayInputStream("Tester\nbye\n".getBytes(StandardCharsets.UTF_8)));
                System.setOut(output);
                MishMash.main(new String[0], directory.resolve("missing/app.%g.log").toString());
            } finally {
                System.setIn(originalInput);
                System.setOut(originalOutput);
            }
            assertTrue(logs.getMessages().contains("continuing without file logging"));
            assertInstanceOf(IOException.class, logs.getRecords().getFirst().getThrown());
            assertEquals(originalLevel, logger.getLevel());
            assertEquals(wereParentHandlersEnabled, logger.getUseParentHandlers());
            assertEquals(originalHandlers, Arrays.asList(logger.getHandlers()));

            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Goodbye!"));
        }
    }

    @Test
    void run_emptyInput_finishesWithoutInventingAName() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(""); PrintStream output = new PrintStream(bytes)) {
            new MishMash(scanner, output, new Parser(), new RecipeBook()).run();
        }
        String display = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(display.contains("Who's cooking today?"));
        assertTrue(display.contains("Welcome to MishMash!"));
        assertFalse(display.contains("Hello "));
    }
    //@@author

    @TestFactory
    Stream<DynamicTest> constructor_missingDependency_rejectsBeforeWritingOutput() {
        return Stream.of("scanner", "output", "parser", "recipeBook")
                .map(dependency -> DynamicTest.dynamicTest(dependency, () -> {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (Scanner scanner = new Scanner("Tester\nbye\n"); PrintStream output = new PrintStream(bytes)) {
                        NullPointerException error = assertThrows(NullPointerException.class,
                                () -> new MishMash(dependency.equals("scanner") ? null : scanner,
                                        dependency.equals("output") ? null : output,
                                        dependency.equals("parser") ? null : new Parser(),
                                        dependency.equals("recipeBook") ? null : new RecipeBook()));

                        assertEquals(dependency, error.getMessage());
                        assertEquals(0, bytes.size());
                    }
                }));
    }

    @Test
    void run_borrowedStreams_preservesConsoleAndLoggingConfiguration() {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        Logger logger = Logger.getLogger("mishmash");
        Level originalLevel = logger.getLevel();
        boolean wereParentHandlersEnabled = logger.getUseParentHandlers();
        List<Handler> originalHandlers = Arrays.asList(logger.getHandlers());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner("Tester\nbye\nRemaining input\n");
                PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new MishMash(scanner, output, new Parser(), new RecipeBook()).run();

            assertEquals("Remaining input", scanner.nextLine());
            output.println("Output remains open");
            assertInOrder(bytes.toString(StandardCharsets.UTF_8), "Hello Tester", "Goodbye!", "Output remains open");
            assertFalse(output.checkError());
        }
        assertSame(originalInput, System.in);
        assertSame(originalOutput, System.out);
        assertEquals(originalLevel, logger.getLevel());
        assertEquals(wereParentHandlersEnabled, logger.getUseParentHandlers());
        assertEquals(originalHandlers, Arrays.asList(logger.getHandlers()));
    }

    @TestFactory
    Stream<DynamicTest> run_recoverableError_choosesHelpFromMetadataAndContinuesInOrder() {
        return Stream.of(false, true).map(hasSpecificUsage -> DynamicTest.dynamicTest(
                hasSpecificUsage ? "Specific usage without Format marker" : "Format marker inside explanation", () -> {
                    String explanation = hasSpecificUsage ? "Invalid day." : "Unrecognized input: Format: fake";
                    String usage = hasSpecificUsage ? "Use view-plan with an optional day." : null;
                    Parser parser = new Parser() {
                        @Override
                        public Command parseCommand(String input) throws MishMashException {
                            if (input.equals("bad")) {
                                throw new MishMashException(explanation, usage, null);
                            }
                            return super.parseCommand(input);
                        }
                    };

                    String output = runSession("Tester\nbad\nview-plan\nbye\n", parser);

                    assertInOrder(output, "Error: " + explanation, hasSpecificUsage ? usage : Parser.COMMAND_USAGE,
                            "No active meal plan. Use 'generate-plan' first.", "Goodbye!");
                    assertEquals(!hasSpecificUsage, output.contains(Parser.ADD_RECIPE_USAGE));
                }));
    }

    @TestFactory
    Stream<DynamicTest> run_unexpectedFailure_logsOriginalCauseOnceAndRethrows() {
        return Stream.of(new IllegalStateException("Broken dependency"), new AssertionError("Broken invariant"))
                .map(failure -> DynamicTest.dynamicTest(failure.getClass().getSimpleName(), () -> {
                    Parser parser = new Parser() {
                        @Override
                        public Command parseCommand(String input) {
                            if (failure instanceof RuntimeException runtimeFailure) {
                                throw runtimeFailure;
                            }
                            throw (AssertionError) failure;
                        }
                    };
                    try (LogCapture logs = new LogCapture(MishMash.class)) {
                        assertSame(failure, assertThrows(failure.getClass(),
                                () -> runSession("Tester\ncommand\n", parser)));
                        List<LogRecord> failures = logs.getRecords().stream()
                                .filter(record -> record.getLevel() == Level.SEVERE).toList();
                        assertEquals(1, failures.size());
                        assertSame(failure, failures.getFirst().getThrown());
                        assertTrue(logs.getMessages().endsWith("MishMash stopped"));
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> run_brokenCommandContract_displaysErrorAndKeepsRecipesForNextCommand() {
        return Stream.of("command", "confirmationPrompt", "command feedback")
                .map(contract -> DynamicTest.dynamicTest(contract, () -> {
                    Command command = brokenCommand(contract);
                    Parser parser = new Parser() {
                        @Override
                        public Command parseCommand(String input) throws MishMashException {
                            return input.equals("bad") ? command : super.parseCommand(input);
                        }
                    };
                    try (LogCapture logs = new LogCapture(MishMash.class)) {
                        RecipeBook book = new RecipeBook();
                        String output = runSession("Tester\n" + AddRecipeTestData.VALID_COMMAND
                                + "\nbad\nlist-recipes\nbye\n", parser, book);

                        assertInOrder(output, "New recipe added!", "Error: ", "1. Rice (200 kcal)", "Goodbye!");
                        assertEquals(1, book.size());
                        assertEquals(1, logs.getRecords().stream()
                                .filter(record -> record.getMessage().equals("Command rejected")
                                        && record.getThrown() instanceof MishMashException).count());
                        assertEquals(2, logs.getRecords().stream()
                                .filter(record -> record.getMessage().startsWith("Completed ")).count());
                        assertEquals(0, logs.getRecords().stream()
                                .filter(record -> record.getLevel() == Level.SEVERE).count());
                    }
                }));
    }

    /**
     * Returns a broken command, or null, to simulate an internal dependency violating its documented contract.
     */
    private Command brokenCommand(String contract) {
        if (contract.equals("command")) {
            return null;
        }
        return new Command() {
            @Override
            public Optional<String> confirmationPrompt(RecipeBook recipeBook) {
                return contract.equals("confirmationPrompt") ? null : Optional.empty();
            }

            @Override
            public String execute(RecipeBook recipeBook) {
                return null;
            }
        };
    }

    @Test
    void run_blankConfirmation_rejectsWithoutExecutingAndPreservesNextInput() {
        Parser parser = new Parser() {
            @Override
            public Command parseCommand(String input) throws MishMashException {
                if (!input.equals("bad")) {
                    return super.parseCommand(input);
                }
                return new Command() {
                    @Override
                    public Optional<String> confirmationPrompt(RecipeBook recipeBook) {
                        return Optional.of(" ");
                    }

                    @Override
                    public String execute(RecipeBook recipeBook) {
                        throw new AssertionError("Command must not execute.");
                    }
                };
            }
        };
        try (LogCapture logs = new LogCapture(MishMash.class)) {
            RecipeBook book = new RecipeBook();
            String output = runSession("Tester\n" + AddRecipeTestData.VALID_COMMAND
                    + "\nbad\nlist-recipes\nbye\n", parser, book);

            assertInOrder(output, "New recipe added!", "Error: Confirmation prompts must not be blank.",
                    "1. Rice (200 kcal)", "Goodbye!");
            assertEquals(1, book.size());
            assertEquals(0, logs.getRecords().stream().filter(record -> record.getLevel() == Level.SEVERE).count());
        }
    }

    @Test
    void run_generationFailureWithoutMessage_showsFallbackAndContinuesWithRecipes() {
        IllegalArgumentException failure = new IllegalArgumentException();
        MealPlanGenerator generator = new MealPlanGenerator(new Random(1)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                throw failure;
            }
        };
        Parser parser = new Parser(generator, new ActiveMealPlan());
        RecipeBook book = new RecipeBook();
        try (LogCapture logs = new LogCapture(MishMash.class)) {
            String output = runSession("Tester\n" + AddRecipeTestData.VALID_COMMAND
                    + "\ngenerate-plan d/1\nlist-recipes\nbye\n", parser, book);

            assertInOrder(output, "New recipe added!", "Error: Command could not be completed.",
                    "1. Rice (200 kcal)", "Goodbye!");
            assertEquals(1, book.size());
            LogRecord rejection = logs.getRecords().stream()
                    .filter(record -> record.getMessage().equals("Command rejected")).findFirst().orElseThrow();
            assertSame(failure, rejection.getThrown().getCause());
            assertEquals(0, logs.getRecords().stream().filter(record -> record.getLevel() == Level.SEVERE).count());
        }
    }

    @TestFactory
    Stream<DynamicTest> run_invalidCommandSyntax_displaysUsageExactlyOnce() {
        return Stream.of("help extra", "set-diet nonsense", "view-diet extra", "check-shortfall cal/",
                "edit-recipe", "add-recipe", "generate-list extra", "generate-plan", "delete-recipe",
                "record-meal", "view-meals extra", "list-recipes bad", "view-list extra", "view-plan bad",
                "view-plan d/0").map(input -> DynamicTest.dynamicTest(input, () -> {
                    MishMashException error = assertThrows(MishMashException.class,
                            () -> new Parser().parseCommand(input));
                    String usage = error.getUsage().orElseThrow();
                    assertFalse(error.getExplanation().contains("Format: "), error.getExplanation());

                    String output = runSession("Tester\n" + input + "\nbye\n", new Parser());

                    assertEquals(1, output.split(Pattern.quote(usage), -1).length - 1, output);
                    assertEquals(1, output.split("Format: ", -1).length - 1, output);
                    assertInOrder(output, "Error: " + error.getExplanation(), usage, "Goodbye!");
                }));
    }

    /**
     * Runs a session with local streams; the test owns and closes those streams after execution.
     */
    private String runSession(String input, Parser parser) {
        return runSession(input, parser, new RecipeBook());
    }

    /**
     * Runs a session with a recipe book that the caller can inspect after recovery.
     */
    private String runSession(String input, Parser parser, RecipeBook recipeBook) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
                PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new MishMash(scanner, output, parser, recipeBook).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
