package mishmash;

import static mishmash.testutil.CliAssertions.assertInOrder;
import static mishmash.testutil.CliAssertions.stripAnsi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;
import mishmash.ui.ConsoleInput;
import mishmash.ui.JLineConsoleInput;

//@@author Rixte
/**
 * Runs the application through JLine to check parser compatibility, confirmation ordering, and EOF recovery.
 */
class JLineSessionTest {
    @Test
    void run_confirmedReset_preservesRecipesAndAcceptsFollowingCommands() throws IOException {
        ActiveMealPlan active = new ActiveMealPlan();
        RecipeBook recipes = TestRecipes.recipeBook(500, 20, 50, 10);
        Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

        String output = run(parser, recipes, "Tester", "generate-plan d/1", "generate-list", "reset-plan",
                "yes", "view-plan", "list-recipes", "bye");

        assertInOrder(output, "Hello Tester", "Meal plan reset.", "No active meal plan.", "Goodbye!");
        assertEquals(3, recipes.size());
        assertTrue(active.get().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
        assertFalse(output.contains("Error:"), output);
    }

    @Test
    void run_eofDuringConfirmation_cancelsWithoutClearingThePlan() throws IOException {
        ActiveMealPlan active = new ActiveMealPlan();
        RecipeBook recipes = TestRecipes.recipeBook(500, 20, 50, 10);
        Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

        String output = run(parser, recipes, "Tester", "generate-plan d/1", "reset-plan");

        assertTrue(output.contains("Cancelled. Nothing was changed."), output);
        assertTrue(active.get().isPresent());
        assertEquals(3, recipes.size());
    }

    @Test
    void run_literalRecipeNameAndInvalidInput_preservesTextAndRecovers() throws IOException {
        RecipeBook recipes = new RecipeBook();
        String name = "A's \\ Soup!!";
        String output = run(new Parser(), recipes, "Tester", "unknown",
                "add-recipe n/" + name + " cal/100 p/1 c/2 f/3 i/Rice:10g", "list-recipes", "bye");

        assertEquals(name, recipes.getRecipes().getFirst().getName());
        assertInOrder(output, "Error:", "New recipe added!", name, "Goodbye!");
    }

    @Test
    void run_borrowedInput_doesNotCloseTheCallersAdapter() {
        ConsoleInput input = new ConsoleInput() {
            @Override
            public Optional<String> readAnswer(String prompt) {
                return Optional.empty();
            }

            @Override
            public void close() {
                throw new AssertionError("The session must not close borrowed input");
            }
        };
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            new MishMash(input, output, new Parser(), new RecipeBook()).run();
        }
    }

    @TestFactory
    Stream<DynamicTest> constructor_missingAdapterDependency_identifiesTheMissingArgument() {
        return Stream.of("input", "output", "parser", "recipeBook").map(dependency ->
                DynamicTest.dynamicTest(dependency, () -> {
                    try (ConsoleInput input = new JLineConsoleInput(new DumbTerminal(
                            new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
                            PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
                        NullPointerException failure = assertThrows(NullPointerException.class,
                                () -> new MishMash(dependency.equals("input") ? null : input,
                                        dependency.equals("output") ? null : output,
                                        dependency.equals("parser") ? null : new Parser(),
                                        dependency.equals("recipeBook") ? null : new RecipeBook()));
                        assertEquals(dependency, failure.getMessage());
                    }
                }));
    }

    /**
     * Exercises the real editor and application loop using deterministic non-native terminal streams.
     */
    private String run(Parser parser, RecipeBook recipes, String... lines) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] commands = (String.join("\n", List.of(lines)) + "\n").getBytes(StandardCharsets.UTF_8);
        try (ConsoleInput input = new JLineConsoleInput(new DumbTerminal(new ByteArrayInputStream(commands), bytes));
                PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new MishMash(input, output, parser, recipes).run();
        }
        return stripAnsi(bytes.toString(StandardCharsets.UTF_8));
    }
}
//@@author
