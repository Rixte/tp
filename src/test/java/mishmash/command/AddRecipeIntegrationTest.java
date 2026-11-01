package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.exception.MishMashException;
import mishmash.model.FoodInfo;
import mishmash.model.FoodTag;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.AddRecipeTestData;

/**
 * Checks addition across parsing, execution and listing without bypassing recipe validation.
 */
class AddRecipeIntegrationTest {
    @Test
    void parseAndExecute_reorderedFields_preservesEveryFieldAndExistingRecipe() throws MishMashException {
        Parser parser = new Parser();
        RecipeBook book = new RecipeBook();
        parser.parseCommand(AddRecipeTestData.VALID_COMMAND).execute(book);
        Recipe existing = book.getRecipes().getFirst();
        List<Recipe> snapshot = book.getRecipes();

        Command addition = parser.parseCommand("add-recipe i/White Rice:150.50g food/soy,milk f/3"
                + " c/45 n/Tofu Bowl p/12 cal/250 i/Water:20ml");

        assertEquals(snapshot, book.getRecipes());
        assertTrue(addition.confirmationPrompt(book).isEmpty());
        assertEquals(snapshot, book.getRecipes());
        CommandResult result = addition.executeWithResult(book);
        Recipe added = book.getRecipes().get(1);
        assertEquals(2, book.size());
        assertSame(existing, book.getRecipes().getFirst());
        assertEquals(List.of(existing), snapshot);
        assertEquals("Tofu Bowl", added.getName());
        assertEquals(250, added.getCalories());
        assertEquals(12, added.getProtein());
        assertEquals(45, added.getCarbs());
        assertEquals(3, added.getFats());
        assertEquals(new FoodInfo(true, Set.of(FoodTag.SOY, FoodTag.MILK)), added.getFoodInfo());
        assertEquals(List.of("White Rice", "Water"), added.getIngredients().stream()
                .map(GroceryItem::getName).toList());
        assertEquals(List.of(new BigDecimal("150.50"), new BigDecimal("20")),
                added.getIngredients().stream().map(GroceryItem::getQuantity).toList());
        assertEquals(List.of("g", "ml"), added.getIngredients().stream().map(GroceryItem::getUnit).toList());
        String details = String.join(System.lineSeparator(), "Tofu Bowl",
                "250 kcal | Protein: 12g | Carbs: 45g | Fats: 3g", "Ingredients:",
                "- White Rice: 150.5g", "- Water: 20ml", "Food tags: milk, soy");
        assertEquals(CommandResult.Status.DONE, result.status());
        assertEquals("New recipe added!" + System.lineSeparator() + details, result.feedback());
        assertEquals(details, parser.parseCommand("list-recipes 2").execute(book));
        assertEquals(List.of(existing, added), book.getRecipes());
    }

    @TestFactory
    Stream<DynamicTest> parseAndExecute_foodDeclarations_preservesClassificationAndDisplay() {
        return Stream.of("", " food/none", " food/soy,milk").map(field ->
                DynamicTest.dynamicTest("classification: " + field, () -> verifyClassification(field)));
    }

    /** Checks the distinction between omitted, explicitly empty and populated food declarations. */
    private void verifyClassification(String field) throws MishMashException {
        Parser parser = new Parser();
        RecipeBook book = new RecipeBook();
        String confirmation = parser.parseCommand(AddRecipeTestData.VALID_COMMAND + field).execute(book);
        Recipe recipe = book.getRecipes().getFirst();
        FoodInfo expected = field.isEmpty() ? FoodInfo.createUnknown()
                : new FoodInfo(true, field.equals(" food/none") ? Set.of() : Set.of(FoodTag.SOY, FoodTag.MILK));
        String label = field.isEmpty() ? "Unclassified" : field.equals(" food/none") ? "None declared" : "milk, soy";
        String details = String.join(System.lineSeparator(), "Rice",
                "200 kcal | Protein: 4g | Carbs: 45g | Fats: 1g", "Ingredients:",
                "- Rice: 150g", "Food tags: " + label);

        assertEquals(expected, recipe.getFoodInfo());
        assertEquals("New recipe added!" + System.lineSeparator() + details, confirmation);
        assertEquals(details, parser.parseCommand("list-recipes 1").execute(book));
        assertEquals(List.of(recipe), book.getRecipes());
    }

    @TestFactory
    Stream<DynamicTest> parseCommand_invalidRecipe_preservesBookAndAllowsRetry() {
        return Stream.of(" food/milk,dairy", " food/milk,", " food/none food/soy", " i/Water:0ml")
                .map(suffix -> DynamicTest.dynamicTest(suffix, () -> {
                    Parser parser = new Parser();
                    RecipeBook book = new RecipeBook();
                    parser.parseCommand(AddRecipeTestData.VALID_COMMAND).execute(book);
                    parser.parseCommand(AddRecipeTestData.VALID_COMMAND.replace("n/Rice", "n/Oats")).execute(book);
                    List<Recipe> original = book.getRecipes();

                    MishMashException error = assertThrows(MishMashException.class, () -> parser
                            .parseCommand(AddRecipeTestData.VALID_COMMAND.replace("n/Rice", "n/Rejected") + suffix));

                    assertEquals(Parser.ADD_RECIPE_USAGE, error.getUsage().orElseThrow());
                    assertEquals(original, book.getRecipes());
                    parser.parseCommand(AddRecipeTestData.VALID_COMMAND.replace("n/Rice", "n/Retry")).execute(book);
                    assertEquals(3, book.size());
                    assertSame(original.get(0), book.getRecipes().get(0));
                    assertSame(original.get(1), book.getRecipes().get(1));
                    assertEquals("Retry", book.getRecipes().get(2).getName());
                    assertEquals(String.join(System.lineSeparator(), "Recipes:",
                            "1. Rice (200 kcal)", "2. Oats (200 kcal)", "3. Retry (200 kcal)"),
                            parser.parseCommand("list-recipes").execute(book));
                }));
    }
}
