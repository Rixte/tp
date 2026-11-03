package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.testutil.TestRecipes;

/**
 * Checks target validation and the ±15% tolerance rule, including the request that carries the targets.
 */
class NutritionTargetsTest {
    private static final DayPlan DAY = new DayPlan(List.of(TestRecipes.recipe("Meal", 1000, 100, 100, 100)));

    @Test
    void calculateExcesses_belowEqualAndAboveTargets_comparesEachNutrientExactly() {
        NutritionTargets targets = new NutritionTargets(100, 100, 100, 100);
        assertEquals(new NutritionTotals(0, 0, 0, 0),
                targets.calculateExcesses(new NutritionTotals(99, 99, 99, 99)));
        assertEquals(new NutritionTotals(0, 0, 0, 0),
                targets.calculateExcesses(new NutritionTotals(100, 100, 100, 100)));
        assertEquals(new NutritionTotals(1, 2, 3, 4),
                targets.calculateExcesses(new NutritionTotals(101, 102, 103, 104)));
    }

    @Test
    void calculateExcesses_partialAndUnsetTargets_ignoresOmittedNutrients() {
        NutritionTotals totals = new NutritionTotals(110, 120, 130, 140);
        assertEquals(new NutritionTotals(0, 20, 0, 40),
                new NutritionTargets(null, 100, null, 100).calculateExcesses(totals));
        assertEquals(new NutritionTotals(0, 0, 0, 0), NutritionTargets.none().calculateExcesses(totals));
    }

    @Test
    void calculateExcesses_largeTotals_preservesLongValues() {
        long total = 3L * Integer.MAX_VALUE;
        long excess = total - Integer.MAX_VALUE;
        NutritionTargets targets = new NutritionTargets(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(new NutritionTotals(excess, excess, excess, excess),
                targets.calculateExcesses(new NutritionTotals(total, total, total, total)));
    }

    @Test
    void calculateExcesses_nullTotals_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> NutritionTargets.none().calculateExcesses(null));
    }

    @Test
    void none_hasNoTargets() {
        assertFalse(NutritionTargets.none().hasAnyTarget());
    }

    @Test
    void constructor_nonPositiveTarget_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new NutritionTargets(0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new NutritionTargets(null, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new NutritionTargets(null, null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new NutritionTargets(null, null, null, -5));
    }

    @Test
    void isSatisfiedBy_withinTolerance_isAccepted() {
        assertTrue(new NutritionTargets(1150, null, null, null).isSatisfiedBy(DAY));
        assertTrue(new NutritionTargets(870, null, null, null).isSatisfiedBy(DAY));
    }

    @Test
    void isSatisfiedBy_justOutsideTolerance_isRejected() {
        assertFalse(new NutritionTargets(1200, null, null, null).isSatisfiedBy(DAY));
    }

    @Test
    void isSatisfiedBy_oneOfSeveralTargetsMissed_isRejected() {
        assertFalse(new NutritionTargets(1000, 100, 100, 200).isSatisfiedBy(DAY));
    }

    @Test
    void isSatisfiedBy_nullDay_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> NutritionTargets.none().isSatisfiedBy((DayPlan) null));
        assertThrows(NullPointerException.class, () -> NutritionTargets.none().isSatisfiedBy((NutritionTotals) null));
    }

    @Test
    void request_validValues_exposesValues() {
        GeneratePlanRequest request = new GeneratePlanRequest(3, NutritionTargets.none());

        assertEquals(3, request.days());
        assertFalse(request.targets().hasAnyTarget());
    }

    @Test
    void isSatisfiedBy_totalsBeyondIntRange_doNotWrapAroundToMatchTarget() {
        // Three of these sum to 4,294,967,298, which would wrap to exactly 2 if added as ints.
        Recipe large = TestRecipes.recipe("Large", 1_431_655_766, 0, 0, 0);
        DayPlan day = new DayPlan(List.of(large, large, large));

        assertFalse(new NutritionTargets(2, null, null, null).isSatisfiedBy(day));
    }

    @Test
    void request_daysOutsideRange_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratePlanRequest(0, NutritionTargets.none()));
        assertThrows(IllegalArgumentException.class, () -> new GeneratePlanRequest(8, NutritionTargets.none()));
    }

    @Test
    void request_nullTargets_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new GeneratePlanRequest(3, null));
    }

    //@@author GauthamNandi
    @Test
    void isWithinTolerance_exactlyFifteenPercentAway_isTheLimit() {
        assertTrue(NutritionTargets.isWithinTolerance(2000, 2300));
        assertTrue(NutritionTargets.isWithinTolerance(2000, 1700));
        assertFalse(NutritionTargets.isWithinTolerance(2000, 2301));
        assertFalse(NutritionTargets.isWithinTolerance(2000, 1699));
    }

    @Test
    void isSatisfiedBy_totalsAndDay_agree() {
        NutritionTargets targets = new NutritionTargets(1000, 100, null, null);

        assertTrue(targets.isSatisfiedBy(DAY.calculateDailyNutritionTotals()));
        assertTrue(targets.isSatisfiedBy(DAY));
        assertFalse(targets.isSatisfiedBy(new NutritionTotals(1000, 200, 0, 0)));
    }
    //@@author
}
