package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.LogCapture;

/**
 * Checks recipe addition and its confirmation output.
 */
class AddRecipeCommandTest {
    /**
     * Creates a recipe with three ingredients for command tests.
     */
    private Recipe createRecipe() {
        return new Recipe("Chicken and Rice", 650, 50, 70, 15, List.of(
                new GroceryItem("Chicken Breast", new BigDecimal("200"), "g"),
                new GroceryItem("White Rice", new BigDecimal("150"), "g"),
                new GroceryItem("Olive Oil", new BigDecimal("10"), "ml")));
    }

    @Test
    void execute_validRecipe_addsRecipeAndReturnsConfirmation() {
        Recipe recipe = createRecipe();
        RecipeBook book = new RecipeBook();

        String result = new AddRecipeCommand(recipe).execute(book);

        String expected = String.join(System.lineSeparator(),
                "New recipe added!",
                "Chicken and Rice",
                "650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g",
                "Ingredients:",
                "- Chicken Breast: 200g",
                "- White Rice: 150g",
                "- Olive Oil: 10ml",
                "Food tags: Unclassified");

        assertEquals(List.of(recipe), book.getRecipes());
        assertEquals(expected, result);
    }

    @Test
    void execute_existingRecipe_preservesExistingEntry() {
        Recipe existing = createRecipe();
        Recipe added = createRecipe();
        RecipeBook book = new RecipeBook();
        book.add(existing);

        new AddRecipeCommand(added).execute(book);

        assertEquals(List.of(existing, added), book.getRecipes());
    }

    @Test
    void constructor_nullRecipe_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new AddRecipeCommand(null));
    }

    @Test
    void execute_nullBook_throwsNullPointerException() {
        AddRecipeCommand command = new AddRecipeCommand(createRecipe());

        assertThrows(NullPointerException.class, () -> command.execute(null));
    }

    @Test
    void confirmationPrompt_addRecipe_asksNothing() throws MishMashException {
        AddRecipeCommand command = new AddRecipeCommand(createRecipe());
        RecipeBook book = new RecipeBook();

        assertFalse(command.confirmationPrompt(book).isPresent());
        assertEquals(0, book.getRecipes().size());
    }

    @Test
    void executeWithResult_validRecipe_returnsDoneAndAddsExactlyOnce() {
        Recipe recipe = createRecipe();
        RecipeBook book = new RecipeBook();

        CommandResult result = new AddRecipeCommand(recipe).executeWithResult(book);

        assertEquals(CommandResult.Status.DONE, result.status());
        assertEquals(String.join(System.lineSeparator(),
                "New recipe added!", "Chicken and Rice",
                "650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g", "Ingredients:",
                "- Chicken Breast: 200g", "- White Rice: 150g", "- Olive Oil: 10ml",
                "Food tags: Unclassified"), result.feedback());
        assertEquals(List.of(recipe), book.getRecipes());
    }

    @Test
    void executeWithResult_formattingFailure_preservesBookAndDoesNotLogSuccess() {
        IllegalStateException failure = new IllegalStateException("Cannot format recipe");
        Recipe recipe = new Recipe("Broken", 0, 0, 0, 0, createRecipe().getIngredients()) {
            @Override
            public String getName() {
                throw failure;
            }
        };
        RecipeBook book = new RecipeBook();
        Recipe existing = createRecipe();
        book.add(existing);

        try (LogCapture logs = new LogCapture(AddRecipeCommand.class)) {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> new AddRecipeCommand(recipe).executeWithResult(book)));
            assertEquals(List.of(existing), book.getRecipes());
            assertTrue(logs.getRecords().isEmpty());
        }
    }

    @Test
    void executeWithResult_rejectedAddition_preservesBookAndDoesNotLogSuccess() {
        IllegalStateException failure = new IllegalStateException("Book unavailable");
        RecipeBook book = new RecipeBook() {
            @Override
            public void add(Recipe recipe) {
                throw failure;
            }
        };

        try (LogCapture logs = new LogCapture(AddRecipeCommand.class)) {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> new AddRecipeCommand(createRecipe()).executeWithResult(book)));
            assertTrue(book.getRecipes().isEmpty());
            assertTrue(logs.getRecords().isEmpty());
        }
    }
}
