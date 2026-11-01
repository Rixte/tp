package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks meal entries, their corrections, and the log operations that depend on dates and servings.
 */
class MealEntryTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);

    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);

    @Test
    void constructor_servingsAtLimits_accepted() {
        assertEquals(1, new MealEntry(oatmeal, MONDAY, 1).servings());
        assertEquals(MealEntry.MAX_SERVINGS, new MealEntry(oatmeal, MONDAY, MealEntry.MAX_SERVINGS).servings());
    }

    @Test
    void constructor_invalidFields_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new MealEntry(oatmeal, MONDAY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MealEntry(oatmeal, MONDAY, MealEntry.MAX_SERVINGS + 1));
        assertThrows(NullPointerException.class, () -> new MealEntry(null, MONDAY, 1));
        assertThrows(NullPointerException.class, () -> new MealEntry(oatmeal, null, 1));
    }

    @Test
    void nutrition_multipliesEveryNutrientByServings() {
        assertEquals(new NutritionTotals(1950, 150, 210, 45), new MealEntry(chickenRice, MONDAY, 3).nutrition());
    }

    @Test
    void nutrition_largeRecipe_doesNotOverflow() {
        Recipe huge = TestRecipes.recipe("Huge", Integer.MAX_VALUE, 0, 0, 0);

        assertEquals(2L * Integer.MAX_VALUE, new MealEntry(huge, MONDAY, 2).nutrition().calories());
    }

    @Test
    void with_suppliedFieldsChangeAndOmittedFieldsStay() {
        MealEntry entry = new MealEntry(oatmeal, MONDAY, 2);

        assertEquals(new MealEntry(oatmeal, TUESDAY, 2), entry.with(new MealEntryChanges(TUESDAY, null)));
        assertEquals(new MealEntry(oatmeal, MONDAY, 5), entry.with(new MealEntryChanges(null, 5)));
        assertEquals(new MealEntry(oatmeal, MONDAY, 2), entry);
    }

    @Test
    void changes_reportWhetherAnythingWasSuppliedAndCheckServings() {
        assertFalse(new MealEntryChanges(null, null).hasAnyChange());
        assertTrue(new MealEntryChanges(MONDAY, null).hasAnyChange());
        assertTrue(new MealEntryChanges(null, 1).hasAnyChange());
        assertThrows(IllegalArgumentException.class, () -> new MealEntryChanges(null, 0));
    }

    @Test
    void log_getTotalsOn_countsOnlyThatDateAndItsServings() {
        MealLog log = new MealLog();
        log.record(new MealEntry(chickenRice, MONDAY, 2));
        log.record(new MealEntry(oatmeal, TUESDAY, 1));

        assertEquals(new NutritionTotals(1300, 100, 140, 30), log.getTotalsOn(MONDAY));
        assertEquals(new NutritionTotals(450, 35, 60, 8), log.getTotalsOn(TUESDAY));
        assertEquals(new NutritionTotals(0, 0, 0, 0), log.getTotalsOn(LocalDate.of(2026, 3, 4)));
        assertEquals(new NutritionTotals(1750, 135, 200, 38), log.getTotals());
    }

    @Test
    void log_replace_keepsPositionAndUpdatesTotals() {
        MealLog log = new MealLog();
        MealEntry second = new MealEntry(oatmeal, MONDAY, 1);
        log.record(new MealEntry(chickenRice, MONDAY, 1));
        log.record(second);

        log.replace(0, new MealEntry(chickenRice, MONDAY, 2));

        assertEquals(2, log.getEntries().get(0).servings());
        assertSame(second, log.getEntries().get(1));
        assertEquals(new NutritionTotals(1750, 135, 200, 38), log.getTotalsOn(MONDAY));
    }

    @Test
    void log_remove_shiftsLaterEntriesAndUpdatesTotals() {
        MealLog log = new MealLog();
        MealEntry first = new MealEntry(chickenRice, MONDAY, 1);
        MealEntry second = new MealEntry(oatmeal, MONDAY, 1);
        log.record(first);
        log.record(second);

        assertSame(first, log.remove(0));

        assertEquals(List.of(second), log.getEntries());
        assertEquals(new NutritionTotals(450, 35, 60, 8), log.getTotalsOn(MONDAY));
    }

    @Test
    void log_invalidPositions_throwAndLeaveLogUnchanged() {
        MealLog log = new MealLog();
        MealEntry entry = new MealEntry(oatmeal, MONDAY, 1);
        log.record(entry);

        assertThrows(IndexOutOfBoundsException.class, () -> log.remove(1));
        assertThrows(IndexOutOfBoundsException.class, () -> log.replace(1, entry));
        assertThrows(NullPointerException.class, () -> log.replace(0, null));
        assertEquals(List.of(entry), log.getEntries());
    }
}
//@@author
