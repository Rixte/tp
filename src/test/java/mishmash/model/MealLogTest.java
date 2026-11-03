package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks that the meal log keeps recorded meals in order and sums their nutrition correctly.
 */
class MealLogTest {
    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);

    @Test
    void newLog_hasNoMealsAndZeroTotals() {
        MealLog log = new MealLog();

        assertTrue(TestRecipes.recordedRecipes(log).isEmpty());
        assertEquals(new NutritionTotals(0, 0, 0, 0), log.getTotals());
    }

    @Test
    void record_severalMeals_keepsThemInRecordedOrder() {
        MealLog log = new MealLog();

        TestRecipes.recordToday(log, oatmeal);
        TestRecipes.recordToday(log, chickenRice);

        assertEquals(List.of(oatmeal, chickenRice), TestRecipes.recordedRecipes(log));
    }

    @Test
    void record_sameRecipeTwice_keepsBothEntries() {
        MealLog log = new MealLog();

        TestRecipes.recordToday(log, chickenRice);
        TestRecipes.recordToday(log, chickenRice);

        assertEquals(2, TestRecipes.recordedRecipes(log).size());
        assertSame(chickenRice, TestRecipes.recordedRecipes(log).get(0));
        assertSame(chickenRice, TestRecipes.recordedRecipes(log).get(1));
    }

    @Test
    void record_nullRecipe_throwsNullPointerExceptionAndLeavesLogUnchanged() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, oatmeal);

        assertThrows(NullPointerException.class, () -> log.record(null));
        assertEquals(List.of(oatmeal), TestRecipes.recordedRecipes(log));
    }

    @Test
    void getTotals_severalMeals_sumsEachNutrient() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, chickenRice);
        TestRecipes.recordToday(log, oatmeal);

        assertEquals(new NutritionTotals(1100, 85, 130, 23), log.getTotals());
    }

    @Test
    void getTotals_repeatedRecipe_countsEveryEntry() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, chickenRice);
        TestRecipes.recordToday(log, chickenRice);
        TestRecipes.recordToday(log, chickenRice);

        assertEquals(new NutritionTotals(1950, 150, 210, 45), log.getTotals());
    }

    @Test
    void getTotals_recipeWithZeroNutrition_addsNothing() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, TestRecipes.recipe("Water", 0, 0, 0, 0));
        TestRecipes.recordToday(log, oatmeal);

        assertEquals(new NutritionTotals(450, 35, 60, 8), log.getTotals());
    }

    @Test
    void getTotals_valuesBeyondIntRange_doesNotOverflow() {
        MealLog log = new MealLog();
        Recipe huge = TestRecipes.recipe("Huge", Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        TestRecipes.recordToday(log, huge);
        TestRecipes.recordToday(log, huge);

        long expected = 2L * Integer.MAX_VALUE;
        assertEquals(new NutritionTotals(expected, expected, expected, expected), log.getTotals());
    }

    @Test
    void getTotals_calledRepeatedly_doesNotChangeLog() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, chickenRice);

        NutritionTotals first = log.getTotals();
        NutritionTotals second = log.getTotals();

        assertEquals(first, second);
        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
    }

    @Test
    void getMeals_returnedSnapshot_cannotBeModified() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, chickenRice);
        List<Recipe> snapshot = TestRecipes.recordedRecipes(log);

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(oatmeal));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(0));
        assertEquals(1, TestRecipes.recordedRecipes(log).size());
    }

    @Test
    void getMeals_earlierSnapshot_doesNotSeeLaterRecordings() {
        MealLog log = new MealLog();
        TestRecipes.recordToday(log, chickenRice);
        List<Recipe> snapshot = TestRecipes.recordedRecipes(log);

        TestRecipes.recordToday(log, oatmeal);

        assertEquals(List.of(chickenRice), snapshot);
        assertEquals(List.of(chickenRice, oatmeal), TestRecipes.recordedRecipes(log));
    }
}
//@@author
