package mishmash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.command.Command;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;

//@@author Rixte
/**
 * Verifies plan-changing confirmation decisions and their effects through the real console loop.
 */
class PlanConfirmationTest {
    private final ActiveMealPlan active = new ActiveMealPlan();
    private final RecipeBook book = TestRecipes.recipeBook(500, 20, 50, 10);
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

    /**
     * Runs a session with supplied commands and confirmation answers.
     */
    private String run(String... lines) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner input = new Scanner("Tester\n" + String.join("\n", lines));
                PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new MishMash(input, output, parser, book).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * Installs a generated plan and groceries without consuming console input.
     */
    private void populate() throws MishMashException {
        parser.parseCommand("generate-plan d/2 cal/1500").execute(book);
        parser.parseCommand("generate-list").execute(book);
    }

    @TestFactory
    Stream<DynamicTest> cancellation_preservesAllPlanStateAndOtherSettings() {
        return Stream.of("reset-plan", "generate-plan d/1", "set-diet diet/vegan")
                .flatMap(command -> Stream.of("n", "NO", "", "maybe", "bye", "view-plan", "yes please")
                        .map(answer -> DynamicTest.dynamicTest(command + ": " + answer, () -> {
                            populate();
                            parser.parseCommand("set-targets p/60").execute(book);
                            parser.parseCommand("record-meal 1").execute(book);
                            MealPlan plan = active.get().orElseThrow();
                            var request = active.getRequest();
                            var groceries = active.getGroceryList();
                            String output = run(command, answer, "view-targets", "view-diet", "view-meals", "bye");
                            assertTrue(output.contains("Cancelled. Nothing was changed."), output);
                            assertSame(plan, active.get().orElseThrow());
                            assertEquals(request, active.getRequest());
                            assertEquals(groceries, active.getGroceryList());
                            assertTrue(output.contains("60"), output);
                            assertTrue(output.contains("Meal 1: A"), output);
                            assertTrue(output.contains("Goodbye!"), output);
                            assertEquals(3, book.size());
                            assertTrue(parser.parseCommand("set-diet diet/none")
                                    .confirmationPrompt(book).isEmpty());
                        })));
    }

    @TestFactory
    Stream<DynamicTest> endOfInput_cancelsEachDestructiveCommand() {
        return Stream.of("reset-plan", "generate-plan d/1", "set-diet diet/vegan")
                .map(command -> DynamicTest.dynamicTest(command, () -> {
                    populate();
                    MealPlan old = active.get().orElseThrow();
                    var groceries = active.getGroceryList();
                    String output = run(command);
                    assertTrue(output.contains("Cancelled. Nothing was changed."), output);
                    assertSame(old, active.get().orElseThrow());
                    assertEquals(groceries, active.getGroceryList());
                }));
    }

    @TestFactory
    Stream<DynamicTest> affirmativeAnswers_allowResetAndReplacement() {
        return Stream.of("y", "Y", "yes", "YeS", "  yes  ")
                .flatMap(answer -> Stream.of("reset-plan", "generate-plan d/1")
                        .map(command -> DynamicTest.dynamicTest(command + ": " + answer, () -> {
                            populate();
                            String output = run(command, answer, "bye");
                            assertFalse(output.contains("Cancelled."), output);
                            assertFalse(output.contains("Error:"), output);
                            assertTrue(active.getGroceryList().isEmpty());
                            if (command.equals("reset-plan")) {
                                assertTrue(active.get().isEmpty());
                                assertTrue(active.getRequest().isEmpty());
                            } else {
                                assertEquals(1, active.get().orElseThrow().getNumberOfDays());
                                assertEquals(1, active.getRequest().orElseThrow().days());
                            }
                        })));
    }

    @Test
    void confirmedGenerationFailure_preservesPlanRequestAndGroceries() throws MishMashException {
        populate();
        MealPlan old = active.get().orElseThrow();
        var request = active.getRequest();
        var groceries = active.getGroceryList();
        String output = run("generate-plan d/1 cal/1", "yes", "view-list", "bye");
        assertTrue(output.contains("Error:"), output);
        assertTrue(output.contains("Goodbye!"), output);
        assertSame(old, active.get().orElseThrow());
        assertEquals(request, active.getRequest());
        assertEquals(groceries, active.getGroceryList());
    }

