package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.FoodInfo;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.model.RecipeChanges;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;
import mishmash.ui.RecipeViewer;

//@@author Rixte
/**
 * Checks positional edits, atomic failures, execution-time lookup, and generated snapshots.
 */
class EditRecipeCommandTest {
    /**
     * Builds a protein-only change for direct command tests.
     */
    private RecipeChanges proteinChange() {
        return new RecipeChanges(null, null, 25, null, null, List.of(), null);
    }

    @Test
    void execute_duplicateNames_editsOnlyChosenPositionAndPreservesBookSnapshot() throws MishMashException {
        RecipeBook book = new RecipeBook();
        Recipe first = TestRecipes.recipe("Rice", 200, 4, 45, 1);
        Recipe second = TestRecipes.recipe("Rice", 300, 6, 60, 2);
        Recipe third = TestRecipes.recipe("Oats", 150, 5, 27, 3);
        book.add(first);
        book.add(second);
        book.add(third);
        List<Recipe> before = book.getRecipes();

        String result = new EditRecipeCommand(2, proteinChange()).execute(book);

        assertEquals(3, book.size());
        assertSame(first, book.getRecipes().getFirst());
        assertSame(third, book.getRecipes().getLast());
        Recipe edited = book.getRecipes().get(1);
        assertEquals(300, edited.getCalories());
        assertEquals(25, edited.getProtein());
        assertEquals(6, second.getProtein());
        assertEquals(List.of(first, second, third), before);
        assertTrue(result.startsWith("Recipe updated!" + System.lineSeparator()
                + new RecipeViewer().view(edited)), result);
        assertTrue(result.contains("Generate a new plan and grocery list"), result);
    }

    @Test
    void execute_firstAndLastPositions_preservesOtherRecipes() throws MishMashException {
        RecipeBook book = new RecipeBook();
        book.add(TestRecipes.recipe("First", 1, 1, 1, 1));
        Recipe middle = TestRecipes.recipe("Middle", 2, 2, 2, 2);
        book.add(middle);
        book.add(TestRecipes.recipe("Last", 3, 3, 3, 3));

        new EditRecipeCommand(1, proteinChange()).execute(book);
        new EditRecipeCommand(3, proteinChange()).execute(book);

        assertEquals(25, book.getRecipes().getFirst().getProtein());
        assertSame(middle, book.getRecipes().get(1));
        assertEquals(25, book.getRecipes().getLast().getProtein());
    }

    @Test
    void execute_parsedBeforeOtherChanges_usesCurrentRecipeAndPreservesItsNewValues() throws MishMashException {
        Parser parser = new Parser();
        RecipeBook book = new RecipeBook();
        book.add(TestRecipes.recipe("Original", 200, 4, 45, 1));
        Command pending = parser.parseCommand("edit-recipe 1 p/25");
        parser.parseCommand("edit-recipe 1 n/New Name cal/350").execute(book);

        pending.execute(book);

        assertEquals("New Name", book.getRecipes().getFirst().getName());
        assertEquals(350, book.getRecipes().getFirst().getCalories());
        assertEquals(25, book.getRecipes().getFirst().getProtein());
    }

