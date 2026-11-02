package mishmash.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.testutil.TestRecipes;

/**
 * Checks plan shape, repeatability with a seeded random source, target matching, and invalid input handling.
 */
class MealPlanGeneratorTest {
    private static final List<Recipe> FIVE_RECIPES = List.of(
            TestRecipes.recipe("Oatmeal", 500, 20, 80, 10),
            TestRecipes.recipe("Chicken Rice", 600, 40, 70, 15),
            TestRecipes.recipe("Salmon Bowl", 700, 45, 60, 25),
            TestRecipes.recipe("Apple", 100, 1, 25, 0),
            TestRecipes.recipe("Pizza", 1500, 50, 150, 70));

    /**
     * Creates a generator whose meal selections can be reproduced from a known seed.
     */
    private static MealPlanGenerator createSeededGenerator(long seed) {
        return new MealPlanGenerator(new Random(seed));
    }

    /**
     * Returns meal names in day and meal order for comparing seeded generation results.
     */
    private static List<String> getMealNames(MealPlan plan) {
        List<String> names = new ArrayList<>();
        for (DayPlan day : plan.getDays()) {
            day.getMeals().forEach(meal -> names.add(meal.getName()));
        }
        return names;
    }

    @Test
    void generate_emptyRecipeBook_throwsIllegalArgumentException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> createSeededGenerator(1).generate(List.of(), 3, NutritionTargets.none()));
        assertTrue(e.getMessage().contains("recipe book is empty"));
    }

    @Test
    void generate_fewerThanThreeRecipes_throwsIllegalArgumentException() {
        List<Recipe> two = FIVE_RECIPES.subList(0, 2);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> createSeededGenerator(1).generate(two, 3, NutritionTargets.none()));
        assertTrue(e.getMessage().contains("at least 3 recipes"));
    }

    @Test
    void generate_daysOutsideSupportedRange_throwsIllegalArgumentException() {
        for (int days : new int[] {-1, 0, 8}) {
            assertThrows(IllegalArgumentException.class,
                    () -> createSeededGenerator(1).generate(FIVE_RECIPES, days, NutritionTargets.none()));
        }
    }

    @Test
    void generate_nullArguments_throwNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> createSeededGenerator(1).generate(null, 3, NutritionTargets.none()));
        assertThrows(NullPointerException.class, () -> createSeededGenerator(1).generate(FIVE_RECIPES, 3, null));
        assertThrows(NullPointerException.class, () -> new MealPlanGenerator(null));
    }

    @Test
    void generate_noTargets_hasRequestedShape() {
        for (int days = 1; days <= 7; days++) {
            MealPlan plan = createSeededGenerator(days).generate(FIVE_RECIPES, days, NutritionTargets.none());

            assertEquals(days, plan.getNumberOfDays());
            for (DayPlan day : plan.getDays()) {
                assertEquals(3, day.getMeals().size());
                assertEquals(3, new HashSet<>(day.getMeals()).size(), "meals within a day must differ");
                assertTrue(FIVE_RECIPES.containsAll(day.getMeals()));
            }
        }
    }

    @Test
    void generate_exactlyThreeRecipes_usesAllEachDay() {
        List<Recipe> three = FIVE_RECIPES.subList(0, 3);
        MealPlan plan = createSeededGenerator(1).generate(three, 4, NutritionTargets.none());
        for (DayPlan day : plan.getDays()) {
            assertEquals(new HashSet<>(three), new HashSet<>(day.getMeals()));
        }
    }

    @Test
    void generate_sameSeed_samePlan() {
        MealPlan first = createSeededGenerator(42).generate(FIVE_RECIPES, 7, NutritionTargets.none());
        MealPlan second = createSeededGenerator(42).generate(FIVE_RECIPES, 7, NutritionTargets.none());
        assertEquals(getMealNames(first), getMealNames(second));
    }

    @Test
    void generate_calorieTarget_picksTheOnlyValidCombination() {
        // Only Oatmeal + Chicken Rice + Salmon Bowl (1800 kcal) is within 15% of 1800.
        NutritionTargets targets = new NutritionTargets(1800, null, null, null);
        Set<Recipe> expected = Set.of(FIVE_RECIPES.get(0), FIVE_RECIPES.get(1), FIVE_RECIPES.get(2));

        for (long seed = 0; seed < 10; seed++) {
            MealPlan plan = createSeededGenerator(seed).generate(FIVE_RECIPES, 5, targets);
            for (DayPlan day : plan.getDays()) {
                assertEquals(expected, new HashSet<>(day.getMeals()));
            }
        }
    }

    @Test
    void generate_calorieAndMacroTargets_everyDayWithinTolerance() {
        NutritionTargets targets = new NutritionTargets(1800, 100, 200, 50);
        for (long seed = 0; seed < 10; seed++) {
            MealPlan plan = createSeededGenerator(seed).generate(FIVE_RECIPES, 7, targets);
            plan.getDays().forEach(day -> assertWithinTolerance(targets, day));
        }
    }

    @Test
    void generate_largeRecipeBook_samplesAndStaysWithinTolerance() {
        // 200 recipes give 1,313,400 combinations, more than MAX_CANDIDATES, so the sampling path is used.
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            recipes.add(TestRecipes.recipe("Recipe " + i, 200 + i * 10, i, i * 5, i * 2));
        }
        NutritionTargets targets = new NutritionTargets(2000, 120, null, null);

        MealPlan plan = createSeededGenerator(7).generate(recipes, 7, targets);
        assertEquals(7, plan.getNumberOfDays());
        for (DayPlan day : plan.getDays()) {
            assertEquals(3, new HashSet<>(day.getMeals()).size());
            assertWithinTolerance(targets, day);
        }
    }

    @Test
    void generate_unreachableTarget_throwsIllegalArgumentException() {
        NutritionTargets targets = new NutritionTargets(10000, null, null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> createSeededGenerator(1).generate(FIVE_RECIPES, 3, targets));
        assertTrue(e.getMessage().contains("within 15%"));
    }

    //@@author GauthamNandi
    @Test
    void generate_oneValidCombinationAmong180Recipes_isAlwaysFound() {
        // With 5,000 random checks, this book reported a false failure almost every time.
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            recipes.add(TestRecipes.recipe("Recipe " + i, i < 3 ? 1000 : 1, 0, 0, 0));
        }
        NutritionTargets targets = new NutritionTargets(3000, null, null, null);
        Set<Recipe> expected = Set.copyOf(recipes.subList(0, 3));

        for (long seed = 0; seed < 5; seed++) {
            MealPlan plan = createSeededGenerator(seed).generate(recipes, 1, targets);
            assertEquals(expected, new HashSet<>(plan.getDays().getFirst().getMeals()));
        }
    }

    @Test
    void generate_moreMatchesThanAreKept_stillBuildsAValidPlan() {
        // All 161,700 combinations of these identical recipes match, which is more than MAX_MATCHES.
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            recipes.add(TestRecipes.recipe("Recipe " + i, 500, 10, 10, 10));
        }

        MealPlan plan = createSeededGenerator(3).generate(recipes, 7, new NutritionTargets(1500, null, null, null));

        assertEquals(7, plan.getNumberOfDays());
        assertEquals(7, new HashSet<>(plan.getDays().stream().map(DayPlanNames::of).toList()).size());
    }

    @Test
    void generate_moreValidCombinationsThanDays_neverRepeatsADay() {
        // Five equal recipes give ten valid days, so a seven-day plan does not need to repeat one.
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            recipes.add(TestRecipes.recipe("Recipe " + i, 500, 10, 10, 10));
        }
        NutritionTargets targets = new NutritionTargets(1500, null, null, null);

        for (long seed = 0; seed < 50; seed++) {
            MealPlan plan = createSeededGenerator(seed).generate(recipes, 7, targets);
            assertEquals(7, new HashSet<>(plan.getDays().stream().map(DayPlanNames::of).toList()).size());
        }
    }

    @Test
    void checksEveryCombination_followsTheCandidateLimit() {
        assertTrue(MealPlanGenerator.checksEveryCombination(182));
        assertTrue(!MealPlanGenerator.checksEveryCombination(183));
    }

    @Test
    void generationFailures_areDistinguishableFromOtherInvalidArguments() {
        NutritionTargets targets = new NutritionTargets(10000, null, null, null);

        assertThrows(PlanGenerationException.class, () -> createSeededGenerator(1).generate(FIVE_RECIPES, 3, targets));
        assertThrows(PlanGenerationException.class,
                () -> createSeededGenerator(1).generate(List.of(), 3, NutritionTargets.none()));
        assertThrows(NullPointerException.class, () -> new PlanGenerationException(null));
    }

    /** Identifies a day by its recipes regardless of their order. */
    private static final class DayPlanNames {
        private DayPlanNames() {
        }

        private static Set<String> of(DayPlan day) {
            Set<String> names = new HashSet<>();
            day.getMeals().forEach(meal -> names.add(meal.getName()));
            return names;
        }
    }
    //@@author

    /**
     * Checks every supplied daily target against the generator's permitted tolerance.
     */
    private static void assertWithinTolerance(NutritionTargets targets, DayPlan day) {
        assertWithin(targets.getCalories(), day.getTotalCalories());
        assertWithin(targets.getProtein(), day.getTotalProtein());
        assertWithin(targets.getCarbs(), day.getTotalCarbs());
        assertWithin(targets.getFats(), day.getTotalFats());
    }

    /**
     * Checks a single supplied target while leaving omitted nutrients unrestricted.
     */
    private static void assertWithin(Integer target, long actual) {
        if (target != null) {
            assertTrue(Math.abs(actual - target) <= target * NutritionTargets.TOLERANCE,
                    "expected " + actual + " to be within 15% of " + target);
        }
    }
}
