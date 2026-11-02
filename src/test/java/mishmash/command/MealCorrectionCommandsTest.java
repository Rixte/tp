package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.MealEntry;
import mishmash.model.MealEntryChanges;
import mishmash.model.MealLog;
import mishmash.model.NutritionTotals;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks reviewing meals by date, and correcting or removing them, from parsing through to the log.
 */
class MealCorrectionCommandsTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);

    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);
    private final RecipeBook book = new RecipeBook();
    private final MealLog log = new MealLog();
    private final Parser parser = new Parser(log);
    private final MealEntry mondayMeal = new MealEntry(chickenRice, MONDAY, 1);
    private final MealEntry tuesdayMeal = new MealEntry(oatmeal, TUESDAY, 1);

    private void recordBothMeals() {
        log.record(mondayMeal);
        log.record(tuesdayMeal);
    }

    @Test
    void recordMeal_withDateAndServings_storesThem() throws MishMashException {
        book.add(chickenRice);

        String feedback = parser.parseCommand("record-meal 1 date/2026-03-02 s/2").execute(book);

        assertEquals(List.of(new MealEntry(chickenRice, MONDAY, 2)), log.getEntries());
        assertTrue(feedback.contains("1300 kcal"), feedback);
        assertTrue(feedback.contains("2 servings | 2026-03-02"), feedback);
    }

    @Test
    void recordMeal_withoutDetails_usesOneServingToday() throws MishMashException {
        book.add(chickenRice);
        LocalDate before = LocalDate.now();

        parser.parseCommand("record-meal 1").execute(book);

        MealEntry entry = log.getEntries().getFirst();
        assertEquals(1, entry.servings());
        assertTrue(!entry.date().isBefore(before) && !entry.date().isAfter(LocalDate.now()));
    }

    @Test
    void viewMeals_withDate_showsThatDateAndKeepsLogNumbers() throws MishMashException {
        recordBothMeals();

        String view = parser.parseCommand("view-meals date/2026-03-03").execute(book);

        assertTrue(view.contains("Meal 2: Protein Oatmeal"), view);
        assertTrue(view.contains("Total for 2026-03-03: 450 kcal"), view);
        assertTrue(!view.contains("Chicken and Rice"), view);
    }

    @Test
    void viewMeals_dateWithNoMeals_isANoteRatherThanAnError() throws MishMashException {
        recordBothMeals();

        Command command = parser.parseCommand("view-meals date/2026-03-09");

        assertEquals(CommandResult.note("No meals recorded on 2026-03-09."), command.executeWithResult(book));
    }

    @Test
    void viewMeals_invalidArguments_showViewMealsUsage() {
        for (String input : List.of("view-meals extra", "view-meals s/2", "view-meals date/2026-02-30")) {
            MishMashException error = assertThrows(MishMashException.class, () -> parser.parseCommand(input));
            assertEquals(Optional.of(Parser.VIEW_MEALS_USAGE), error.getUsage(), input);
        }
    }

    @Test
    void editMeal_servings_updatesEntryAndReportsDateTotals() throws MishMashException {
        recordBothMeals();

        String feedback = parser.parseCommand("edit-meal 1 s/2").execute(book);

        assertEquals(List.of(new MealEntry(chickenRice, MONDAY, 2), tuesdayMeal), log.getEntries());
        assertTrue(feedback.contains("Total for 2026-03-02: 1300 kcal"), feedback);
    }

    @Test
    void editMeal_date_movesTheMealBetweenDailyTotals() throws MishMashException {
        recordBothMeals();

        parser.parseCommand("edit-meal 1 date/2026-03-03").execute(book);

        assertEquals(new NutritionTotals(0, 0, 0, 0), log.getTotalsOn(MONDAY));
        assertEquals(new NutritionTotals(1100, 85, 130, 23), log.getTotalsOn(TUESDAY));
    }

    @Test
    void editMeal_needsNoConfirmation() throws MishMashException {
        recordBothMeals();

        assertEquals(Optional.empty(), parser.parseCommand("edit-meal 1 s/2").confirmationPrompt(book));
    }

    @Test
    void editMeal_invalidSyntax_showsEditMealUsageAndLeavesLogUnchanged() {
        recordBothMeals();

        for (String input : List.of("edit-meal", "edit-meal 1", "edit-meal x s/2", "edit-meal 1 s/0",
                "edit-meal 1 date/2999-01-01")) {
            MishMashException error = assertThrows(MishMashException.class, () -> parser.parseCommand(input));
            assertEquals(Optional.of(Parser.EDIT_MEAL_USAGE), error.getUsage(), input);
        }
        assertEquals(List.of(mondayMeal, tuesdayMeal), log.getEntries());
    }

    @Test
    void editMeal_numberBeyondLog_isRejectedWhenRunAndLeavesLogUnchanged() throws MishMashException {
        recordBothMeals();
        Command command = parser.parseCommand("edit-meal 3 s/2");

        MishMashException error = assertThrows(MishMashException.class, () -> command.execute(book));

        assertEquals("Meal number must be between 1 and 2.", error.getExplanation());
        assertEquals(List.of(mondayMeal, tuesdayMeal), log.getEntries());
    }

    @Test
    void editMeal_emptyLog_pointsToRecordMeal() throws MishMashException {
        Command command = parser.parseCommand("edit-meal 1 s/2");

        MishMashException error = assertThrows(MishMashException.class, () -> command.execute(book));

        assertEquals("No meals recorded yet. Use 'record-meal' to add one.", error.getExplanation());
    }

    @Test
    void editMeal_constructor_rejectsInvalidArguments() {
        MealEntryChanges changes = new MealEntryChanges(null, 2);

        assertThrows(IllegalArgumentException.class, () -> new EditMealCommand(0, changes, log));
        assertThrows(NullPointerException.class, () -> new EditMealCommand(1, null, log));
        assertThrows(NullPointerException.class, () -> new EditMealCommand(1, changes, null));
    }

    @Test
    void deleteMeal_removesEntryAndReportsRemainingDateTotals() throws MishMashException {
        recordBothMeals();
        log.record(new MealEntry(oatmeal, MONDAY, 1));

        String feedback = parser.parseCommand("delete-meal 1").execute(book);

        assertEquals(2, log.getEntries().size());
        assertEquals(tuesdayMeal, log.getEntries().getFirst());
        assertTrue(feedback.contains("Meal deleted!"), feedback);
        assertTrue(feedback.contains("Total for 2026-03-02: 450 kcal"), feedback);
    }

    @Test
    void deleteMeal_confirmationDescribesTheMealWithoutChangingTheLog() throws MishMashException {
        recordBothMeals();

        Optional<String> prompt = parser.parseCommand("delete-meal 2").confirmationPrompt(book);

        assertEquals(Optional.of("Are you sure you want to delete meal 2: Protein Oatmeal"
                + " (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | 2026-03-03? (y/n)"), prompt);
        assertEquals(List.of(mondayMeal, tuesdayMeal), log.getEntries());
    }

    @Test
    void deleteMeal_numberBeyondLog_isReportedBeforeConfirmation() throws MishMashException {
        recordBothMeals();
        Command command = parser.parseCommand("delete-meal 9");

        MishMashException error = assertThrows(MishMashException.class, () -> command.confirmationPrompt(book));

        assertEquals(Optional.of(Parser.DELETE_MEAL_USAGE), error.getUsage());
    }

    @Test
    void deleteMeal_invalidSyntax_showsDeleteMealUsage() {
        for (String input : List.of("delete-meal", "delete-meal 0", "delete-meal x", "delete-meal 1 2")) {
            MishMashException error = assertThrows(MishMashException.class, () -> parser.parseCommand(input));
            assertEquals(Optional.of(Parser.DELETE_MEAL_USAGE), error.getUsage(), input);
        }
    }

    @Test
    void deleteMeal_constructor_rejectsInvalidArguments() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DeleteMealCommand(0, log));

        assertEquals("Meal number must be at least 1.", error.getMessage());
        assertThrows(NullPointerException.class, () -> new DeleteMealCommand(1, null));
    }

    @Test
    void parser_buildsTheExpectedCommands() throws MishMashException {
        assertInstanceOf(EditMealCommand.class, parser.parseCommand("edit-meal 1 s/2"));
        assertInstanceOf(DeleteMealCommand.class, parser.parseCommand("delete-meal 1"));
    }

    @Test
    void help_listsTheNewCommands() {
        String help = new HelpCommand().execute(book);

        assertTrue(help.contains(EditMealCommand.USAGE), help);
        assertTrue(help.contains(DeleteMealCommand.USAGE), help);
    }

    @Test
    void editMeal_valuesAlreadySet_isANoteAndChangesNothing() throws MishMashException {
        recordBothMeals();

        Command command = parser.parseCommand("edit-meal 1 s/1 date/2026-03-02");

        assertEquals(CommandResult.note("Meal 1 already has those details. Nothing was changed."),
                command.executeWithResult(book));
        assertEquals(List.of(mondayMeal, tuesdayMeal), log.getEntries());
    }
}
//@@author
