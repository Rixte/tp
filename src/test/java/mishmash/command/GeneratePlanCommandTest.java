package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.GeneratePlanParser;
import mishmash.testutil.LogCapture;
import mishmash.testutil.TestRecipes;

/**
 * Checks that generate-plan stores the plan, replaces an older one, shows it like view-plan,
 * and keeps the previous plan when generation fails.
 */
class GeneratePlanCommandTest {
    private final MealPlanGenerator generator = new MealPlanGenerator(new Random(1));
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final RecipeBook recipeBook = createRecipeBook(
            TestRecipes.recipe("Oatmeal", 500, 20, 80, 10),
            TestRecipes.recipe("Chicken Rice", 600, 40, 70, 15),
            TestRecipes.recipe("Salmon Bowl", 700, 45, 60, 25),
            TestRecipes.recipe("Apple", 100, 1, 25, 0));

    /**
     * Creates a recipe book preserving the supplied recipes' order for command-level checks.
     */
    private static RecipeBook createRecipeBook(Recipe... recipes) {
        RecipeBook book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.add(recipe);
        }
        return book;
    }

    /**
     * Creates a command connected to this test's generator and active plan.
     */
    private GeneratePlanCommand createCommand(String arguments) {
        return new GeneratePlanCommand(new GeneratePlanParser().parse(arguments), generator, activePlan);
    }

    @Test
    void activePlan_initiallyEmpty() {
        assertTrue(activePlan.get().isEmpty());
    }

    @Test
    void execute_success_setsActivePlan() throws MishMashException {
        String message = createCommand("d/3").execute(recipeBook);

        assertEquals(3, activePlan.get().orElseThrow().getNumberOfDays());
        assertEquals("Meal plan successfully generated for 3 day(s)!" + System.lineSeparator()
                + "Use 'view-plan' to inspect your planned meals.", message);
    }

    @Test
    void execute_secondPlan_replacesFirst() throws MishMashException {
        createCommand("d/2").execute(recipeBook);
        MealPlan first = activePlan.get().orElseThrow();

        createCommand("d/5").execute(recipeBook);
        MealPlan second = activePlan.get().orElseThrow();

        assertNotSame(first, second);
        assertEquals(5, second.getNumberOfDays());
    }

    @Test
    void execute_failure_keepsPreviousPlan() throws MishMashException {
        createCommand("d/2").execute(recipeBook);
        new GenerateListCommand(activePlan).execute(recipeBook);
        MealPlan previous = activePlan.get().orElseThrow();
        GeneratePlanRequest previousRequest = activePlan.getRequest().orElseThrow();
        List<GroceryItem> previousGroceries = activePlan.getGroceryList().orElseThrow();
        List<Recipe> originalRecipes = recipeBook.getRecipes();
        RecipeBook emptyBook = new RecipeBook();

        for (RecipeBook source : List.of(recipeBook, emptyBook)) {
            String arguments = source == recipeBook ? "d/3 cal/99999" : "d/3";
            assertThrows(MishMashException.class, () -> createCommand(arguments).execute(source));

            assertSame(previous, activePlan.get().orElseThrow());
            assertSame(previousRequest, activePlan.getRequest().orElseThrow());
            assertSame(previousGroceries, activePlan.getGroceryList().orElseThrow());
            assertEquals(originalRecipes, recipeBook.getRecipes());
            assertTrue(emptyBook.getRecipes().isEmpty());
        }
    }

    @Test
    void execute_failureWithoutMessage_preservesCauseAndPreviousState() throws MishMashException {
        createCommand("d/1").execute(recipeBook);
        new GenerateListCommand(activePlan).execute(recipeBook);
        MealPlan previousPlan = activePlan.get().orElseThrow();
        GeneratePlanRequest previousRequest = activePlan.getRequest().orElseThrow();
        List<GroceryItem> previousGroceries = activePlan.getGroceryList().orElseThrow();
        List<Recipe> previousRecipes = recipeBook.getRecipes();
        IllegalArgumentException failure = new IllegalArgumentException();
        MealPlanGenerator failingGenerator = new MealPlanGenerator(new Random(1)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                throw failure;
            }
        };
        GeneratePlanCommand command = new GeneratePlanCommand(new GeneratePlanParser().parse("d/2"),
                failingGenerator, activePlan);

        MishMashException error = assertThrows(MishMashException.class, () -> command.execute(recipeBook));

        assertEquals("Command could not be completed.", error.getExplanation());
        assertSame(failure, error.getCause());
        assertSame(previousPlan, activePlan.get().orElseThrow());
        assertSame(previousRequest, activePlan.getRequest().orElseThrow());
        assertSame(previousGroceries, activePlan.getGroceryList().orElseThrow());
        assertEquals(previousRecipes, recipeBook.getRecipes());
    }

    @Test
    void execute_emptyRecipeBook_reportsHowToFix() {
        MishMashException e = assertThrows(MishMashException.class,
                () -> createCommand("d/3").execute(new RecipeBook()));

        assertTrue(e.getMessage().contains("recipe book is empty"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(e.getExplanation().startsWith(e.getCause().getMessage()), e.getExplanation());
    }

    @Test
    void execute_failedGeneration_logsRejectionWithoutClaimingSuccess() throws MishMashException {
        createCommand("d/1").execute(recipeBook);
        MealPlan savedPlan = activePlan.get().orElseThrow();
        GeneratePlanRequest savedRequest = activePlan.getRequest().orElseThrow();
        var savedGroceries = activePlan.getGroceryList();
        List<Recipe> originalRecipes = recipeBook.getRecipes();
        try (LogCapture logs = new LogCapture(GeneratePlanCommand.class)) {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> createCommand("d/2 cal/99999").execute(recipeBook));

            assertSame(savedPlan, activePlan.get().orElseThrow());
            assertSame(savedRequest, activePlan.getRequest().orElseThrow());
            assertEquals(savedGroceries, activePlan.getGroceryList());
            assertEquals(originalRecipes, recipeBook.getRecipes());
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertEquals(1, logs.getRecords().size());
            assertTrue(logs.getMessages().contains("generation rejected for 2 days"));
            assertTrue(logs.getRecords().stream()
                    .noneMatch(record -> record.getMessage().contains("Stored generated")));
        }
    }

    @Test
    void execute_success_messageConfirmsAndPointsToViewPlan() throws MishMashException {
        String message = createCommand("d/2").execute(recipeBook);

        assertEquals("Meal plan successfully generated for 2 day(s)!" + System.lineSeparator()
                + "Use 'view-plan' to inspect your planned meals.", message);
    }

    @Test
    void execute_laterRecipeAdded_isConsideredNextTime() throws MishMashException {
        RecipeBook small = createRecipeBook(
                TestRecipes.recipe("A", 100, 1, 1, 1),
                TestRecipes.recipe("B", 100, 1, 1, 1));
        List<Recipe> originalRecipes = small.getRecipes();
        assertThrows(MishMashException.class, () -> createCommand("d/1").execute(small));
        assertTrue(activePlan.get().isEmpty());
        assertTrue(activePlan.getRequest().isEmpty());
        assertTrue(activePlan.getGroceryList().isEmpty());
        assertEquals(originalRecipes, small.getRecipes());

        small.add(TestRecipes.recipe("C", 100, 1, 1, 1));
        createCommand("d/1").execute(small);

        assertEquals(1, activePlan.get().orElseThrow().getNumberOfDays());
    }

    @Test
    void constructor_nullArguments_throwNullPointerException() {
        GeneratePlanRequest request = new GeneratePlanParser().parse("d/1");

        assertThrows(NullPointerException.class, () -> new GeneratePlanCommand(null, generator, activePlan));
        assertThrows(NullPointerException.class, () -> new GeneratePlanCommand(request, null, activePlan));
        assertThrows(NullPointerException.class, () -> new GeneratePlanCommand(request, generator, null));
    }

    @Test
    void execute_nullRecipeBook_throwsNullPointerException() {
        GeneratePlanCommand command = createCommand("d/1");

        assertThrows(NullPointerException.class, () -> command.execute(null));
    }

    @Test
    void activePlan_setNull_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> activePlan.set(null));
    }

    //@@author GauthamNandi
    @Test
    void execute_unexpectedFailure_hidesItsDetailsLogsItAndKeepsThePlan() throws MishMashException {
        createCommand("d/1").execute(recipeBook);
        MealPlan previousPlan = activePlan.get().orElseThrow();
        MealPlanGenerator faultyGenerator = new MealPlanGenerator(new Random(1)) {
            @Override
            public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
                throw new IllegalArgumentException("internal detail");
            }
        };
        GeneratePlanCommand command = new GeneratePlanCommand(new GeneratePlanParser().parse("d/2"),
                faultyGenerator, activePlan);

        try (LogCapture logs = new LogCapture(GeneratePlanCommand.class)) {
            MishMashException error = assertThrows(MishMashException.class, () -> command.execute(recipeBook));

            assertEquals("Command could not be completed.", error.getExplanation());
            assertTrue(logs.getMessages().contains("Unexpected meal-plan generation failure"));
        }
        assertSame(previousPlan, activePlan.get().orElseThrow());
    }

    @Test
    void execute_adviceFails_stillShowsTheGenerationError() {
        // The first read serves generation; the second read, by the advisor, fails.
        RecipeBook faultyBook = new RecipeBook() {
            private int reads;

            @Override
            public List<Recipe> getRecipes() {
                reads++;
                if (reads > 1) {
                    throw new IllegalStateException("advice failure");
                }
                return super.getRecipes();
            }
        };

        MishMashException error = assertThrows(MishMashException.class, () -> createCommand("d/1").execute(faultyBook));

        assertTrue(error.getExplanation().startsWith("Your recipe book is empty."), error.getExplanation());
    }
    //@@author
}
