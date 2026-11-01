package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author Rixte
/**
 * Checks that resetting clears every plan-owned field and preserves recipe and earlier plan snapshots.
 */
class ResetPlanCommandTest {
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final RecipeBook book = new RecipeBook();
    private final Recipe recipe = TestRecipes.recipe("Stored recipe", 300, 20, 40, 5);
    private final MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(recipe))));
    private final GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");

    @Test
    void constructor_nullPlanHolder_rejectsInvalidDependency() {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new ResetPlanCommand(null));

        assertEquals("activePlan", error.getMessage());
    }

    @TestFactory
    Stream<DynamicTest> execute_generatedPlan_clearsRequestAndEveryGroceryState() {
        return Stream.of(Optional.<List<GroceryItem>>empty(), Optional.of(List.<GroceryItem>of()),
                Optional.of(List.of(rice))).map(groceries -> DynamicTest.dynamicTest(groceries.toString(), () -> {
                    ActiveMealPlan active = new ActiveMealPlan();
                    active.setGenerated(plan, new GeneratePlanRequest(1, new NutritionTargets(300, 20, 40, 5)),
                            groceries);
                    RecipeBook currentBook = new RecipeBook();
                    currentBook.add(recipe);
                    List<Recipe> originalRecipes = currentBook.getRecipes();

                    String result = new ResetPlanCommand(active).execute(currentBook);

                    assertEquals("Meal plan reset. Recipes, dietary preferences, and recorded meals are unchanged.",
                            result);
                    assertTrue(active.get().isEmpty());
                    assertTrue(active.getRequest().isEmpty());
                    assertTrue(active.getGroceryList().isEmpty());
                    assertEquals(originalRecipes, currentBook.getRecipes());
                    assertSame(recipe, plan.getDays().getFirst().getMeals().getFirst());
                }));
    }

    @TestFactory
    Stream<DynamicTest> execute_legacyAndEmptyPlans_clearsPlanWithoutRequiringGenerationSettings() {
        return Stream.of(plan, new MealPlan(List.of()), new MealPlan(List.of(new DayPlan(List.of()))))
                .map(current -> DynamicTest.dynamicTest(describePlan(current), () -> {
                    ActiveMealPlan active = new ActiveMealPlan();
                    active.set(current);

                    assertTrue(new ResetPlanCommand(active).execute(book).startsWith("Meal plan reset."));
                    assertTrue(active.get().isEmpty());
                    assertTrue(active.getRequest().isEmpty());
                    assertTrue(active.getGroceryList().isEmpty());
                }));
    }

    @Test
    void execute_repeatedResets_safelyReportsMissingPlanAndPreservesRecipes() throws MishMashException {
        book.add(recipe);
        activePlan.set(plan);
        ResetPlanCommand reset = new ResetPlanCommand(activePlan);
        reset.execute(book);

        assertEquals("No active meal plan to reset.", reset.execute(book));
        assertEquals("No active meal plan to reset.", reset.execute(book));
        assertEquals(List.of(recipe), book.getRecipes());
        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getRequest().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_groceriesWithoutPlan_clearsRemainingDerivedData() throws MishMashException {
        activePlan.setGroceryList(List.of(rice));

        assertTrue(new ResetPlanCommand(activePlan).execute(book).startsWith("Meal plan reset."));
        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_planReplacedAfterConstruction_resetsLatestPlanWithoutChangingOldSnapshots() throws MishMashException {
        ResetPlanCommand reset = new ResetPlanCommand(activePlan);
        activePlan.set(plan);
        reset.execute(book);
        MealPlan replacement = new MealPlan(List.of(new DayPlan(List.of(recipe, recipe))));
        activePlan.set(replacement);
        activePlan.setGroceryList(List.of(rice));

        reset.execute(book);

        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
        assertEquals(300, plan.getDays().getFirst().getTotalCalories());
        assertEquals(600, replacement.getDays().getFirst().getTotalCalories());
    }

    @TestFactory
    Stream<DynamicTest> execute_supportedPlanLengths_clearsAllDaysAndMaximumTargets() {
        return IntStream.rangeClosed(1, 7).mapToObj(days -> DynamicTest.dynamicTest(days + " days", () -> {
            ActiveMealPlan active = new ActiveMealPlan();
            MealPlan current = new MealPlan(IntStream.range(0, days)
                    .mapToObj(index -> new DayPlan(List.of(recipe))).toList());
            NutritionTargets targets = new NutritionTargets(Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE);
            active.setGenerated(current, new GeneratePlanRequest(days, targets), Optional.of(List.of(rice)));

            new ResetPlanCommand(active).execute(book);

            assertTrue(active.get().isEmpty());
            assertTrue(active.getRequest().isEmpty());
            assertTrue(active.getGroceryList().isEmpty());
            assertEquals(days, current.getNumberOfDays());
        }));
    }

    @TestFactory
    Stream<DynamicTest> execute_differentRecipeBooks_preservesEmptyRepeatedAndDuplicateNameEntries() {
        Recipe sameName = TestRecipes.recipe("Stored recipe", 600, 40, 80, 10);
        return Stream.of(List.<Recipe>of(), List.of(recipe), List.of(recipe, recipe), List.of(recipe, sameName))
                .map(recipes -> DynamicTest.dynamicTest(recipes.stream().map(Recipe::getCalories).toList().toString(),
                        () -> {
                            RecipeBook currentBook = new RecipeBook();
                            recipes.forEach(currentBook::add);
                            ActiveMealPlan active = new ActiveMealPlan();
                            active.set(plan);

                            new ResetPlanCommand(active).execute(currentBook);

                            assertEquals(recipes, currentBook.getRecipes());
                            assertEquals(recipes.size(), currentBook.size());
                            assertTrue(active.get().isEmpty());
                        }));
    }

    @Test
    void execute_twoPendingCommands_secondResetDoesNotResurrectPreviousState() throws MishMashException {
        ResetPlanCommand first = new ResetPlanCommand(activePlan);
        ResetPlanCommand second = new ResetPlanCommand(activePlan);
        activePlan.set(plan);
        activePlan.setGroceryList(List.of(rice));

        first.execute(book);

        assertEquals("No active meal plan to reset.", second.execute(book));
        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getRequest().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
    }

    @Test
    void execute_brokenClearImplementation_reportsRecoverableFailure() {
        ActiveMealPlan broken = new ActiveMealPlan() {
            @Override
            public void clear() {
                // Simulates a model bug that fails to implement the clear contract.
            }
        };
        broken.set(plan);

        MishMashException error = assertThrows(MishMashException.class,
                () -> new ResetPlanCommand(broken).execute(book));

        assertEquals("The meal plan could not be reset. Please try again.", error.getExplanation());
        assertEquals(Optional.of(ResetPlanCommand.USAGE), error.getUsage());
        assertSame(plan, broken.get().orElseThrow());
    }

    @Test
    void execute_requestRemainsAfterMealsCleared_reportsRecoverableFailureAndPreservesRecipes() {
        GeneratePlanRequest request = new GeneratePlanRequest(1, new NutritionTargets(300, 20, 40, 5));
        ActiveMealPlan incomplete = new ActiveMealPlan() {
            @Override
            public Optional<GeneratePlanRequest> getRequest() {
                // Simulates a stale request source after clear has removed the meals and groceries.
                return Optional.of(request);
            }
        };
        incomplete.setGenerated(plan, request, Optional.of(List.of(rice)));
        book.add(recipe);

        MishMashException error = assertThrows(MishMashException.class,
                () -> new ResetPlanCommand(incomplete).execute(book));

        assertEquals("The meal plan could not be reset. Please try again.", error.getExplanation());
        assertEquals(Optional.of(ResetPlanCommand.USAGE), error.getUsage());
        assertNull(error.getCause());
        assertTrue(incomplete.get().isEmpty());
        assertTrue(incomplete.getGroceryList().isEmpty());
        assertSame(request, incomplete.getRequest().orElseThrow());
        assertEquals(List.of(recipe), book.getRecipes());
    }

    @TestFactory
    Stream<DynamicTest> execute_groceriesRemainAfterMealsCleared_rejectsEmptyAndNonEmptySnapshots() {
        return Stream.of(List.<GroceryItem>of(), List.of(rice))
                .map(groceries -> DynamicTest.dynamicTest(groceries.toString(), () -> {
                    ActiveMealPlan incomplete = new ActiveMealPlan() {
                        @Override
                        public void clear() {
                            super.clear();
                            // Simulates incomplete clearing that leaves only the grocery snapshot behind.
                            setGroceryList(groceries);
                        }
                    };
                    GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
                    incomplete.setGenerated(plan, request, Optional.of(groceries));
                    RecipeBook currentBook = new RecipeBook();
                    currentBook.add(recipe);

                    MishMashException error = assertThrows(MishMashException.class,
                            () -> new ResetPlanCommand(incomplete).execute(currentBook));

                    assertEquals("The meal plan could not be reset. Please try again.", error.getExplanation());
                    assertEquals(Optional.of(ResetPlanCommand.USAGE), error.getUsage());
                    assertNull(error.getCause());
                    assertTrue(incomplete.get().isEmpty());
                    assertTrue(incomplete.getRequest().isEmpty());
                    assertEquals(Optional.of(groceries), incomplete.getGroceryList());
                    assertEquals(List.of(recipe), currentBook.getRecipes());
                }));
    }

    /**
     * Returns a distinct test label for legacy, zero-day, and empty-day plans.
     */
    private String describePlan(MealPlan current) {
        return "Meals per day: " + current.getDays().stream().map(day -> day.getMeals().size()).toList();
    }
}
//@@author
