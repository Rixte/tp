package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.model.MealEntry;
import mishmash.model.NutritionTotals;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks the layout of the recorded-meal display and the feedback shown after changing the log.
 */
class MealLogViewerTest {
    private static final String HEADER = "================ Recorded Meals ================";
    private static final String DIVIDER = "------------------------------------------------";
    private static final String FOOTER = "================================================";
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);

    private final MealLogViewer viewer = new MealLogViewer();
    private final MealEntry chickenRice =
            new MealEntry(TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15), MONDAY, 1);
    private final MealEntry oatmeal =
            new MealEntry(TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8), TUESDAY, 1);

    @Test
    void view_severalMeals_listsEachMealThenTotals() {
        String expected = String.join(System.lineSeparator(),
                HEADER,
                "Meal 1: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g) | 1 serving | 2026-03-02",
                "Meal 2: Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | 2026-03-03",
                DIVIDER,
                "Total: 1100 kcal | Protein: 85g | Carbs: 130g | Fats: 23g",
                FOOTER);

        assertEquals(expected,
                viewer.view(List.of(chickenRice, oatmeal), new NutritionTotals(1100, 85, 130, 23)));
    }

    @Test
    void view_sameRecipeTwice_showsTwoNumberedLines() {
        String result = viewer.view(List.of(oatmeal, oatmeal), new NutritionTotals(900, 70, 120, 16));

        assertEquals(1, countOccurrences(result, "Meal 1: Protein Oatmeal"));
        assertEquals(1, countOccurrences(result, "Meal 2: Protein Oatmeal"));
    }

    @Test
    void view_noMeals_showsOnlyHeaderAndZeroTotals() {
        String expected = String.join(System.lineSeparator(),
                HEADER,
                DIVIDER,
                "Total: 0 kcal | Protein: 0g | Carbs: 0g | Fats: 0g",
                FOOTER);

        assertEquals(expected, viewer.view(List.of(), new NutritionTotals(0, 0, 0, 0)));
    }

    @Test
    void view_totalsBeyondIntRange_printsFullValue() {
        long beyondInt = 2L * Integer.MAX_VALUE;

        String result = viewer.view(List.of(oatmeal), new NutritionTotals(beyondInt, 0, 0, 0));

        assertEquals(1, countOccurrences(result, "Total: 4294967294 kcal"));
    }

    @Test
    void view_decorativeLines_matchTheHeaderWidth() {
        assertEquals(HEADER.length(), DIVIDER.length());
        assertEquals(HEADER.length(), FOOTER.length());
    }

    @Test
    void view_nullArgumentsOrMeal_throwNullPointerException() {
        NutritionTotals totals = new NutritionTotals(0, 0, 0, 0);

        assertThrows(NullPointerException.class, () -> viewer.view(null, totals));
        assertThrows(NullPointerException.class, () -> viewer.view(List.of(oatmeal), null));
        assertThrows(NullPointerException.class, () -> viewer.view(Arrays.asList(oatmeal, null), totals));
    }

    @Test
    void viewDate_keepsLogNumbersAndLabelsTheTotal() {
        String result = viewer.viewDate(List.of(chickenRice, oatmeal), TUESDAY, new NutritionTotals(450, 35, 60, 8));

        assertFalse(result.contains("Chicken and Rice"), result);
        assertTrue(result.contains("Meal 2: Protein Oatmeal"), result);
        assertTrue(result.contains("Total for 2026-03-03: 450 kcal"), result);
    }

    @Test
    void describe_severalServings_scalesNutritionAndUsesPlural() {
        MealEntry twoServings = new MealEntry(chickenRice.recipe(), MONDAY, 2);

        assertEquals("Chicken and Rice (1300 kcal | P: 100g, C: 140g, F: 30g) | 2 servings | 2026-03-02",
                viewer.describe(twoServings));
    }

    @Test
    void viewRecorded_laterMeal_showsThatMealAndTheTotalsForItsDate() {
        String expected = String.join(System.lineSeparator(),
                "Meal recorded!",
                "Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | 2026-03-03",
                "Total for 2026-03-03: 1100 kcal | Protein: 85g | Carbs: 130g | Fats: 23g");

        assertEquals(expected, viewer.viewRecorded(oatmeal, new NutritionTotals(1100, 85, 130, 23)));
    }

    @Test
    void viewRecorded_nullArguments_throwNullPointerException() {
        NutritionTotals totals = new NutritionTotals(0, 0, 0, 0);

        assertThrows(NullPointerException.class, () -> viewer.viewRecorded(null, totals));
        assertThrows(NullPointerException.class, () -> viewer.viewRecorded(oatmeal, null));
    }

    @Test
    void viewEdited_showsTheMealAndItsDateTotals() {
        String expected = String.join(System.lineSeparator(),
                "Meal updated!",
                "Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | 2026-03-03",
                "Total for 2026-03-03: 450 kcal | Protein: 35g | Carbs: 60g | Fats: 8g");

        assertEquals(expected, viewer.viewEdited(oatmeal, new NutritionTotals(450, 35, 60, 8)));
    }

    @Test
    void viewDeleted_showsTheMealAndRemainingDateTotals() {
        String expected = String.join(System.lineSeparator(),
                "Meal deleted!",
                "Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g) | 1 serving | 2026-03-03",
                "Total for 2026-03-03: 0 kcal | Protein: 0g | Carbs: 0g | Fats: 0g");

        assertEquals(expected, viewer.viewDeleted(oatmeal, new NutritionTotals(0, 0, 0, 0)));
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        for (int from = text.indexOf(part); from >= 0; from = text.indexOf(part, from + part.length())) {
            count++;
        }
        return count;
    }
}
//@@author