    @Test
    void invalidSyntax_doesNotConsumeNextCommand() throws MishMashException {
        populate();
        String output = run("generate-plan d/0", "reset-plan extra", "set-diet diet/invalid", "view-plan", "bye");
        assertFalse(output.contains("(y/n)"), output);
        assertTrue(output.contains("Daily Totals:"), output);
        assertTrue(output.contains("Goodbye!"), output);
        assertEquals(2, active.get().orElseThrow().getNumberOfDays());
    }

    @Test
    void prompts_readLatestStateWithoutMutatingIt() throws MishMashException {
        Command reset = parser.parseCommand("reset-plan");
        Command generate = parser.parseCommand("generate-plan d/1");
        Command diet = parser.parseCommand("set-diet diet/vegan");
        for (Command command : List.of(reset, generate, diet)) {
            assertTrue(command.confirmationPrompt(book).isEmpty());
        }
        populate();
        MealPlan old = active.get().orElseThrow();
        var request = active.getRequest();
        var groceries = active.getGroceryList();
        for (Command command : List.of(reset, generate, diet)) {
            assertTrue(command.confirmationPrompt(book).orElseThrow().endsWith("(y/n)"));
            assertSame(old, active.get().orElseThrow());
            assertEquals(request, active.getRequest());
            assertEquals(groceries, active.getGroceryList());
        }
        assertTrue(parser.parseCommand("set-diet diet/none").confirmationPrompt(book).isEmpty());
        active.clear();
        for (Command command : List.of(reset, generate, diet)) {
            assertTrue(command.confirmationPrompt(book).isEmpty());
        }
    }

    @Test
    void reset_promptsForOrphanedRequestAndEmptyGroceries() throws MishMashException {
        ActiveMealPlan orphan = new ActiveMealPlan() {
            @Override
            public Optional<GeneratePlanRequest> getRequest() {
                return Optional.of(new GeneratePlanRequest(1, NutritionTargets.none()));
            }
        };
        assertTrue(new mishmash.command.ResetPlanCommand(orphan).confirmationPrompt(book).isPresent());
        active.setGroceryList(List.of());
        assertTrue(parser.parseCommand("reset-plan").confirmationPrompt(book).isPresent());
        assertTrue(run("reset-plan", "yes", "bye").contains("Meal plan reset."));
        assertTrue(active.getGroceryList().isEmpty());
    }

    @Test
    void confirmedDietRepair_usesNewRecipesAndRefreshesGroceries() throws MishMashException {
        populate();
        MealPlan old = active.get().orElseThrow();
        for (int index = 1; index <= 3; index++) {
            parser.parseCommand("edit-recipe " + index + " food/none").execute(book);
        }
        String output = run("set-diet diet/vegan", "yes", "view-diet", "bye");
        assertTrue(output.contains("Conflicting days were replaced."), output);
        assertTrue(output.contains("grocery list has also been updated"), output);
        assertFalse(old == active.get().orElseThrow());
        assertTrue(active.getGroceryList().isPresent());
        assertEquals(2, active.get().orElseThrow().getNumberOfDays());
    }

    @Test
    void compatibleDietAndFirstGeneration_doNotConsumeFollowingCommand() {
        String output = run("reset-plan", "set-diet diet/none", "generate-plan d/1",
                "set-diet diet/none", "view-plan", "bye");
        assertFalse(output.contains("(y/n)"), output);
        assertFalse(output.contains("Cancelled."), output);
        assertTrue(output.contains("Daily Totals:"), output);
        assertTrue(output.contains("Goodbye!"), output);
    }

    @Test
    void confirmedDietFailure_clearsPlanAndSavesPreferences() throws MishMashException {
        populate();
        String output = run("set-diet diet/vegan", "yes", "view-diet", "bye");
        assertTrue(output.contains("previous meal plan and grocery list have been cleared"), output);
        assertTrue(output.contains("vegan"), output);
        assertTrue(active.get().isEmpty());
        assertTrue(active.getRequest().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
    }
}
//@@author
