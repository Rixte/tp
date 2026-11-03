package mishmash.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.command.GeneratePlanCommand;
import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.FoodInfo;
import mishmash.model.FoodTag;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.GeneratePlanParser;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks that a failed generation is explained with its real cause and a useful next step.
 */
class PlanFailureAdvisorTest {
    private static final NutritionTargets NO_TARGETS = NutritionTargets.none();
    private static final DietaryPreferences NO_DIET = DietaryPreferences.createUnrestricted();
    private static final FoodTag AVOIDED_TAG = FoodTag.values()[0];
    private static final DietaryPreferences RESTRICTED =
            new DietaryPreferences(Diet.NONE, Set.of(AVOIDED_TAG), Set.of());

    private final PlanFailureAdvisor advisor = new PlanFailureAdvisor();

    private static Recipe meal(String name, int calories, int protein) {
        return TestRecipes.recipe(name, calories, protein, 10, 10);
    }

    private static Recipe taggedMeal(String name, FoodInfo foodInfo) {
        return new Recipe(name, 500, 30, 50, 10, List.of(new GroceryItem("Rice", BigDecimal.TEN, "g")), foodInfo);
    }

    private static List<Recipe> threeMeals() {
        return List.of(meal("A", 400, 20), meal("B", 500, 25), meal("C", 600, 30));
    }

    @Test
    void explain_tooFewRecipes_saysHowManyMoreAreNeeded() {
        List<String> advice = advisor.explain(List.of(meal("A", 400, 20)), NO_TARGETS, NO_DIET);

        assertEquals(List.of("What to try next:", "- Add 2 more suitable recipe(s) with 'add-recipe'."), advice);
    }

    @Test
    void explain_dietLeavesTooFewRecipes_namesTheFilterAndEveryWayOut() {
        List<Recipe> recipes = List.of(
                taggedMeal("Salad", new FoodInfo(true, Set.of())),
                taggedMeal("Steak", new FoodInfo(true, Set.of(AVOIDED_TAG))),
                meal("Unclassified", 500, 30));

        String advice = String.join("\n", advisor.explain(recipes, NO_TARGETS,
                RESTRICTED));

        assertTrue(advice.contains("allow 1 of your 3 recipes (1 excluded for having no food tags)"), advice);
        assertTrue(advice.contains("Add 2 more suitable recipe(s)"), advice);
        assertTrue(advice.contains("edit-recipe INDEX food/TAG"), advice);
        assertTrue(advice.contains("'set-diet' or 'clear-diet'"), advice);
    }

    @Test
    void explain_targetTooHigh_comparesItWithTheHighestRecipes() {
        NutritionTargets targets = new NutritionTargets(5000, null, null, null);

        String advice = String.join("\n", advisor.explain(threeMeals(), targets, NO_DIET));

        assertTrue(advice.contains("Calories: your target is 5000 kcal, but your 3 highest recipes total only "
                + "1500 kcal."), advice);
        assertTrue(advice.contains("Lower cal/ or add recipes higher in Calories."), advice);
    }

    @Test
    void explain_targetTooLow_comparesItWithTheLowestRecipes() {
        NutritionTargets targets = new NutritionTargets(null, 20, null, null);

        String advice = String.join("\n", advisor.explain(threeMeals(), targets, NO_DIET));

        assertTrue(advice.contains("Protein: your target is 20 g, but your 3 lowest recipes total 75 g."), advice);
        assertTrue(advice.contains("Raise p/ or add recipes lower in Protein."), advice);
    }

    @Test
    void explain_severalUnreachableTargets_reportsEachOne() {
        NutritionTargets targets = new NutritionTargets(5000, 20, null, null);

        String advice = String.join("\n", advisor.explain(threeMeals(), targets, NO_DIET));

        assertTrue(advice.contains("Calories:"), advice);
        assertTrue(advice.contains("Protein:"), advice);
    }

    @Test
    void explain_targetsConflictButEachIsReachable_suggestsRelaxingOne() {
        // Calories alone and protein alone are reachable, but the high-calorie meals have the low protein.
        List<Recipe> recipes = List.of(meal("A", 100, 90), meal("B", 100, 90), meal("C", 100, 90),
                meal("D", 900, 10), meal("E", 900, 10), meal("F", 900, 10));
        NutritionTargets targets = new NutritionTargets(2700, 270, null, null);

        String advice = String.join("\n", advisor.explain(recipes, targets, NO_DIET));

        assertTrue(advice.contains("no 3 recipes were found that meet all of your targets"), advice);
        assertTrue(advice.contains("Relax or remove one target"), advice);
        assertFalse(advice.contains("Try again"), advice);
    }

    @Test
    void explain_targetBetweenTheExtremesButUnreachable_doesNotClaimItCanBeReached() {
        // Day totals can only be 300, 1200, 2100 or 3000, and none is within 15% of 1500.
        List<Recipe> recipes = List.of(meal("A", 100, 10), meal("B", 100, 10), meal("C", 100, 10),
                meal("D", 1000, 10), meal("E", 1000, 10), meal("F", 1000, 10));
        NutritionTargets targets = new NutritionTargets(1500, null, null, null);

        String advice = String.join("\n", advisor.explain(recipes, targets, NO_DIET));

        assertTrue(advice.contains("within the range your recipes can reach"), advice);
        assertFalse(advice.contains("can be reached"), advice);
    }

