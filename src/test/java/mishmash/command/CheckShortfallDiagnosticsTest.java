package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.AssertionDisabledClassLoader;
import mishmash.testutil.LogCapture;
import mishmash.testutil.TestRecipes;

/**
 * Checks nutrition-shortfall logs, parser exception causes, and recovery after invalid targets.
 */
class CheckShortfallDiagnosticsTest {
    //@@author Rixte
    @Test
    void execute_negativeModelTotal_rejectsBrokenInvariantBeforeReportingSuccess() {
        DayPlan invalidDay = new DayPlan(List.of()) {
            @Override
            public long getTotalCalories() {
                return -1;
            }
        };
        ActiveMealPlan active = new ActiveMealPlan();
        active.set(new MealPlan(List.of(invalidDay)));
        CheckShortfallCommand command = new CheckShortfallCommand(active,
                new NutritionTargets(100, null, null, null));
        try (LogCapture logs = new LogCapture(CheckShortfallCommand.class)) {
            AssertionError error = assertThrows(AssertionError.class, () -> command.execute(new RecipeBook()));

            assertEquals("Planned nutrition totals must not be negative.", error.getMessage());
            assertTrue(logs.getRecords().isEmpty());
        }
    }

    @Test
    void execute_assertionsDisabled_reportsCorrectDeficitWithoutChangingPlan() throws Exception {
        ActiveMealPlan active = new ActiveMealPlan();
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(TestRecipes.recipe("Meal", 50, 1, 1, 1)))));
        active.set(plan);
        try (AssertionDisabledClassLoader loader = new AssertionDisabledClassLoader(CheckShortfallCommand.class)) {
            Class<?> commandClass = loader.loadClass(CheckShortfallCommand.class.getName());
            Command command = (Command) commandClass.getConstructor(ActiveMealPlan.class, NutritionTargets.class)
                    .newInstance(active, new NutritionTargets(100, null, null, null));

            assertFalse(commandClass.desiredAssertionStatus());
            assertTrue(command.execute(new RecipeBook()).contains("Calories: 50/100 kcal (short by 50 kcal)."));
            assertEquals(plan, active.get().orElseThrow());
        }
    }
    //@@author

    @Test
    void execute_mixedDays_logsCountOfAffectedDaysWithoutRecipeContents() {
        ActiveMealPlan active = new ActiveMealPlan();
        active.set(new MealPlan(List.of(
                new DayPlan(List.of(TestRecipes.recipe("Private recipe", 50, 1, 1, 1))),
                new DayPlan(List.of(TestRecipes.recipe("Another recipe", 100, 2, 1, 1))),
                new DayPlan(List.of()))));
        try (LogCapture logs = new LogCapture(CheckShortfallCommand.class)) {
            new CheckShortfallCommand(active, new NutritionTargets(100, 2, null, null)).execute(new RecipeBook());

            assertEquals(1, logs.getRecords().size());
            assertTrue(logs.getMessages().contains("3 days; days with shortfalls: 2"));
            assertFalse(logs.getMessages().contains("Private recipe"));
            assertTrue(logs.getRecords().stream().allMatch(record -> record.getLevel() == Level.FINE));
        }
    }

    @Test
    void execute_noDeficits_logsZeroAffectedDays() {
        ActiveMealPlan active = new ActiveMealPlan();
        active.set(new MealPlan(List.of(new DayPlan(List.of(TestRecipes.recipe("Meal", 100, 1, 1, 1))))));
        try (LogCapture logs = new LogCapture(CheckShortfallCommand.class)) {
            new CheckShortfallCommand(active, new NutritionTargets(100, null, null, null)).execute(new RecipeBook());

            assertTrue(logs.getMessages().contains("days with shortfalls: 0"));
        }
    }

    @Test
    void execute_missingAndEmptyPlans_logsSkippedCheckWithoutSuccess() {
        ActiveMealPlan active = new ActiveMealPlan();
        CheckShortfallCommand command = new CheckShortfallCommand(active, new NutritionTargets(1, null, null, null));
        try (LogCapture logs = new LogCapture(CheckShortfallCommand.class)) {
            command.execute(new RecipeBook());
            active.set(new MealPlan(List.of()));
            command.execute(new RecipeBook());

            assertEquals(2, logs.getRecords().size());
            assertTrue(logs.getRecords().stream().allMatch(record -> record.getMessage().contains("skipped")));
            assertFalse(logs.getMessages().contains("Checked nutrition"));
        }
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_overflowingTargets_preservesNumericCauseAndCommandUsage() {
        return Stream.of("cal", "p", "c", "f").map(prefix -> DynamicTest.dynamicTest(prefix, () -> {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> new Parser().parseCommand("check-shortfall " + prefix + "/2147483648"));

            assertTrue(error.getMessage().endsWith(CheckShortfallCommand.USAGE));
            assertTrue(error.getMessage().contains(prefix + "/"));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertInstanceOf(NumberFormatException.class, error.getCause().getCause());
        }));
    }

    @Test
    void parseCommand_rejectedTargets_logsCommandWithoutRawInputAndRecovers() throws MishMashException {
        Parser parser = new Parser();
        try (LogCapture logs = new LogCapture(Parser.class)) {
            assertThrows(MishMashException.class, () -> parser.parseCommand("check-shortfall cal/PrivateTarget"));

            assertTrue(logs.getMessages().contains("check-shortfall rejected"));
            assertFalse(logs.getMessages().contains("Private"));
            assertFalse(logs.getMessages().contains("Parsed "));
            parser.parseCommand("check-shortfall p/1");
            assertTrue(logs.getMessages().contains("Parsed check-shortfall"));
        }
    }
}
