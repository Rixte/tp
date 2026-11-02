package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

/**
 * Checks recipe deletion by index, its confirmation question, and the errors for indices that do not exist.
 */
class DeleteRecipeCommandTest {
    /**
     * Creates a minimal valid recipe with the given name.
     */
    private Recipe createRecipe(String name) {
        return new Recipe(name, 100, 1, 1, 1,
                List.of(new GroceryItem("Rice", new BigDecimal("100"), "g")));
    }

    /**
     * Creates a book holding the given recipes in order.
     */
    private RecipeBook createBook(Recipe... recipes) {
        RecipeBook book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.add(recipe);
        }
        return book;
    }

    @Test
    void constructor_indexBelowOne_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new DeleteRecipeCommand(0));
        assertThrows(IllegalArgumentException.class, () -> new DeleteRecipeCommand(-1));
        assertThrows(IllegalArgumentException.class, () -> new DeleteRecipeCommand(Integer.MIN_VALUE));
    }

    @Test
    void execute_validIndex_removesThatRecipeAndReturnsConfirmation() throws MishMashException {
        Recipe rice = createRecipe("Rice");
        Recipe oats = createRecipe("Oats");
        Recipe chicken = createRecipe("Chicken");
        RecipeBook book = createBook(rice, oats, chicken);

        String result = new DeleteRecipeCommand(2).execute(book);

        String expected = String.join(System.lineSeparator(),
                "Recipe deleted!",
                "Oats",
                "Recipes remaining: 2");
        assertEquals(expected, result);
        assertEquals(List.of(rice, chicken), book.getRecipes());
    }

    @Test
    void execute_firstAndLastIndex_removeTheCorrectRecipes() throws MishMashException {
        Recipe rice = createRecipe("Rice");
        Recipe oats = createRecipe("Oats");
        Recipe chicken = createRecipe("Chicken");
        RecipeBook book = createBook(rice, oats, chicken);

        new DeleteRecipeCommand(3).execute(book);
        assertEquals(List.of(rice, oats), book.getRecipes());

        new DeleteRecipeCommand(1).execute(book);
        assertEquals(List.of(oats), book.getRecipes());
    }

    @Test
    void execute_repeatedAtSameIndex_deletesSuccessiveRecipes() throws MishMashException {
        Recipe rice = createRecipe("Rice");
        Recipe oats = createRecipe("Oats");
        Recipe chicken = createRecipe("Chicken");
        RecipeBook book = createBook(rice, oats, chicken);
        DeleteRecipeCommand deleteFirst = new DeleteRecipeCommand(1);

        // Later recipes move up after each delete, so index 1 now points at the next recipe.
        assertEquals("Recipe deleted!" + System.lineSeparator() + "Rice"
                + System.lineSeparator() + "Recipes remaining: 2", deleteFirst.execute(book));
        assertEquals("Recipe deleted!" + System.lineSeparator() + "Oats"
                + System.lineSeparator() + "Recipes remaining: 1", deleteFirst.execute(book));

        assertEquals(List.of(chicken), book.getRecipes());
    }

    @Test
    void execute_duplicateNames_removesOnlyRecipeAtIndex() throws MishMashException {
        Recipe firstRice = createRecipe("Rice");
        Recipe secondRice = createRecipe("Rice");
        RecipeBook book = createBook(firstRice, secondRice);

        new DeleteRecipeCommand(2).execute(book);

        // Recipe has no equals(), so this compares the exact object that was kept.
        assertEquals(List.of(firstRice), book.getRecipes());
    }

    @Test
    void execute_lastRecipe_leavesEmptyBookAndReportsZeroRemaining() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Rice"));

        String result = new DeleteRecipeCommand(1).execute(book);

        assertEquals("Recipe deleted!" + System.lineSeparator() + "Rice"
                + System.lineSeparator() + "Recipes remaining: 0", result);
        assertEquals(0, book.getRecipes().size());
    }

    @Test
    void execute_indexTooLarge_throwsAndLeavesBookUnchanged() {
        Recipe rice = createRecipe("Rice");
        Recipe oats = createRecipe("Oats");
        RecipeBook book = createBook(rice, oats);

        for (int index : new int[] {3, 4, Integer.MAX_VALUE}) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> new DeleteRecipeCommand(index).execute(book));
            assertEquals("Recipe index must be between 1 and 2.", exception.getExplanation());
        }

        assertEquals(List.of(rice, oats), book.getRecipes());
    }

    @Test
    void execute_emptyBook_throwsNoRecipesToDelete() {
        RecipeBook book = new RecipeBook();

        MishMashException exception = assertThrows(MishMashException.class,
                () -> new DeleteRecipeCommand(1).execute(book));

        assertEquals("There are no recipes to delete.", exception.getExplanation());
        assertEquals(0, book.getRecipes().size());
    }

    @Test
    void execute_nullBook_throwsNullPointerException() {
        DeleteRecipeCommand command = new DeleteRecipeCommand(1);

        assertThrows(NullPointerException.class, () -> command.execute(null));
    }

    @Test
    void confirmationPrompt_validIndex_namesRecipeWithoutDeletingIt() throws MishMashException {
        Recipe rice = createRecipe("Rice");
        Recipe oats = createRecipe("Oats");
        RecipeBook book = createBook(rice, oats);

        String prompt = new DeleteRecipeCommand(2).confirmationPrompt(book).orElseThrow();

        assertEquals("Are you sure you want to delete recipe 2: Oats? (y/n)", prompt);
        assertEquals(List.of(rice, oats), book.getRecipes());
    }

    @Test
    void confirmationPrompt_duplicateNames_includesIndexSoTheyCanBeTold() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Rice"), createRecipe("Rice"));

        assertEquals("Are you sure you want to delete recipe 1: Rice? (y/n)",
                new DeleteRecipeCommand(1).confirmationPrompt(book).orElseThrow());
        assertEquals("Are you sure you want to delete recipe 2: Rice? (y/n)",
                new DeleteRecipeCommand(2).confirmationPrompt(book).orElseThrow());
    }

    @Test
    void confirmationPrompt_indexTooLarge_throwsBeforeAnythingIsAsked() {
        Recipe rice = createRecipe("Rice");
        RecipeBook book = createBook(rice);

        MishMashException exception = assertThrows(MishMashException.class,
                () -> new DeleteRecipeCommand(2).confirmationPrompt(book));

        assertEquals("Recipe index must be between 1 and 1.", exception.getExplanation());
        assertEquals(List.of(rice), book.getRecipes());
    }

    @Test
    void confirmationPrompt_emptyBook_throwsNoRecipesToDelete() {
        RecipeBook book = new RecipeBook();

        MishMashException exception = assertThrows(MishMashException.class,
                () -> new DeleteRecipeCommand(1).confirmationPrompt(book));

        assertEquals("There are no recipes to delete.", exception.getExplanation());
    }

    @Test
    void confirmationPrompt_nullBook_throwsNullPointerException() {
        DeleteRecipeCommand command = new DeleteRecipeCommand(1);

        assertThrows(NullPointerException.class, () -> command.confirmationPrompt(null));
    }
}
