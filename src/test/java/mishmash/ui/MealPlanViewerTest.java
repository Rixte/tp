package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.model.NutritionTargets;
import mishmash.testutil.AssertionDisabledClassLoader;
import mishmash.ui.MealPlanViewer.MealSummary;

/**
 * Checks the view-plan display, day selection, nutrition totals, and invalid input handling.
 */
class MealPlanViewerTest {
    private static final String DAY_ONE_DISPLAY = String.join(System.lineSeparator(),
            "================ Day 1 ================",
            "Meal 1: Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g)",
            "Meal 2: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)",
            "Meal 3: Salmon Salad (550 kcal | P: 42g, C: 20g, F: 32g)",
            "--------------------------------------",
            "Daily Totals: 1650 kcal | Protein: 127g | Carbs: 150g | Fats: 55g",
            "======================================");
    private static final String DAY_TWO_BODY = String.join(System.lineSeparator(),
            "Meal 1: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)",
            "Meal 2: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)",
            "Meal 3: Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g)",
            "--------------------------------------",
            "Daily Totals: 1750 kcal | Protein: 135g | Carbs: 200g | Fats: 38g",
            "======================================");

    private static final String DAY_TWO_DISPLAY =
            "================ Day 2 ================" + System.lineSeparator() + DAY_TWO_BODY;

    private final MealPlanViewer viewer = new MealPlanViewer();

    //@@author Rixte
    @Test
    void view_assertionsDisabled_preservesMealsAndDailyTotals() throws Exception {
        List<List<MealSummary>> mealsByDay = List.of(createMealsByDay().getFirst());
        try (AssertionDisabledClassLoader loader = new AssertionDisabledClassLoader(MealPlanViewer.class)) {
            Class<?> viewerClass = loader.loadClass(MealPlanViewer.class.getName());
            Object isolatedViewer = viewerClass.getConstructor().newInstance();

            assertFalse(viewerClass.desiredAssertionStatus());
            assertEquals(DAY_ONE_DISPLAY, viewerClass.getMethod("view", List.class)
                    .invoke(isolatedViewer, mealsByDay));
            assertEquals(DAY_ONE_DISPLAY, viewer.view(mealsByDay));
        }
    }
    //@@author

    //@@author Rixte
    @Test
    void formatDay_invalidInternalDay_detectsBrokenInvariant() throws ReflectiveOperationException {
        // Public entry points reject invalid days first; invoke the formatter to check its internal safeguard.
        Method formatter = MealPlanViewer.class.getDeclaredMethod(
                "formatDay", List.class, int.class, NutritionTargets.class);
        formatter.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> formatter.invoke(viewer, List.of(), 0, NutritionTargets.none()));