    @Test
    void explain_largeBook_mentionsThatOnlyASampleWasChecked() {
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            recipes.add(meal("Recipe " + i, i < 100 ? 100 : 900, i < 100 ? 90 : 10));
        }
        NutritionTargets targets = new NutritionTargets(2700, 270, null, null);

        String advice = String.join("\n", advisor.explain(recipes, targets, NO_DIET));

        assertTrue(advice.contains("Try again"), advice);
    }

    @Test
    void explain_restrictedDietWithUnreachableTarget_alsoSuggestsWideningTheDiet() {
        List<Recipe> recipes = List.of(taggedMeal("A", new FoodInfo(true, Set.of())),
                taggedMeal("B", new FoodInfo(true, Set.of())), taggedMeal("C", new FoodInfo(true, Set.of())),
                taggedMeal("D", new FoodInfo(true, Set.of(AVOIDED_TAG))));

        String advice = String.join("\n",
                advisor.explain(recipes, new NutritionTargets(9000, null, null, null), RESTRICTED));

        assertTrue(advice.contains("allow 3 of your 4 recipes."), advice);
        assertTrue(advice.contains("Calories:"), advice);
        assertTrue(advice.contains("to allow more recipes"), advice);
    }

    @Test
    void explain_dietExcludedNothing_givesNoDietAdvice() {
        List<Recipe> recipes = List.of(taggedMeal("A", new FoodInfo(true, Set.of())),
                taggedMeal("B", new FoodInfo(true, Set.of())), taggedMeal("C", new FoodInfo(true, Set.of())));

        String advice = String.join("\n",
                advisor.explain(recipes, new NutritionTargets(9000, null, null, null), RESTRICTED));

        assertTrue(advice.contains("Calories:"), advice);
        assertFalse(advice.contains("dietary preferences"), advice);
        assertFalse(advice.contains("clear-diet"), advice);
    }

    @Test
    void explain_tooFewRecipesWithNoUnclassifiedOnes_doesNotSuggestAddingTags() {
        List<Recipe> recipes = List.of(taggedMeal("Salad", new FoodInfo(true, Set.of())),
                taggedMeal("Steak", new FoodInfo(true, Set.of(AVOIDED_TAG))));

        String advice = String.join("\n", advisor.explain(recipes, NO_TARGETS, RESTRICTED));

        assertTrue(advice.contains("clear-diet"), advice);
        assertFalse(advice.contains("food/TAG"), advice);
    }

    @Test
    void explain_nothingToAdd_returnsNoLines() {
        assertEquals(List.of(), advisor.explain(threeMeals(), NO_TARGETS, NO_DIET));
    }

    @Test
    void explain_nullArguments_throwNullPointerException() {
        assertThrows(NullPointerException.class, () -> advisor.explain(null, NO_TARGETS, NO_DIET));
        assertThrows(NullPointerException.class, () -> advisor.explain(List.of(), null, NO_DIET));
        assertThrows(NullPointerException.class, () -> advisor.explain(List.of(), NO_TARGETS, null));
    }

    @Test
    void generatePlan_failureWithNoAdvice_showsOnlyTheOriginalMessage() {
        RecipeBook book = new RecipeBook();
        threeMeals().forEach(book::add);
        MealPlanGenerator failing = new MealPlanGenerator(new Random(42)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                throw new PlanGenerationException("Generation failed.");
            }
        };
        GeneratePlanCommand command = new GeneratePlanCommand(new GeneratePlanRequest(1, NO_TARGETS), failing,
                new ActiveMealPlan(), new DietarySettings());

        MishMashException error = assertThrows(MishMashException.class, () -> command.execute(book));

        assertEquals("Generation failed.", error.getExplanation());
    }

    @Test
    void generatePlan_failure_showsAdviceAndTheShortUsageAndKeepsThePlan() {
        RecipeBook book = new RecipeBook();
        threeMeals().forEach(book::add);
        ActiveMealPlan activePlan = new ActiveMealPlan();
        GeneratePlanCommand command = new GeneratePlanCommand(
                new GeneratePlanRequest(1, new NutritionTargets(5000, null, null, null)),
                new MealPlanGenerator(new Random(42)), activePlan, new DietarySettings());

        MishMashException error = assertThrows(MishMashException.class, () -> command.execute(book));

        assertTrue(error.getExplanation().contains("Could not find 3 recipes"), error.getExplanation());
        assertTrue(error.getExplanation().contains("Why this happened:"), error.getExplanation());
        assertTrue(error.getExplanation().contains("What to try next:"), error.getExplanation());
        assertEquals(Optional.of(GeneratePlanParser.USAGE), error.getUsage());
        assertTrue(activePlan.get().isEmpty());
    }
}
//@@author
