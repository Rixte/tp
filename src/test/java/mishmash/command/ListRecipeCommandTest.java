package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

/**
 * Checks the recipe list, the full details of one recipe, and the errors for recipes that do not exist.
 */
class ListRecipeCommandTest {
    /**
     * Creates a recipe with the given name, calories and a single ingredient.
     */
    private Recipe createRecipe(String name, int calories) {
        return new Recipe(name, calories, 5, 27, 3,
                List.of(new GroceryItem("Oats", new BigDecimal("40"), "g")));
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
        assertThrows(IllegalArgumentException.class, () -> new ListRecipeCommand(0));
        assertThrows(IllegalArgumentException.class, () -> new ListRecipeCommand(-1));
        assertThrows(IllegalArgumentException.class, () -> new ListRecipeCommand(Integer.MIN_VALUE));
    }

    @Test
    void execute_noIndex_listsNumberedNamesAndCalories() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Chicken and Rice", 650), createRecipe("Oats", 150));

        String result = new ListRecipeCommand().execute(book);

        String expected = String.join(System.lineSeparator(),
                "Recipes:",
                "1. Chicken and Rice (650 kcal)",
                "2. Oats (150 kcal)");
        assertEquals(expected, result);
    }

    @Test
    void execute_noIndex_doesNotShowMacrosOrIngredients() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Oats", 150));

        String result = new ListRecipeCommand().execute(book);

        assertFalse(result.contains("Protein"), result);
        assertFalse(result.contains("Ingredients"), result);
    }

    @Test
    void execute_noIndexAndNoRecipes_saysThereAreNone() throws MishMashException {
        String result = new ListRecipeCommand().execute(new RecipeBook());

        assertEquals("No recipes yet. Use 'add-recipe' to add one.", result);
    }

    @Test
    void execute_noIndexWithDuplicateNames_numbersEachRecipe() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Oats", 150), createRecipe("Oats", 200));

        String result = new ListRecipeCommand().execute(book);

        assertEquals("Recipes:" + System.lineSeparator() + "1. Oats (150 kcal)"
                + System.lineSeparator() + "2. Oats (200 kcal)", result);
    }

    @Test
    void execute_validIndex_showsFullRecipe() throws MishMashException {
        Recipe chicken = new Recipe("Chicken and Rice", 650, 50, 70, 15, List.of(
                new GroceryItem("Chicken Breast", new BigDecimal("200"), "g"),
                new GroceryItem("White Rice", new BigDecimal("150"), "g"),
                new GroceryItem("Olive Oil", new BigDecimal("2.50"), "ml")));
        RecipeBook book = createBook(createRecipe("Oats", 150), chicken);

        String result = new ListRecipeCommand(2).execute(book);

        String expected = String.join(System.lineSeparator(),
                "Chicken and Rice",
                "650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g",
                "Ingredients:",
                "- Chicken Breast: 200g",
                "- White Rice: 150g",
                "- Olive Oil: 2.5ml",
                "Food tags: Unclassified");
        assertEquals(expected, result);
    }

    @Test
    void execute_firstAndLastIndex_showTheCorrectRecipes() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Rice", 200), createRecipe("Oats", 150),
                createRecipe("Eggs", 300));

        assertEquals("Rice", new ListRecipeCommand(1).execute(book).split(System.lineSeparator())[0]);
        assertEquals("Eggs", new ListRecipeCommand(3).execute(book).split(System.lineSeparator())[0]);
    }

    @Test
    void execute_duplicateNames_showsOnlyRecipeAtIndex() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Oats", 150), createRecipe("Oats", 200));

        String result = new ListRecipeCommand(2).execute(book);

        assertEquals("Oats", result.split(System.lineSeparator())[0]);
        assertEquals("200 kcal | Protein: 5g | Carbs: 27g | Fats: 3g", result.split(System.lineSeparator())[1]);
    }

    @Test
    void execute_indexTooLarge_throwsAndLeavesBookUnchanged() {
        Recipe rice = createRecipe("Rice", 200);
        Recipe oats = createRecipe("Oats", 150);
        RecipeBook book = createBook(rice, oats);

        for (int index : new int[] {3, 4, Integer.MAX_VALUE}) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> new ListRecipeCommand(index).execute(book));
            assertEquals("Recipe index must be between 1 and 2.", exception.getExplanation());
        }

        assertEquals(List.of(rice, oats), book.getRecipes());
    }

    @Test
    void execute_indexWithNoRecipes_throwsNoRecipesToShow() {
        MishMashException exception = assertThrows(MishMashException.class,
                () -> new ListRecipeCommand(1).execute(new RecipeBook()));

        assertEquals("There are no recipes to show. Use 'add-recipe' to add one.", exception.getExplanation());
    }

    @Test
    void execute_repeatedCalls_giveSameResultAndKeepRecipes() throws MishMashException {
        Recipe rice = createRecipe("Rice", 200);
        Recipe oats = createRecipe("Oats", 150);
        RecipeBook book = createBook(rice, oats);
        ListRecipeCommand listAll = new ListRecipeCommand();
        ListRecipeCommand listOne = new ListRecipeCommand(1);

        assertEquals(listAll.execute(book), listAll.execute(book));
        assertEquals(listOne.execute(book), listOne.execute(book));
        assertEquals(List.of(rice, oats), book.getRecipes());
    }

    @Test
    void execute_nullBook_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new ListRecipeCommand().execute(null));
        assertThrows(NullPointerException.class, () -> new ListRecipeCommand(1).execute(null));
    }

    @Test
    void confirmationPrompt_listing_asksNothing() throws MishMashException {
        RecipeBook book = createBook(createRecipe("Oats", 150));

        assertFalse(new ListRecipeCommand().confirmationPrompt(book).isPresent());
        assertFalse(new ListRecipeCommand(1).confirmationPrompt(book).isPresent());
    }
}
