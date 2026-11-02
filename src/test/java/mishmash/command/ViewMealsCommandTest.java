package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.MealEntry;
import mishmash.model.MealLog;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks that view-meals displays the log as it is when run, without changing it.
 */
class ViewMealsCommandTest {
    private static final String HEADER = "================ Recorded Meals ================";
    private static final String DIVIDER = "------------------------------------------------";
    private static final String FOOTER = "================================================";

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);
    private final RecipeBook recipeBook = new RecipeBook();
    private final MealLog log = new MealLog();

    @Test
    void constructor_nullLog_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ViewMealsCommand(null));
    }

    @Test
    void execute_noMealsRecorded_returnsEmptyMessage() {
        assertEquals("No meals recorded yet.", new ViewMealsCommand(log).execute(recipeBook));
    }

    @Test
    void execute_recordedMeals_listsEachMealAndTotals() {
        log.record(new MealEntry(chickenRice, MONDAY, 1));
        log.record(new MealEntry(oatmeal, MONDAY, 2));
        String expected = String.join(System.lineSeparator(),
                HEADER,
                "Meal 1: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g) | 1 serving | 2026-03-02",
                "Meal 2: Protein Oatmeal (900 kcal | P: 70g, C: 120g, F: 16g) | 2 servings | 2026-03-02",
                DIVIDER,
                "Total: 1550 kcal | Protein: 120g | Carbs: 190g | Fats: 31g",
                FOOTER);

        assertEquals(expected, new ViewMealsCommand(log).execute(recipeBook));
    }

    @Test
    void execute_mealRecordedAfterCommandCreated_isIncluded() {
        ViewMealsCommand command = new ViewMealsCommand(log);
        assertEquals("No meals recorded yet.", command.execute(recipeBook));

        TestRecipes.recordToday(log, oatmeal);

        assertTrue(command.execute(recipeBook).contains("Meal 1: Protein Oatmeal"));
    }

    @Test
    void execute_repeatedViewing_leavesLogAndRecipesUnchanged() {
        recipeBook.add(chickenRice);
        TestRecipes.recordToday(log, chickenRice);
        ViewMealsCommand command = new ViewMealsCommand(log);

        String first = command.execute(recipeBook);
        String second = command.execute(recipeBook);

        assertEquals(first, second);
        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
        assertEquals(List.of(chickenRice), recipeBook.getRecipes());
    }

    @Test
    void execute_nullRecipeBook_isIgnoredBecauseViewingDoesNotReadRecipes() {
        TestRecipes.recordToday(log, oatmeal);

        assertTrue(new ViewMealsCommand(log).execute(null).contains("Meal 1: Protein Oatmeal"));
    }

    @Test
    void confirmationPrompt_viewingNeedsNoConfirmation() throws MishMashException {
        assertEquals(Optional.empty(), new ViewMealsCommand(log).confirmationPrompt(recipeBook));
    }

    @Test
    void usage_namesTheCommandAndItsOptionalDate() {
        assertEquals("Format: view-meals [date/YYYY-MM-DD]", ViewMealsCommand.USAGE);
        assertEquals("view-meals", ViewMealsCommand.COMMAND_WORD);
    }
}
//@@author
