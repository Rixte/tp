package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.MealLog;
import mishmash.model.NutritionTotals;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks that record-meal logs the chosen recipe, reports the running totals, and rejects bad indices
 * without changing the log or the recipe book.
 */
class RecordMealCommandTest {
    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);
    private final Recipe salad = TestRecipes.recipe("Salmon Salad", 550, 42, 20, 32);
    private final MealLog log = new MealLog();

    private RecipeBook bookOf(Recipe... recipes) {
        RecipeBook book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.add(recipe);
        }
        return book;
    }

    @Test
    void constructor_indexBelowOne_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new RecordMealCommand(0, log));
        assertThrows(IllegalArgumentException.class, () -> new RecordMealCommand(-1, log));
        assertThrows(IllegalArgumentException.class, () -> new RecordMealCommand(Integer.MIN_VALUE, log));
    }

    @Test
    void constructor_nullLog_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new RecordMealCommand(1, null));
    }

    @Test
    void execute_validIndex_recordsThatRecipeAndReturnsFeedback() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal, salad);

        String result = new RecordMealCommand(2, log).execute(book);

        String expected = String.join(System.lineSeparator(),
                "Meal recorded!",
                "Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | " + LocalDate.now(),
                "Total so far: 450 kcal | Protein: 35g | Carbs: 60g | Fats: 8g");
        assertEquals(expected, result);
        assertEquals(List.of(oatmeal), log.getMeals());
    }

    @Test
    void execute_firstAndLastIndex_recordTheCorrectRecipes() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal, salad);

        new RecordMealCommand(1, log).execute(book);
        new RecordMealCommand(3, log).execute(book);

        assertEquals(List.of(chickenRice, salad), log.getMeals());
    }

    @Test
    void execute_secondMeal_reportsCumulativeTotals() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal);
        new RecordMealCommand(1, log).execute(book);

        String result = new RecordMealCommand(2, log).execute(book);

        assertTrue(result.endsWith("Total so far: 1100 kcal | Protein: 85g | Carbs: 130g | Fats: 23g"), result);
        assertEquals(new NutritionTotals(1100, 85, 130, 23), log.getTotals());
    }

    @Test
    void execute_sameIndexTwice_recordsTwoEntries() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);
        RecordMealCommand command = new RecordMealCommand(1, log);

        command.execute(book);
        command.execute(book);

        assertEquals(List.of(chickenRice, chickenRice), log.getMeals());
        assertEquals(new NutritionTotals(1300, 100, 140, 30), log.getTotals());
    }

    @Test
    void execute_emptyRecipeBook_throwsAndRecordsNothing() {
        MishMashException error = assertThrows(MishMashException.class,
                () -> new RecordMealCommand(1, log).execute(new RecipeBook()));

        assertEquals("There are no recipes to record. Use 'add-recipe' to add one.", error.getExplanation());
        assertTrue(log.getMeals().isEmpty());
    }

    @Test
    void execute_indexTooLarge_throwsWithValidRangeAndRecordsNothing() {
        RecipeBook book = bookOf(chickenRice, oatmeal, salad);

        MishMashException error = assertThrows(MishMashException.class,
                () -> new RecordMealCommand(4, log).execute(book));
        assertThrows(MishMashException.class,
                () -> new RecordMealCommand(Integer.MAX_VALUE, log).execute(book));

        assertEquals("Recipe index must be between 1 and 3.", error.getExplanation());
        assertTrue(log.getMeals().isEmpty());
    }

    @Test
    void execute_validRecording_doesNotChangeRecipeBook() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal);

        new RecordMealCommand(1, log).execute(book);

        assertEquals(List.of(chickenRice, oatmeal), book.getRecipes());
    }

    @Test
    void execute_recipeDeletedAfterRecording_keepsTheRecordedMeal() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal);
        new RecordMealCommand(1, log).execute(book);

        book.remove(0);

        assertEquals(List.of(chickenRice), log.getMeals());
        assertEquals(new NutritionTotals(650, 50, 70, 15), log.getTotals());
    }

    @Test
    void execute_indexIsReadWhenRunNotWhenCreated() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);
        RecordMealCommand command = new RecordMealCommand(2, log);

        book.add(oatmeal);
        command.execute(book);

        assertEquals(List.of(oatmeal), log.getMeals());
    }

    @Test
    void execute_nullRecipeBook_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new RecordMealCommand(1, log).execute(null));
    }

    @Test
    void confirmationPrompt_recordingNeedsNoConfirmation() throws MishMashException {
        assertEquals(Optional.empty(), new RecordMealCommand(1, log).confirmationPrompt(bookOf(chickenRice)));
        assertTrue(log.getMeals().isEmpty());
    }

    @Test
    void usage_namesTheCommandAndItsIndex() {
        assertEquals("Format: record-meal INDEX [date/YYYY-MM-DD] [s/SERVINGS]", RecordMealCommand.USAGE);
        assertEquals("record-meal", RecordMealCommand.COMMAND_WORD);
    }
}
//@@author
