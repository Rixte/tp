package mishmash.integration;

import static mishmash.testutil.CliAssertions.assertInOrder;
import static mishmash.testutil.CliAssertions.stripAnsi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import mishmash.MishMash;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.AddRecipeTestData;

/**
 * Exercises addition, recoverable validation errors and detailed listing through the command loop.
 */
class AddRecipeSessionTest {
    @TestFactory
    Stream<DynamicTest> run_rejectedAdditionThenRetry_preservesExistingRecipesOnByeAndEof() {
        return Stream.of("bye", "").map(ending -> DynamicTest.dynamicTest("exit: " + ending, () -> {
            Parser parser = new Parser();
            RecipeBook book = new RecipeBook();
            parser.parseCommand(AddRecipeTestData.VALID_COMMAND).execute(book);
            Recipe original = book.getRecipes().getFirst();
            String input = String.join(System.lineSeparator(), "Tester",
                    AddRecipeTestData.VALID_COMMAND.replace("n/Rice", "n/Rejected") + " food/milk,dairy",
                    "list-recipes",
                    "add-recipe n/Tofu cal/100 p/10 c/2 f/3 i/Tofu:25.50g food/soy",
                    "list-recipes 2", ending);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (Scanner scanner = new Scanner(input);
                    PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
                new MishMash(scanner, output, parser, book).run();
            }
            String transcript = stripAnsi(bytes.toString(StandardCharsets.UTF_8));
            String details = String.join(System.lineSeparator(), "Tofu",
                    "100 kcal | Protein: 10g | Carbs: 2g | Fats: 3g", "Ingredients:",
                    "- Tofu: 25.5g", "Food tags: soy");

            assertInOrder(transcript, "Error: Repeated food tag: milk.", "Format: add-recipe",
                    "Recipes:" + System.lineSeparator() + "1. Rice (200 kcal)",
                    "[DONE] New recipe added!" + System.lineSeparator() + details, details);
            assertEquals(1, transcript.lines().filter(line -> line.contains("New recipe added!")).count());
            assertFalse(transcript.contains("[CONFIRM]"));
            assertFalse(transcript.contains("Rejected"));
            assertEquals(!ending.isEmpty(), transcript.contains("Goodbye!"));
            assertEquals(2, book.size());
            assertSame(original, book.getRecipes().getFirst());
            assertEquals(List.of("Rice", "Tofu"), book.getRecipes().stream().map(Recipe::getName).toList());
        }));
    }
}