    @Test
    void execute_missingTarget_reportsErrorAndLeavesBookUnchanged() {
        RecipeBook book = new RecipeBook();
        MishMashException empty = assertThrows(MishMashException.class,
                () -> new EditRecipeCommand(1, proteinChange()).execute(book));
        assertEquals("There are no recipes to edit.", empty.getMessage());
        Recipe recipe = TestRecipes.recipe("Rice", 200, 4, 45, 1);
        book.add(recipe);
        for (int index : new int[] {2, Integer.MAX_VALUE}) {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> new EditRecipeCommand(index, proteinChange()).execute(book));
            assertEquals("Recipe index must be between 1 and 1.", error.getMessage());
        }
        assertEquals(List.of(recipe), book.getRecipes());
    }

    @Test
    void constructorAndExecute_invalidDependencies_failClearly() {
        assertThrows(IllegalArgumentException.class, () -> new EditRecipeCommand(0, proteinChange()));
        assertThrows(IllegalArgumentException.class, () -> new EditRecipeCommand(-1, proteinChange()));
        assertThrows(NullPointerException.class, () -> new EditRecipeCommand(1, null));
        assertThrows(NullPointerException.class, () -> new EditRecipeCommand(1, proteinChange()).execute(null));
    }

    @Test
    void execute_planAndGroceriesKeepSnapshots_regenerationUsesUpdatedRecipe() throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        Parser parser = new Parser(new MealPlanGenerator(new Random(1)), active);
        RecipeBook book = new RecipeBook();
        for (String name : List.of("A", "B", "C")) {
            parser.parseCommand("add-recipe n/" + name + " cal/600 p/30 c/50 f/10 i/Rice:100g food/none")
                    .execute(book);
        }
        parser.parseCommand("generate-plan d/1").execute(book);
        parser.parseCommand("generate-list").execute(book);
        var plan = active.get().orElseThrow();
        var request = active.getRequest();
        var groceries = active.getGroceryList().orElseThrow();

        parser.parseCommand("edit-recipe 1 cal/800 p/50 i/Tofu:200g").execute(book);

        assertSame(plan, active.get().orElseThrow());
        assertEquals(request, active.getRequest());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        assertEquals(1800, plan.getDays().getFirst().getTotalCalories());
        assertTrue(parser.parseCommand("check-shortfall cal/2000").execute(book).contains("short by 200 kcal"));
        parser.parseCommand("generate-list").execute(book);
        assertEquals(groceries.stream().map(Object::toString).toList(),
                active.getGroceryList().orElseThrow().stream().map(Object::toString).toList());

        parser.parseCommand("generate-plan d/1").execute(book);
        parser.parseCommand("generate-list").execute(book);
        assertEquals(2000, active.get().orElseThrow().getDays().getFirst().getTotalCalories());
        assertEquals(110, active.get().orElseThrow().getDays().getFirst().getTotalProtein());
        assertTrue(active.getGroceryList().orElseThrow().stream().anyMatch(item ->
                item.getName().equals("Tofu") && item.getQuantity().compareTo(new BigDecimal("200")) == 0
                        && item.getUnit().equals("g")));
    }

    @Test
    void execute_updatedFoodDeclaration_isUsedByDietaryFiltering() throws MishMashException {
        Parser parser = new Parser();
        RecipeBook book = new RecipeBook();
        for (String name : List.of("A", "B", "C")) {
            book.add(new Recipe(name, 600, 30, 50, 10,
                    List.of(new GroceryItem("Rice", BigDecimal.ONE, "g")), FoodInfo.parseField("none")));
        }
        parser.parseCommand("set-diet diet/vegetarian").execute(book);
        parser.parseCommand("edit-recipe 1 food/poultry").execute(book);

        assertThrows(MishMashException.class, () -> parser.parseCommand("generate-plan d/1").execute(book));
        parser.parseCommand("edit-recipe 1 food/none").execute(book);
        parser.parseCommand("generate-plan d/1").execute(book);
    }

    @Test
    void execute_afterDeletion_editsRecipeNowAtSelectedPosition() throws MishMashException {
        Recipe first = TestRecipes.recipe("First", 100, 1, 10, 2);
        Recipe second = TestRecipes.recipe("Second", 200, 2, 20, 3);
        Recipe third = TestRecipes.recipe("Third", 300, 3, 30, 4);
        RecipeBook book = new RecipeBook();
        book.add(first);
        book.add(second);
        book.add(third);
        EditRecipeCommand pending = new EditRecipeCommand(2, proteinChange());

        book.remove(0);
        pending.execute(book);

        assertSame(second, book.getRecipes().getFirst());
        Recipe edited = book.getRecipes().getLast();
        assertEquals("Third", edited.getName());
        assertEquals(300, edited.getCalories());
        assertEquals(25, edited.getProtein());
        assertEquals(3, third.getProtein());
        assertEquals(2, book.size());
    }

    @Test
    void execute_bookShrinksAfterParsing_rechecksIndexWithoutEditingAnotherRecipe() throws MishMashException {
        Parser parser = new Parser();
        RecipeBook book = new RecipeBook();
        Recipe kept = TestRecipes.recipe("Kept", 100, 1, 10, 2);
        book.add(kept);
        book.add(TestRecipes.recipe("Removed", 200, 2, 20, 3));
        Command pending = parser.parseCommand("edit-recipe 2 p/25");
        book.remove(1);

        MishMashException error = assertThrows(MishMashException.class, () -> pending.execute(book));

        assertEquals("Recipe index must be between 1 and 1.", error.getMessage());
        assertEquals(List.of(kept), book.getRecipes());
        assertEquals(1, kept.getProtein());
    }

    @Test
    void execute_failedOnEmptyBook_canBeReusedAfterAddingRecipe() throws MishMashException {
        RecipeBook book = new RecipeBook();
        EditRecipeCommand command = new EditRecipeCommand(1, proteinChange());
        assertThrows(MishMashException.class, () -> command.execute(book));
        book.add(TestRecipes.recipe("Added Later", 100, 1, 10, 2));

        command.execute(book);

        assertEquals("Added Later", book.getRecipes().getFirst().getName());
        assertEquals(25, book.getRecipes().getFirst().getProtein());
        assertEquals(1, book.size());
    }

    @Test
    void execute_reusedAcrossBooks_doesNotCarryOverOmittedFields() throws MishMashException {
        EditRecipeCommand command = new EditRecipeCommand(1, proteinChange());
        RecipeBook firstBook = new RecipeBook();
        RecipeBook secondBook = new RecipeBook();
        firstBook.add(TestRecipes.recipe("Rice", 200, 4, 45, 1));
        secondBook.add(TestRecipes.recipe("Oats", 500, 10, 60, 8));

        command.execute(firstBook);
        command.execute(secondBook);

        Recipe first = firstBook.getRecipes().getFirst();
        Recipe second = secondBook.getRecipes().getFirst();
        assertEquals("Rice", first.getName());
        assertEquals(200, first.getCalories());
        assertEquals(25, first.getProtein());
        assertEquals("Oats", second.getName());
        assertEquals(500, second.getCalories());
        assertEquals(25, second.getProtein());
        assertEquals(60, second.getCarbs());
        assertEquals(8, second.getFats());
    }

    @Test
    void execute_renameToExistingName_keepsBothEntriesAndTheirNutrition() throws MishMashException {
        RecipeBook book = new RecipeBook();
        book.add(TestRecipes.recipe("Rice", 200, 4, 45, 1));
        Recipe oats = TestRecipes.recipe("Oats", 150, 5, 27, 3);
        book.add(oats);

        new Parser().parseCommand("edit-recipe 1 n/Oats").execute(book);

        assertEquals(2, book.size());
        assertEquals("Oats", book.getRecipes().getFirst().getName());
        assertEquals(200, book.getRecipes().getFirst().getCalories());
        assertSame(oats, book.getRecipes().getLast());
    }

    @Test
    void execute_editMakesTargetsUnreachable_failedRegenerationPreservesOldPlan() throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        Parser parser = new Parser(new MealPlanGenerator(new Random(2)), active);
        RecipeBook book = new RecipeBook();
        for (String name : List.of("A", "B", "C")) {
            parser.parseCommand("add-recipe n/" + name + " cal/600 p/30 c/50 f/10 i/Rice:100g")
                    .execute(book);
        }
        parser.parseCommand("generate-plan d/2 cal/1700").execute(book);
        parser.parseCommand("generate-list").execute(book);
        var plan = active.get().orElseThrow();
        var request = active.getRequest().orElseThrow();
        var groceries = active.getGroceryList().orElseThrow();
        String before = parser.parseCommand("view-plan").execute(book);
        assertTrue(before.contains("Warning: Calories exceed target by 100 kcal"), before);

        parser.parseCommand("edit-recipe 1 cal/1000").execute(book);
        Recipe edited = book.getRecipes().getFirst();
        List<Recipe> editedRecipes = book.getRecipes();
        assertThrows(MishMashException.class,
                () -> parser.parseCommand("generate-plan d/1 cal/1700").execute(book));

        assertSame(plan, active.get().orElseThrow());
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        assertEquals(editedRecipes, book.getRecipes());
        assertEquals(before, parser.parseCommand("view-plan").execute(book));
        assertSame(edited, book.getRecipes().getFirst());
        assertEquals(1000, edited.getCalories());
    }

    @Test
    void execute_editedIngredientIsExcluded_futureGenerationUsesCurrentIngredients() throws MishMashException {
        ActiveMealPlan active = new ActiveMealPlan();
        Parser parser = new Parser(new MealPlanGenerator(new Random(3)), active);
        RecipeBook book = new RecipeBook();
        for (String name : List.of("A", "B", "C")) {
            parser.parseCommand("add-recipe n/" + name + " cal/600 p/30 c/50 f/10 i/Rice:100g food/none")
                    .execute(book);
        }
        parser.parseCommand("set-diet exclude/Tofu").execute(book);
        parser.parseCommand("generate-plan d/1").execute(book);
        parser.parseCommand("generate-list").execute(book);
        var originalPlan = active.get().orElseThrow();
        var originalGroceries = active.getGroceryList().orElseThrow();
        var originalRequest = active.getRequest().orElseThrow();

        parser.parseCommand("edit-recipe 1 i/Tofu:200g").execute(book);
        List<Recipe> editedRecipes = book.getRecipes();
        assertThrows(MishMashException.class, () -> parser.parseCommand("generate-plan d/1").execute(book));

        assertSame(originalPlan, active.get().orElseThrow());
        assertSame(originalRequest, active.getRequest().orElseThrow());
        assertSame(originalGroceries, active.getGroceryList().orElseThrow());
        assertEquals(editedRecipes, book.getRecipes());
        assertEquals(FoodInfo.parseField("none"), book.getRecipes().getFirst().getFoodInfo());
        parser.parseCommand("edit-recipe 1 i/Rice:200g").execute(book);
        parser.parseCommand("generate-plan d/1").execute(book);
        parser.parseCommand("generate-list").execute(book);
        assertEquals(List.of("Rice: 400g"), active.getGroceryList().orElseThrow().stream()
                .map(Object::toString).toList());
    }
}
//@@author
