package mishmash.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.model.DayPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.FoodInfo;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;

/**
 * Tests dietary selection and partial repair independently of command parsing and display.
 */
class DietaryPlannerTest {
    private final DietaryPlanner planner = new DietaryPlanner(new MealPlanGenerator(new Random(42)));
    private final DietaryPreferences vegan = new DietaryPreferences(Diet.VEGAN, Set.of(), Set.of());

    /**
     * Creates a recipe with controlled nutrition and food metadata for planner tests.
     */
    private Recipe createRecipe(String name, int calories, String tags) {
        return new Recipe(name, calories, 10, 20, 5,
                List.of(new GroceryItem(name, BigDecimal.ONE, "g")), FoodInfo.parseField(tags));
    }

    /**
     * Creates three compatible recipes whose combined daily calories total 1800.
     */
    private List<Recipe> createSuitableRecipes() {
        return List.of(createRecipe("Rice", 600, "none"), createRecipe("Beans", 600, "legume"),
                createRecipe("Tofu", 600, "soy,legume"));
    }

    @Test
    void generate_restrictedPlan_meetsDietTargetsAndDailyVariety() {
        List<Recipe> suitableRecipes = createSuitableRecipes();
        Recipe milk = createRecipe("Milk", 600, "milk,lactose");
        List<Recipe> recipes = List.of(suitableRecipes.get(0), suitableRecipes.get(1), suitableRecipes.get(2), milk,
                createRecipe("Unknown", 600, null));
        NutritionTargets targets = new NutritionTargets(1800, 30, 60, 15);
        MealPlan plan = planner.generate(recipes, new GeneratePlanRequest(7, targets), vegan);

        assertEquals(7, plan.getNumberOfDays());
        for (DayPlan day : plan.getDays()) {
            assertEquals(3, day.getMeals().size());
            assertEquals(3, Set.copyOf(day.getMeals()).size());
            assertTrue(targets.isSatisfiedBy(day));
            assertTrue(day.getMeals().stream().allMatch(suitableRecipes::contains));
        }
        assertEquals(5, recipes.size());
    }

    @Test
    void repair_conflictingDays_preservesCompatibleDaysAndOriginalPositions() {
        List<Recipe> recipes = createSuitableRecipes();
        DayPlan unchanged = new DayPlan(recipes);
        DayPlan conflicting = new DayPlan(List.of(recipes.get(0), recipes.get(1), createRecipe("Egg", 600, "egg")));
        MealPlan current = new MealPlan(List.of(unchanged, conflicting, unchanged, conflicting));
        NutritionTargets targets = new NutritionTargets(1800, 30, 60, 15);

        MealPlan result = planner.repair(recipes, current, new GeneratePlanRequest(4, targets), vegan);

        assertSame(unchanged, result.getDays().get(0));
        assertSame(unchanged, result.getDays().get(2));
        assertFalse(result.getDays().get(1).getMeals().contains(conflicting.getMeals().get(2)));
        assertEquals(4, result.getNumberOfDays());
        assertTrue(result.getDays().stream().allMatch(targets::isSatisfiedBy));
        assertTrue(planner.isCompatible(result, vegan));
        assertSame(conflicting, current.getDays().get(1));
    }

    @Test
    void repair_compatiblePlan_returnsSamePlanWithoutNeedingRecipePool() {
        MealPlan current = new MealPlan(List.of(new DayPlan(createSuitableRecipes())));
        assertSame(current, planner.repair(List.of(), current,
                new GeneratePlanRequest(1, NutritionTargets.none()), vegan));
    }

