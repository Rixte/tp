package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.exception.MishMashException;
import mishmash.model.ApplicationState;
import mishmash.model.DayPlan;
import mishmash.model.DietaryPreferences;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealEntry;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;

/** Checks malformed numbers, control sequences and literal Unicode without hiding unchecked failures. */
class HostileCommandTest {
    @Test
    void parseAndExecute_largeMalformedInputs_remainsRecoverable() {
        for (String input : List.of("generate-plan d/" + "9".repeat(100_000),
                "replace-meal d/1 m/1 r/" + "\u0661".repeat(100_000),
                "customize-meal d/1 m/1 i/" + "x:".repeat(25_000) + "1g")) {
            assertRecoverableAndUnchanged(input);
        }
    }

    @TestFactory
    Stream<DynamicTest> parseAndExecute_invalidNumbers_rejectsWithoutChangingExistingData() {
        String[] templates = {"generate-plan d/%s", "generate-plan d/1 cal/%s", "set-targets cal/%s",
            "view-plan d/%s", "adjust-portion d/1 m/1 portions/%s", "customize-meal d/%s m/1 i/Rice:1g",
            "replace-meal d/1 m/1 r/%s", "list-recipes %s", "record-meal %s", "record-meal 1 s/%s",
            "edit-meal %s s/1", "delete-meal %s", "delete-recipe %s", "edit-recipe %s n/Test",
            "check-shortfall cal/%s"};
        String[] values = {"-1", "-2147483648", "0", "+1", "2147483648", "9".repeat(1024), "1.5", "1e3",
            "NaN", "Infinity", "\u0661", "\uFF11", "1;bye", "1\u0000", "1\u001B[2J"};
        List<DynamicTest> tests = new ArrayList<>();
        for (String template : templates) {
            for (int index = 0; index < values.length; index++) {
                String input = String.format(Locale.ROOT, template, values[index]);
                tests.add(DynamicTest.dynamicTest(template + " invalid value " + index,
                        () -> assertRecoverableAndUnchanged(input)));
            }
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_controlCharacters_rejectsBeforeTrimmingOrEchoing() {
        return Stream.of(0, 7, 27, 28, 31, 127, 128, 155, 157).map(code -> DynamicTest.dynamicTest(
                "control U+" + Integer.toHexString(code), () -> {
                    String input = "help" + (char) code.intValue();
                    MishMashException error = assertThrows(MishMashException.class,
                            () -> new Parser().parseCommand(input));
                    assertTrue(error.getExplanation().contains("control characters"));
                }));
    }

    @TestFactory
    Stream<DynamicTest> parseAndExecute_printableUnicodeAndSymbols_preservesLiteralData() {
        return Stream.of("\u7C73\u996D", "\u0623\u0631\u0632", "\u0411\u043E\u0440\u0449", "Caf\u00E9",
                "Cafe\u0301", "\uD83E\uDD55", "O'Reilly", "' OR 1=1 --", "literal_\";$(whoami)|&<>{}[]%s")
                .map(name -> DynamicTest.dynamicTest("literal text: " + name, () -> {
                    ApplicationState state = new ApplicationState();
                    new Parser(state).parseCommand("add-recipe n/" + name
                            + " cal/1 p/0 c/0 f/0 i/" + name + ":1g").executeWithResult(state.getRecipeBook());
                    Recipe recipe = state.getRecipeBook().getRecipes().getFirst();
                    assertEquals(1, state.getRecipeBook().size());
                    assertEquals(name, recipe.getName());
                    assertEquals(name, recipe.getIngredients().getFirst().getName());
                }));
    }

    /** Verifies exception type and the retained plan, request, groceries, recipes and meal log. */
    private void assertRecoverableAndUnchanged(String input) {
        ApplicationState state = populatedState();
        MealPlan plan = state.getActiveMealPlan().get().orElseThrow();
        GeneratePlanRequest request = state.getActiveMealPlan().getRequest().orElseThrow();
        List<GroceryItem> groceries = state.getActiveMealPlan().getGroceryList().orElseThrow();
        List<Recipe> recipes = state.getRecipeBook().getRecipes();
        List<MealEntry> entries = state.getMealLog().getEntries();
        DietaryPreferences preferences = state.getDietarySettings().get();
        NutritionTargets targets = state.getTargetSettings().get();
        Parser parser = new Parser(state);
        assertThrows(MishMashException.class,
                () -> parser.parseCommand(input).executeWithResult(state.getRecipeBook()));
        assertSame(plan, state.getActiveMealPlan().get().orElseThrow());
        assertSame(request, state.getActiveMealPlan().getRequest().orElseThrow());
        assertSame(groceries, state.getActiveMealPlan().getGroceryList().orElseThrow());
        assertEquals(recipes, state.getRecipeBook().getRecipes());
        assertEquals(entries, state.getMealLog().getEntries());
        assertSame(preferences, state.getDietarySettings().get());
        assertSame(targets, state.getTargetSettings().get());
    }

    /** Supplies an existing two-day plan and snapshots so rejected commands cannot pass by clearing empty state. */
    private ApplicationState populatedState() {
        ApplicationState state = new ApplicationState();
        for (String name : List.of("A", "B", "C")) {
            state.getRecipeBook().add(new Recipe(name, 100, 10, 10, 1,
                    List.of(new GroceryItem("Rice", BigDecimal.ONE, "g"))));
        }
        DayPlan day = new DayPlan(state.getRecipeBook().getRecipes());
        state.getActiveMealPlan().setGenerated(new MealPlan(List.of(day, day)),
                new GeneratePlanRequest(2, NutritionTargets.none()),
                Optional.of(List.of(new GroceryItem("Rice", new BigDecimal("6"), "g"))));
        state.getMealLog().record(new MealEntry(state.getRecipeBook().getRecipes().getFirst(),
                LocalDate.of(2024, 1, 2), 1));
        return state;
    }
}
