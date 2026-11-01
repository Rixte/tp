package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.exception.MishMashException;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.model.RecipeChanges;
import mishmash.parser.Parser;
import mishmash.testutil.AssertionDisabledClassLoader;
import mishmash.testutil.LogCapture;
import mishmash.testutil.TestRecipes;

/**
 * Checks recipe-editing logs, parser exception causes, and failures without false success messages.
 */
class EditRecipeDiagnosticsTest {
    //@@author Rixte
    @Test
    void execute_assertionsDisabled_replacesRecipeWithoutChangingCount() throws Exception {
        RecipeBook book = TestRecipes.recipeBook(100, 1, 1, 1);
        RecipeChanges changes = new RecipeChanges("Updated", null, null, null, null, List.of(), null);
        try (AssertionDisabledClassLoader loader = new AssertionDisabledClassLoader(EditRecipeCommand.class)) {
            Class<?> commandClass = loader.loadClass(EditRecipeCommand.class.getName());
            Command command = (Command) commandClass.getConstructor(int.class, RecipeChanges.class)
                    .newInstance(1, changes);

            assertFalse(commandClass.desiredAssertionStatus());
            assertTrue(command.execute(book).contains("Recipe updated!"));
            assertEquals("Updated", book.getRecipes().getFirst().getName());
            assertEquals(3, book.size());
        }
    }
    //@@author

    //@@author Rixte
    @Test
    void execute_storageAddsInsteadOfReplacing_detectsBrokenRecipeCountWithoutSuccessLog() {
        // Simulate a faulty storage implementation to exercise the command's invariant check.
        RecipeBook book = new RecipeBook() {
            @Override
            public void replace(int index, Recipe recipe) {
                add(recipe);
            }
        };
        book.add(TestRecipes.recipe("Original", 100, 1, 1, 1));
        RecipeChanges changes = new RecipeChanges("New", null, null, null, null, List.of(), null);
        try (LogCapture logs = new LogCapture(EditRecipeCommand.class)) {
            AssertionError error = assertThrows(AssertionError.class,
                    () -> new EditRecipeCommand(1, changes).execute(book));

            assertEquals("Editing must preserve the recipe count.", error.getMessage());
            assertFalse(logs.getMessages().contains("Recipe edited"));
        }
    }
    //@@author

    @Test
    void execute_failedEdits_logReasonsWithoutSuccessThenRecover() throws MishMashException {
        RecipeBook book = new RecipeBook();
        RecipeChanges changes = new RecipeChanges("Private replacement", null, null, null, null, List.of(), null);
        try (LogCapture logs = new LogCapture(EditRecipeCommand.class)) {
            assertThrows(MishMashException.class, () -> new EditRecipeCommand(2, changes).execute(book));
            book.add(TestRecipes.recipe("Private original", 100, 1, 1, 1));
            assertThrows(MishMashException.class, () -> new EditRecipeCommand(2, changes).execute(book));

            assertTrue(logs.getMessages().contains("recipe book is empty"));
            assertTrue(logs.getMessages().contains("index 2; recipe count: 1"));
            assertFalse(logs.getMessages().contains("Recipe edited"));
            new EditRecipeCommand(1, changes).execute(book);
            assertEquals(1, logs.getRecords().stream().filter(record -> record.getMessage()
                    .contains("Recipe edited at index 1")).count());
            assertFalse(logs.getMessages().contains("Private"));
            assertEquals(1, book.size());
        }
    }

    @Test
    void execute_replacementFailure_propagatesOriginalExceptionWithoutSuccessLog() {
        IllegalStateException failure = new IllegalStateException("Storage failed");
        RecipeBook book = new RecipeBook() {
            @Override
            public void replace(int index, Recipe recipe) {
                throw failure;
            }
        };
        book.add(TestRecipes.recipe("Original", 100, 1, 1, 1));
        List<Recipe> originalRecipes = book.getRecipes();
        RecipeChanges changes = new RecipeChanges("New", null, null, null, null, List.of(), null);
        try (LogCapture logs = new LogCapture(EditRecipeCommand.class)) {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> new EditRecipeCommand(1, changes).execute(book)));
            assertEquals(originalRecipes, book.getRecipes());
            assertTrue(logs.getRecords().isEmpty());
        }
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_overflowingEdits_preservesNumericCauseAndEditUsage() {
        return Stream.of("cal", "p", "c", "f").map(prefix -> DynamicTest.dynamicTest(prefix, () -> {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> new Parser().parseCommand("edit-recipe 1 " + prefix + "/2147483648"));

            assertTrue(error.getMessage().endsWith(Parser.EDIT_RECIPE_USAGE));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertInstanceOf(NumberFormatException.class, error.getCause().getCause());
        }));
    }

    @Test
    void parseCommand_rejectedFields_logsCommandWithoutRawInputAndRecovers() throws MishMashException {
        Parser parser = new Parser();
        try (LogCapture logs = new LogCapture(Parser.class)) {
            assertThrows(MishMashException.class,
                    () -> parser.parseCommand("edit-recipe 1 n/Private Recipe cal/invalid"));

            assertTrue(logs.getMessages().contains("edit-recipe rejected"));
            assertFalse(logs.getMessages().contains("Private"));
            assertFalse(logs.getMessages().contains("Parsed "));
            parser.parseCommand("edit-recipe 1 p/1");
            assertTrue(logs.getMessages().contains("Parsed edit-recipe for index 1"));
        }
    }
}