    @Test
    void generate_insufficientCompatibleRecipes_reportsCount() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> planner.generate(createSuitableRecipes().subList(0, 2),
                        new GeneratePlanRequest(1, NutritionTargets.none()), vegan));
        assertTrue(error.getMessage().contains("found 2"));
        assertTrue(error.getMessage().contains("classified"));
    }

    @Test
    void generate_unreachableNutritionTargets_reportsFailure() {
        assertThrows(IllegalArgumentException.class, () -> planner.generate(createSuitableRecipes(),
                new GeneratePlanRequest(1, new NutritionTargets(5000, null, null, null)), vegan));
    }

    @Test
    void repair_inconsistentRequest_rejectsBeforeGenerating() {
        MealPlan current = new MealPlan(List.of(new DayPlan(createSuitableRecipes())));
        assertThrows(IllegalArgumentException.class, () -> planner.repair(createSuitableRecipes(), current,
                new GeneratePlanRequest(2, NutritionTargets.none()), vegan));
    }

    @Test
    void generate_nullInputs_rejectsProgrammingErrors() {
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        assertThrows(NullPointerException.class, () -> new DietaryPlanner(null));
        assertThrows(NullPointerException.class, () -> planner.generate(null, request, vegan));
        assertThrows(NullPointerException.class, () -> planner.generate(createSuitableRecipes(), null, vegan));
        assertThrows(NullPointerException.class, () -> planner.generate(createSuitableRecipes(), request, null));
        assertThrows(NullPointerException.class, () -> planner.generate(Arrays.asList((Recipe) null), request, vegan));
        assertThrows(NullPointerException.class, () -> planner.isCompatible(null, vegan));
        assertThrows(NullPointerException.class,
                () -> planner.isCompatible(new MealPlan(List.of()), null));
        assertThrows(NullPointerException.class, () -> planner.repair(null, null, null, null));
    }

    @Test
    void generate_brokenGenerator_assertsDietaryInvariant() {
        MealPlanGenerator broken = new MealPlanGenerator(new Random(1)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                return new MealPlan(List.of(new DayPlan(List.of(createRecipe("Egg", 600, "egg")))));
            }
        };
        assertThrows(AssertionError.class, () -> new DietaryPlanner(broken).generate(createSuitableRecipes(),
                new GeneratePlanRequest(1, NutritionTargets.none()), vegan));
    }

    @Test
    void generate_unrestrictedProfile_keepsUnclassifiedRecipes() {
        List<Recipe> recipes = List.of(createRecipe("Unknown", 600, null),
                createRecipe("Milk", 600, "milk"), createRecipe("Egg", 600, "egg"));
        MealPlan plan = planner.generate(recipes, new GeneratePlanRequest(1, NutritionTargets.none()),
                DietaryPreferences.createUnrestricted());
        assertEquals(Set.copyOf(recipes), Set.copyOf(plan.getDays().get(0).getMeals()));
    }

    @Test
    void repair_nullArguments_validateEachDependencyIndependently() {
        List<Recipe> recipes = createSuitableRecipes();
        MealPlan current = new MealPlan(List.of(new DayPlan(recipes)));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        assertThrows(NullPointerException.class, () -> planner.repair(null, current, request, vegan));
        assertThrows(NullPointerException.class, () -> planner.repair(recipes, null, request, vegan));
        assertThrows(NullPointerException.class, () -> planner.repair(recipes, current, null, vegan));
        assertThrows(NullPointerException.class, () -> planner.repair(recipes, current, request, null));
    }

    @Test
    void repair_failedReplacement_preservesOriginalPlan() {
        List<Recipe> recipes = createSuitableRecipes();
        DayPlan conflicting = new DayPlan(List.of(createRecipe("Egg", 600, "egg")));
        MealPlan current = new MealPlan(List.of(conflicting));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        assertThrows(IllegalArgumentException.class,
                () -> planner.repair(recipes.subList(0, 2), current, request, vegan));
        assertSame(conflicting, current.getDays().get(0));
        assertEquals("Egg", current.getDays().get(0).getMeals().get(0).getName());
    }

    @Test
    void isCompatible_customizedIngredients_checksEffectiveMealContents() {
        DayPlan original = new DayPlan(createSuitableRecipes());
        DayPlan customized = original.withCustomizedIngredients(0,
                List.of(new GroceryItem("Salt", BigDecimal.ONE, "g")));
        DietaryPreferences noSalt = new DietaryPreferences(Diet.NONE, Set.of(), Set.of("salt"));
        assertTrue(planner.isCompatible(new MealPlan(List.of(original)), noSalt));
        assertFalse(planner.isCompatible(new MealPlan(List.of(customized)), noSalt));
    }

    @Test
    void generate_wrongLength_assertsRequestInvariant() {
        MealPlan wrongLength = new MealPlan(List.of(new DayPlan(createSuitableRecipes())));
        AssertionError error = assertThrows(AssertionError.class,
                () -> plannerReturning(wrongLength).generate(createSuitableRecipes(),
                        new GeneratePlanRequest(2, NutritionTargets.none()), vegan));
        assertEquals("Generated plan length must match the request", error.getMessage());
    }

    @Test
    void generate_insufficientNutrition_assertsTargetInvariant() {
        MealPlan underTarget = new MealPlan(List.of(new DayPlan(createSuitableRecipes())));
        AssertionError error = assertThrows(AssertionError.class,
                () -> plannerReturning(underTarget).generate(createSuitableRecipes(),
                        new GeneratePlanRequest(1, new NutritionTargets(5000, null, null, null)), vegan));
        assertEquals("Generated days must satisfy the original nutrition targets", error.getMessage());
    }

    /**
     * Supplies a deliberately invalid generator result to exercise internal assertions.
     */
    private DietaryPlanner plannerReturning(MealPlan result) {
        return new DietaryPlanner(new MealPlanGenerator(new Random(1)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                return result;
            }
        });
    }

    @Test
    void repair_allDaysConflict_replacesEveryDayWithoutChangingOriginal() {
        List<Recipe> recipes = createSuitableRecipes();
        DayPlan conflicting = new DayPlan(List.of(createRecipe("Milk", 600, "milk")));
        MealPlan original = new MealPlan(List.of(conflicting, conflicting));
        NutritionTargets targets = new NutritionTargets(1800, 30, 60, 15);

        MealPlan repaired = planner.repair(recipes, original, new GeneratePlanRequest(2, targets), vegan);

        assertEquals(2, repaired.getNumberOfDays());
        assertTrue(planner.isCompatible(repaired, vegan));
        assertTrue(repaired.getDays().stream().allMatch(targets::isSatisfiedBy));
        for (DayPlan day : repaired.getDays()) {
            assertEquals(3, day.getMeals().size());
            assertEquals(Set.copyOf(recipes), Set.copyOf(day.getMeals()));
        }
        assertSame(conflicting, original.getDays().get(0));
        assertSame(conflicting, original.getDays().get(1));
    }

    @Test
    void repair_compatibleCustomizedDay_preservesPortionsAndIngredients() {
        DayPlan customized = new DayPlan(createSuitableRecipes()).withAdjustedPortion(0, 2)
                .withCustomizedIngredients(0, List.of(new GroceryItem("Pepper", BigDecimal.ONE, "g")));
        DayPlan conflicting = new DayPlan(List.of(createRecipe("Egg", 600, "egg")));
        MealPlan original = new MealPlan(List.of(conflicting, customized));
        MealPlan repaired = planner.repair(createSuitableRecipes(), original,
                new GeneratePlanRequest(2, NutritionTargets.none()), vegan);

        assertSame(customized, repaired.getDays().get(1));
        assertEquals(2400, repaired.getDays().get(1).getTotalCalories());
        assertEquals("Pepper", repaired.getDays().get(1).getPlannedMeals().get(0).getIngredients().get(0).getName());
        assertTrue(planner.isCompatible(repaired, vegan));
    }

    @Test
    void generate_noCompatibleRecipes_reportsZeroWithoutChangingPool() {
        List<Recipe> recipes = List.of(createRecipe("Milk", 600, "milk"),
                createRecipe("Unknown", 600, null));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> planner.generate(recipes, new GeneratePlanRequest(1, NutritionTargets.none()), vegan));
        assertTrue(error.getMessage().contains("found 0"));
        assertEquals(List.of("Milk", "Unknown"), recipes.stream().map(Recipe::getName).toList());
    }

    @Test
    void repair_ingredientExclusion_replacesCustomizedConflict() {
        List<Recipe> recipes = createSuitableRecipes();
        DayPlan customized = new DayPlan(recipes).withCustomizedIngredients(0,
                List.of(new GroceryItem("Salt", BigDecimal.ONE, "g")));
        MealPlan original = new MealPlan(List.of(customized));
        DietaryPreferences noSalt = new DietaryPreferences(Diet.NONE, Set.of(), Set.of("salt"));

        MealPlan repaired = planner.repair(recipes, original,
                new GeneratePlanRequest(1, NutritionTargets.none()), noSalt);

        assertTrue(planner.isCompatible(repaired, noSalt));
        assertFalse(planner.isCompatible(original, noSalt));
        assertSame(customized, original.getDays().get(0));
    }
}