        assertInstanceOf(AssertionError.class, failure.getCause());
        assertEquals("A displayed day must have a positive number: 0", failure.getCause().getMessage());
    }
    //@@author

    @Test
    void view_exceededTargets_placesWarningsAfterTotalsInNutrientOrder() {
        NutritionTargets targets = new NutritionTargets(1500, 120, 140, 50);
        String warnings = String.join(System.lineSeparator(),
                "Warning: Calories exceed target by 150 kcal (1650 kcal actual; 1500 kcal target).",
                "Warning: Protein exceeds target by 7 g (127 g actual; 120 g target).",
                "Warning: Carbs exceed target by 10 g (150 g actual; 140 g target).",
                "Warning: Fats exceed target by 5 g (55 g actual; 50 g target).");
        String expected = DAY_ONE_DISPLAY.replace("======================================",
                warnings + System.lineSeparator() + "======================================");

        assertEquals(expected, viewer.view(List.of(createMealsByDay().get(0)), targets));
        assertEquals(expected, viewer.view(createMealsByDay(), 1, targets));
    }

    @Test
    void view_multipleDays_checksTotalsIndependentlyAndSelectsOnlyRequestedDay() {
        NutritionTargets targets = new NutritionTargets(1700, null, null, null);
        String warning = "Warning: Calories exceed target by 50 kcal (1750 kcal actual; 1700 kcal target).";
        String expectedDayTwo = DAY_TWO_DISPLAY.replace("======================================",
                warning + System.lineSeparator() + "======================================");

        assertEquals(DAY_ONE_DISPLAY + System.lineSeparator() + System.lineSeparator() + expectedDayTwo,
                viewer.view(createMealsByDay(), targets));
        assertEquals(expectedDayTwo, viewer.view(createMealsByDay(), 2, targets));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(createMealsByDay(), 1, targets));
    }

    @Test
    void view_unsetEqualOrHigherTargets_preservesExistingDisplay() {
        List<List<MealSummary>> meals = List.of(createMealsByDay().get(0));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(meals, NutritionTargets.none()));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(meals, new NutritionTargets(1650, 127, 150, 55)));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(meals, new NutritionTargets(1800, 150, 200, 80)));
    }

    @Test
    void view_largeTotals_formatsExcessWithoutOverflow() {
        MealSummary huge = new MealSummary("Huge", Integer.MAX_VALUE, 0, 0, 0);
        List<List<MealSummary>> meals = List.of(List.of(huge, huge));
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "Meal 1: Huge (2147483647 kcal | P: 0g, C: 0g, F: 0g)",
                "Meal 2: Huge (2147483647 kcal | P: 0g, C: 0g, F: 0g)",
                "--------------------------------------",
                "Daily Totals: 4294967294 kcal | Protein: 0g | Carbs: 0g | Fats: 0g",
                "Warning: Calories exceed target by 2147483647 kcal"
                        + " (4294967294 kcal actual; 2147483647 kcal target).",
                "======================================");
        assertEquals(expected, viewer.view(meals, new NutritionTargets(Integer.MAX_VALUE, null, null, null)));
    }

    @Test
    void view_nullTargets_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> viewer.view(createMealsByDay(), null));
        assertThrows(NullPointerException.class, () -> viewer.view(createMealsByDay(), 1, null));
    }

    //@@author Rixte
    /**
     * Creates two days with different totals and a repeated meal on day 2.
     */
    private List<List<MealSummary>> createMealsByDay() {
        MealSummary oatmeal = new MealSummary("Protein Oatmeal", 450, 35, 60, 8);
        MealSummary chicken = new MealSummary("Chicken and Rice", 650, 50, 70, 15);
        MealSummary salmon = new MealSummary("Salmon Salad", 550, 42, 20, 32);
        return List.of(List.of(oatmeal, chicken, salmon), List.of(chicken, chicken, oatmeal));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_singleDayPlan_matchesSpecifiedOutput() {
        assertEquals(DAY_ONE_DISPLAY, viewer.view(List.of(createMealsByDay().get(0))));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_multipleDays_displaysEachDayWithIndependentTotals() {
        String expected = DAY_ONE_DISPLAY + System.lineSeparator() + System.lineSeparator()
                + DAY_TWO_DISPLAY;

        assertEquals(expected, viewer.view(createMealsByDay()));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_daysWithDifferentMealCounts_restartsNumberingAndCalculatesTotals() {
        List<List<MealSummary>> mealsByDay = List.of(
                List.of(new MealSummary("Rice", 200, 4, 45, 1),
                        new MealSummary("Beans", 300, 20, 40, 5)),
                List.of(new MealSummary("Salmon", 550, 42, 20, 32)));
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "Meal 1: Rice (200 kcal | P: 4g, C: 45g, F: 1g)",
                "Meal 2: Beans (300 kcal | P: 20g, C: 40g, F: 5g)",
                "--------------------------------------",
                "Daily Totals: 500 kcal | Protein: 24g | Carbs: 85g | Fats: 6g",
                "======================================",
                "",
                "================ Day 2 ================",
                "Meal 1: Salmon (550 kcal | P: 42g, C: 20g, F: 32g)",
                "--------------------------------------",
                "Daily Totals: 550 kcal | Protein: 42g | Carbs: 20g | Fats: 32g",
                "======================================");

        assertEquals(expected, viewer.view(mealsByDay));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_firstDay_displaysOnlyFirstDay() {
        assertEquals(DAY_ONE_DISPLAY, viewer.view(createMealsByDay(), 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_lastDay_preservesDayNumberAndCountsRepeatedMeals() {
        assertEquals(DAY_TWO_DISPLAY, viewer.view(createMealsByDay(), 2));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_middleDay_displaysOnlySelectedDay() {
        List<List<MealSummary>> originalMealsByDay = createMealsByDay();
        List<List<MealSummary>> mealsByDay = List.of(originalMealsByDay.get(0), originalMealsByDay.get(1),
                List.of(new MealSummary("Beans", 300, 20, 40, 5)));

        assertEquals(DAY_TWO_DISPLAY, viewer.view(mealsByDay, 2));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_emptyDayBetweenMeals_preservesDayNumbers() {
        List<List<MealSummary>> originalMealsByDay = createMealsByDay();
        List<List<MealSummary>> mealsByDay =
                List.of(originalMealsByDay.get(0), List.of(), originalMealsByDay.get(1));
        String emptyDayDisplay = String.join(System.lineSeparator(),
                "================ Day 2 ================",
                "--------------------------------------",
                "Daily Totals: 0 kcal | Protein: 0g | Carbs: 0g | Fats: 0g",
                "======================================");
        // The former second day's meals now belong to day 3, with the same nutrition totals.
        String thirdDayDisplay =
                "================ Day 3 ================" + System.lineSeparator() + DAY_TWO_BODY;
        String expected = String.join(System.lineSeparator() + System.lineSeparator(),
                DAY_ONE_DISPLAY, emptyDayDisplay, thirdDayDisplay);

        assertEquals(expected, viewer.view(mealsByDay));
        assertEquals(emptyDayDisplay, viewer.view(mealsByDay, 2));
        assertEquals(thirdDayDisplay, viewer.view(mealsByDay, 3));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_differentPlans_displaysOnlyLatestInput() {
        viewer.view(createMealsByDay());
        viewer.view(createMealsByDay(), 2);
        List<List<MealSummary>> replacementMealsByDay =
                List.of(List.of(new MealSummary("Beans", 300, 20, 40, 5)));
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "Meal 1: Beans (300 kcal | P: 20g, C: 40g, F: 5g)",
                "--------------------------------------",
                "Daily Totals: 300 kcal | Protein: 20g | Carbs: 40g | Fats: 5g",
                "======================================");

        assertEquals(expected, viewer.view(replacementMealsByDay));
        assertEquals(expected, viewer.view(replacementMealsByDay, 1));
        assertEquals("No active meal plan. Use 'generate-plan' first.", viewer.view(List.of()));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_dayOutsidePlan_throwsIllegalArgumentException() {
        List<List<MealSummary>> mealsByDay = createMealsByDay();

        assertThrows(IllegalArgumentException.class, () -> viewer.view(mealsByDay, -1));
        assertThrows(IllegalArgumentException.class, () -> viewer.view(mealsByDay, 0));
        assertThrows(IllegalArgumentException.class, () -> viewer.view(mealsByDay, 3));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_emptyPlan_displaysGeneratePlanPrompt() {
        String expected = "No active meal plan. Use 'generate-plan' first.";

        assertEquals(expected, viewer.view(List.of()));
        assertEquals(expected, viewer.view(List.of(), 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_emptyDay_displaysZeroTotals() {
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "--------------------------------------",
                "Daily Totals: 0 kcal | Protein: 0g | Carbs: 0g | Fats: 0g",
                "======================================");
        List<List<MealSummary>> mealsByDay = List.of(List.of());

        assertEquals(expected, viewer.view(mealsByDay));
        assertEquals(expected, viewer.view(mealsByDay, 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_largeNutritionValues_totalsDoNotOverflow() {
        MealSummary meal = new MealSummary("Large meal", Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "Meal 1: Large meal (2147483647 kcal | P: 2147483647g, C: 2147483647g, F: 2147483647g)",
                "Meal 2: Large meal (2147483647 kcal | P: 2147483647g, C: 2147483647g, F: 2147483647g)",
                "--------------------------------------",
                "Daily Totals: 4294967294 kcal | Protein: 4294967294g"
                        + " | Carbs: 4294967294g | Fats: 4294967294g",
                "======================================");

        assertEquals(expected, viewer.view(List.of(List.of(meal, meal))));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_repeatedCalls_doesNotChangePlan() {
        List<List<MealSummary>> originalMealsByDay = createMealsByDay();
        List<List<MealSummary>> mealsByDay = new ArrayList<>();
        for (List<MealSummary> meals : originalMealsByDay) {
            mealsByDay.add(new ArrayList<>(meals));
        }

        String firstDisplay = viewer.view(mealsByDay);
        viewer.view(mealsByDay, 2);

        assertEquals(firstDisplay, viewer.view(mealsByDay));
        assertEquals(originalMealsByDay, mealsByDay);
    }
    //@@author

    //@@author Rixte
    @Test
    void view_nullPlan_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> viewer.view(null));
        assertThrows(NullPointerException.class, () -> viewer.view(null, 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_nullDay_throwsNullPointerException() {
        // Arrays.asList permits null so the viewer's validation is exercised.
        List<List<MealSummary>> mealsByDay = Arrays.asList(createMealsByDay().get(0), null);

        assertThrows(NullPointerException.class, () -> viewer.view(mealsByDay));
        assertThrows(NullPointerException.class, () -> viewer.view(mealsByDay, 2));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_validDayWithAnotherNullDay_displaysOnlySelectedDay() {
        // Viewing one day only needs the selected day's meals to be valid.
        List<List<MealSummary>> mealsByDay = Arrays.asList(createMealsByDay().get(0), null);

        assertEquals(DAY_ONE_DISPLAY, viewer.view(mealsByDay, 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_nullMeal_throwsNullPointerException() {
        List<MealSummary> meals = Arrays.asList(new MealSummary("Rice", 200, 4, 45, 1), null);
        List<List<MealSummary>> mealsByDay = List.of(meals);

        assertThrows(NullPointerException.class, () -> viewer.view(mealsByDay));
        assertThrows(NullPointerException.class, () -> viewer.view(mealsByDay, 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_validPlanAfterRejectedInput_displaysOnlyValidMealsAndTotals() {
        // A meal is formatted before the null entry interrupts the failed call.
        List<List<MealSummary>> invalidMealsByDay = List.of(
                Arrays.asList(new MealSummary("Incomplete meal", 999, 90, 80, 70), null));
        List<List<MealSummary>> validMealsByDay = List.of(createMealsByDay().get(0));

        assertThrows(NullPointerException.class, () -> viewer.view(invalidMealsByDay));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(validMealsByDay));
        assertThrows(NullPointerException.class, () -> viewer.view(invalidMealsByDay, 1));
        assertEquals(DAY_ONE_DISPLAY, viewer.view(validMealsByDay, 1));
    }
    //@@author

    //@@author Rixte
    @Test
    void mealSummary_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new MealSummary(null, 100, 1, 2, 3));
    }
    //@@author

    //@@author Rixte
    @Test
    void mealSummary_blankName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("", 100, 1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new MealSummary(" \t ", 100, 1, 2, 3));
    }
    //@@author

    //@@author Rixte
    @Test
    void mealSummary_negativeNutrition_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", -1, 1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", 100, -1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", 100, 1, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", 100, 1, 2, -1));
    }
    //@@author

    //@@author Rixte
    @Test
    void view_zeroNutrition_displaysZeroValues() {
        MealSummary meal = new MealSummary("Water", 0, 0, 0, 0);
        String expected = String.join(System.lineSeparator(),
                "================ Day 1 ================",
                "Meal 1: Water (0 kcal | P: 0g, C: 0g, F: 0g)",
                "--------------------------------------",
                "Daily Totals: 0 kcal | Protein: 0g | Carbs: 0g | Fats: 0g",
                "======================================");

        assertEquals(expected, viewer.view(List.of(List.of(meal))));
    }
    //@@author

    @Test
    void view_adjustedPortion_displaysCountAndScaledLongNutrition() {
        MealSummary meal = new MealSummary("Rice", 3, 600L, 12L, 135L, 3L);

        String display = viewer.view(List.of(List.of(meal)));

        assertTrue(display.contains("Meal 1: Rice [3 portions] (600 kcal | P: 12g, C: 135g, F: 3g)"));
        assertTrue(display.contains("Daily Totals: 600 kcal | Protein: 12g | Carbs: 135g | Fats: 3g"));
    }

    @Test
    void view_customizedIngredients_displaysMarkerWithoutChangingNutrition() {
        MealSummary meal = new MealSummary("Rice", 2, true, 400, 8, 90, 2);

        String display = viewer.view(List.of(List.of(meal)));

        assertTrue(display.contains("Meal 1: Rice [2 portions] [custom ingredients]"
                + " (400 kcal | P: 8g, C: 90g, F: 2g)"));
    }

    @Test
    void mealSummary_nonPositivePortions_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", 0, 200, 4, 45, 1));
        assertThrows(IllegalArgumentException.class, () -> new MealSummary("Rice", -1, 200, 4, 45, 1));
    }

    @Test
    void view_totalsBeyondLongRange_throwsInsteadOfWrappingNegative() {
        MealSummary huge = new MealSummary("Huge", Long.MAX_VALUE, 0, 0, 0);
        MealSummary extra = new MealSummary("Extra", 1, 0, 0, 0);

        assertThrows(ArithmeticException.class, () -> viewer.view(List.of(List.of(huge, extra))));
    }
}
