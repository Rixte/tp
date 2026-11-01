package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;

//@@author Rixte
/**
 * Checks reset diagnostics and failures while restoring logging settings after each test.
 */
class ResetPlanDiagnosticsTest {
    private final Recipe recipe = TestRecipes.recipe("Private recipe", 100, 10, 20, 5);
    private final MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));

    @Test
    void execute_resetAndRepeatedReset_logsPreviousStateWithoutRecipeContents() throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        active.set(plan);
        active.setGroceryList(List.of());
        ResetPlanCommand reset = new ResetPlanCommand(active);

        try (RecordingHandler handler = new RecordingHandler(ResetPlanCommand.class)) {
            reset.execute(new RecipeBook());
            reset.execute(new RecipeBook());

            assertEquals(List.of("Reset plan state; had plan: true; had groceries: true",
                    "Reset plan state; had plan: false; had groceries: false"), handler.messages());
            assertTrue(handler.records.stream().allMatch(record -> record.getLevel() == Level.FINE));
            assertFalse(handler.messages().stream().anyMatch(message -> message.contains(recipe.getName())));
        }
    }

    @Test
    void execute_modelFailure_retainsCauseInRecoverableErrorWithoutLoggingSuccess() {
        IllegalStateException failure = new IllegalStateException("Simulated model failure");
        ActiveMealPlan broken = new ActiveMealPlan() {
            @Override
            public void clear() {
                throw failure;
            }
        };
        broken.set(plan);

        try (RecordingHandler handler = new RecordingHandler(ResetPlanCommand.class)) {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> new ResetPlanCommand(broken).execute(new RecipeBook()));

            assertSame(failure, error.getCause());
            assertSame(plan, broken.get().orElseThrow());
            assertTrue(handler.records.isEmpty());
        }
    }

    @Test
    void execute_brokenClearContract_doesNotLogSuccessfulReset() {
        ActiveMealPlan broken = new ActiveMealPlan() {
            @Override
            public void clear() {
                // Simulates a model bug that leaves stale plan state behind.
            }
        };
        broken.set(plan);

        try (RecordingHandler handler = new RecordingHandler(ResetPlanCommand.class)) {
            assertThrows(MishMashException.class, () -> new ResetPlanCommand(broken).execute(new RecipeBook()));

            assertTrue(handler.records.isEmpty());
        }
    }

    @Test
    void parseCommand_rejectedResetThenRetry_logsOutcomeWithoutArgumentsOrPrematureReset() throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        active.set(plan);
        Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);

        try (RecordingHandler handler = new RecordingHandler(Parser.class)) {
            assertThrows(MishMashException.class, () -> parser.parseCommand("reset-plan " + recipe.getName()));
            assertSame(plan, active.get().orElseThrow());
            Command reset = parser.parseCommand("reset-plan");

            assertSame(plan, active.get().orElseThrow());
            List<String> outcomes = handler.messages().stream()
                    .filter(message -> message.startsWith("reset-plan rejected:")
                            || message.startsWith("Parsed reset-plan")).toList();
            assertEquals(List.of("reset-plan rejected: unexpected arguments", "Parsed reset-plan with no arguments"),
                    outcomes);
            assertFalse(handler.messages().stream().anyMatch(message -> message.contains(recipe.getName())));
            reset.execute(new RecipeBook());
            assertTrue(active.get().isEmpty());
        }
    }

    /**
     * Captures one logger temporarily and restores its original level and parent-handler setting on close.
     */
    private static class RecordingHandler extends Handler implements AutoCloseable {
        private final List<LogRecord> records = new ArrayList<>();
        private final Logger logger;
        private final Level originalLevel;
        private final boolean originalUseParentHandlers;

        /**
         * Attaches a temporary handler without replacing other registered handlers.
         */
        private RecordingHandler(Class<?> source) {
            logger = Logger.getLogger(source.getName());
            originalLevel = logger.getLevel();
            originalUseParentHandlers = logger.getUseParentHandlers();
            setLevel(Level.ALL);
            logger.addHandler(this);
            logger.setLevel(Level.FINE);
            logger.setUseParentHandlers(false);
        }

        private List<String> messages() {
            return records.stream().map(LogRecord::getMessage).toList();
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // Records are already in memory.
        }

        /**
         * Removes this temporary handler and restores the settings captured at construction.
         */
        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
        }
    }
}
//@@author
