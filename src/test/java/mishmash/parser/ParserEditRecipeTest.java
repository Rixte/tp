package mishmash.parser;

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

import mishmash.command.Command;
import mishmash.command.EditRecipeCommand;
import mishmash.command.HelpCommand;
import mishmash.exception.MishMashException;
import mishmash.model.FoodInfo;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

//@@author Rixte
/**
 * Checks edit syntax, shared recipe validation, and preservation of omitted fields.
 */
class ParserEditRecipeTest {
    private final Parser parser = new Parser();

    /**
     * Creates a classified recipe so omitted food information can be checked too.
     */
    private RecipeBook createBook() throws MishMashException {
        RecipeBook book = new RecipeBook();
        parser.parseCommand("add-recipe n/Rice cal/200 p/4 c/45 f/1 i/Rice:150g food/none").execute(book);
        return book;
    }

    @Test
    void parse_partialEdit_preservesOmittedFieldsAndDoesNotChangeBookUntilExecution() throws MishMashException {
        RecipeBook book = createBook();
        Recipe original = book.getRecipes().getFirst();

        Command command = parser.parseCommand("edit-recipe 1 p/12");

        assertInstanceOf(EditRecipeCommand.class, command);
        assertSame(original, book.getRecipes().getFirst());
        command.execute(book);
        Recipe edited = book.getRecipes().getFirst();
        assertEquals("Rice", edited.getName());
        assertEquals(200, edited.getCalories());
        assertEquals(12, edited.getProtein());
        assertEquals(45, edited.getCarbs());
        assertEquals(1, edited.getFats());
        assertEquals(original.getIngredients(), edited.getIngredients());
        assertEquals(original.getFoodInfo(), edited.getFoodInfo());
        assertEquals(4, original.getProtein());
    }

    @Test
    void parse_allFieldsInDifferentOrder_replacesEntireIngredientsAndFoodDeclaration() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 01 i/Chicken Breast:220.5g f/15 c/70 p/50 cal/650"
                + " n/Chicken Rice i/Water:0.25L food/poultry").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertEquals("Chicken Rice", edited.getName());
        assertEquals(650, edited.getCalories());
        assertEquals(50, edited.getProtein());
        assertEquals(70, edited.getCarbs());
        assertEquals(15, edited.getFats());
        assertEquals(List.of("Chicken Breast: 220.5g", "Water: 0.25L"),
                edited.getIngredients().stream().map(Object::toString).toList());
        assertEquals(FoodInfo.parseField("poultry"), edited.getFoodInfo());
    }

    @Test
    void parse_ingredientOnlyEdit_replacesOldItemsWhilePreservingNutritionAndFood() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 1 i/Brown Rice:175g").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertEquals(List.of("Brown Rice: 175g"),
                edited.getIngredients().stream().map(Object::toString).toList());
        assertEquals(200, edited.getCalories());
        assertEquals(FoodInfo.parseField("none"), edited.getFoodInfo());
    }

    @Test
    void parse_nameAndFoodOnlyEdits_preserveAllOtherFields() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("  edit-recipe\t1\tn/ Brown Rice  ").execute(book);
        parser.parseCommand("edit-recipe 1 food/dairy").execute(book);
        parser.parseCommand("edit-recipe 1 food/none").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertEquals("Brown Rice", edited.getName());
        assertEquals(200, edited.getCalories());
        assertEquals(4, edited.getProtein());
        assertEquals(45, edited.getCarbs());
        assertEquals(1, edited.getFats());
        assertEquals(FoodInfo.parseField("none"), edited.getFoodInfo());
        assertEquals("Rice", edited.getIngredients().getFirst().getName());
    }

    @Test
    void parse_nutritionBoundaries_acceptsZeroAndMaximumInteger() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 1 cal/0 p/2147483647 c/000 f/0").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertEquals(0, edited.getCalories());
        assertEquals(Integer.MAX_VALUE, edited.getProtein());
        assertEquals(0, edited.getCarbs());
        assertEquals(0, edited.getFats());
    }

    /**
     * Checks every nutrition prefix independently, including zero, leading zeros, and integer limits.
     */
    @TestFactory
    Stream<DynamicTest> parse_singleNutritionField_preservesAllOtherValues() {
        return Stream.of("cal", "p", "c", "f").flatMap(prefix ->
                Stream.of("0", "00042", "2147483647").map(value ->
                        DynamicTest.dynamicTest(prefix + "/" + value, () -> {
                            RecipeBook book = createBook();
                            Recipe original = book.getRecipes().getFirst();
                            parser.parseCommand("edit-recipe 1 " + prefix + "/" + value).execute(book);

                            Recipe edited = book.getRecipes().getFirst();
                            int expectedValue = Integer.parseInt(value);
                            assertEquals(prefix.equals("cal") ? expectedValue : 200, edited.getCalories());
                            assertEquals(prefix.equals("p") ? expectedValue : 4, edited.getProtein());
                            assertEquals(prefix.equals("c") ? expectedValue : 45, edited.getCarbs());
                            assertEquals(prefix.equals("f") ? expectedValue : 1, edited.getFats());
                            assertEquals("Rice", edited.getName());
                            assertEquals(original.getIngredients(), edited.getIngredients());
                            assertEquals(original.getFoodInfo(), edited.getFoodInfo());
                        })));
    }

    /**
     * Guards consistent numeric validation across all four optional nutrition fields.
     */
    @TestFactory
    Stream<DynamicTest> parse_invalidNutritionForEachPrefix_keepsRecipeUnchanged() {
        return Stream.of("cal", "p", "c", "f").flatMap(prefix ->
                Stream.of("-1", "+1", "1.5", "2147483648", "\u0661").map(value ->
                        DynamicTest.dynamicTest(prefix + "/" + value, () -> {
                            RecipeBook book = createBook();
                            Recipe original = book.getRecipes().getFirst();
                            MishMashException error = assertThrows(MishMashException.class,
                                    () -> parser.parseCommand("edit-recipe 1 " + prefix + "/" + value));

                            assertTrue(error.getMessage().endsWith(Parser.EDIT_RECIPE_USAGE), error.getMessage());
                            assertSame(original, book.getRecipes().getFirst());
                        })));
    }

    @Test
    void parse_multipleFoodTags_replacesOldDeclarationAndAcceptsAliases() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 1 food/MILK, eggs").execute(book);
        Recipe firstEdit = book.getRecipes().getFirst();
        assertEquals(FoodInfo.parseField("milk,egg"), firstEdit.getFoodInfo());

        parser.parseCommand("edit-recipe 1 food/SoYa, tree-nuts").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertEquals(FoodInfo.parseField("soy,tree-nut"), edited.getFoodInfo());
        assertEquals(FoodInfo.parseField("milk,egg"), firstEdit.getFoodInfo());
        assertEquals(200, edited.getCalories());
        assertEquals(firstEdit.getIngredients(), edited.getIngredients());
    }

    @Test
    void parse_ingredientsWithoutFood_doesNotInferClassification() throws MishMashException {
        RecipeBook book = new RecipeBook();
        parser.parseCommand("add-recipe n/Drink cal/100 p/5 c/10 f/3 i/Water:100ml").execute(book);

        parser.parseCommand("edit-recipe 1 i/Milk:200ml").execute(book);

        Recipe edited = book.getRecipes().getFirst();
        assertFalse(edited.getFoodInfo().isClassified());
        assertEquals(FoodInfo.createUnknown(), edited.getFoodInfo());
        assertEquals("Milk: 200ml", edited.getIngredients().getFirst().toString());
        parser.parseCommand("edit-recipe 1 food/milk").execute(book);
        assertEquals(FoodInfo.parseField("milk"), book.getRecipes().getFirst().getFoodInfo());
    }

    @Test
    void parse_repeatedIngredients_keepsEntriesOrderUnitsAndDecimalPrecision() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 1 i/Rice:100g i/Rice:50g i/rice:25g"
                + " i/Rice:1kg i/Oil:0.000000001ml").execute(book);

        assertEquals(List.of("Rice: 100g", "Rice: 50g", "rice: 25g", "Rice: 1kg", "Oil: 0.000000001ml"),
                book.getRecipes().getFirst().getIngredients().stream().map(Object::toString).toList());
        assertEquals(200, book.getRecipes().getFirst().getCalories());
    }

    @Test
    void parse_successiveIngredientEdits_replacesRatherThanAppends() throws MishMashException {
        RecipeBook book = createBook();
        parser.parseCommand("edit-recipe 1 i/Chicken:200g i/Water:100ml").execute(book);
        Recipe firstEdit = book.getRecipes().getFirst();

        parser.parseCommand("edit-recipe 1 i/Tofu:150g").execute(book);

        assertEquals(List.of("Tofu: 150g"), book.getRecipes().getFirst().getIngredients().stream()
                .map(Object::toString).toList());
        assertEquals(List.of("Chicken: 200g", "Water: 100ml"), firstEdit.getIngredients().stream()
                .map(Object::toString).toList());
    }

    /**
     * Rejects late errors after otherwise valid changes, including invalid food declarations.
     */
    @TestFactory
    Stream<DynamicTest> parse_lateInvalidFields_doesNotApplyEarlierFields() {
        return Stream.of("food/none,milk", "food/milk,", "food/,milk", "food/milk,dairy",
                "i/Valid:1g i/Invalid:0g", "P/30", "i/Rice:1e2g", "i/Rice:1g/2")
                .map(invalidFields -> DynamicTest.dynamicTest(invalidFields, () -> {
                    RecipeBook book = createBook();
                    Recipe original = book.getRecipes().getFirst();

                    MishMashException error = assertThrows(MishMashException.class,
                            () -> parser.parseCommand("edit-recipe 1 n/Changed cal/250 " + invalidFields));

                    assertTrue(error.getMessage().endsWith(Parser.EDIT_RECIPE_USAGE), error.getMessage());
                    assertEquals(List.of(original), book.getRecipes());
                    assertEquals("Rice", original.getName());
                    assertEquals(200, original.getCalories());
                }));
    }

    /**
     * Exercises malformed edits that must never publish a partially changed recipe.
     */
    @TestFactory
    Stream<DynamicTest> parse_invalidEdits_reportsEditUsageAndPreservesRecipe() {
        return Stream.of("", "1", "0 p/5", "-1 p/5", "+1 p/5", "1.0 p/5", "2147483648 p/5",
                "p/5", "1 2 p/5", "1 text p/5", "1 n/", "1 i/", "1 cal/", "1 p/", "1 c/", "1 f/",
                "1 food/", "1 n/A n/B", "1 cal/1 cal/2", "1 p/1 p/2", "1 c/1 c/2", "1 f/1 f/2",
                "1 food/none food/dairy", "1 x/5", "1 p/-1", "1 p/+1", "1 p/1.5", "1 p/5g",
                "1 p/2147483648", "1 cal/2147483648", "1 c/-1", "1 f/1.5", "1 i/Rice:0g",
                "1 i/Rice:-1g", "1 i/Rice:1", "1 i/:1g", "1 i/Rice:1g n/Updated p/bad",
                "1 p/5c/3", "1 n/Rice/Bowl", "1 food/unsupported", "1 food/dairy,dairy")
                .map(arguments -> DynamicTest.dynamicTest("edit-recipe " + arguments, () -> {
                    RecipeBook book = createBook();
                    Recipe original = book.getRecipes().getFirst();
                    MishMashException error = assertThrows(MishMashException.class,
                            () -> parser.parseCommand("edit-recipe " + arguments));
                    assertTrue(error.getMessage().endsWith(Parser.EDIT_RECIPE_USAGE), error.getMessage());
                    assertInstanceOf(IllegalArgumentException.class, error.getCause());
                    assertEquals(List.of(original), book.getRecipes());
                }));
    }

    @Test
    void helpAndGeneralUsage_includeEditing() {
        String help = new HelpCommand().execute(new RecipeBook());
        assertTrue(help.contains(Parser.EDIT_RECIPE_USAGE));
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.EDIT_RECIPE_USAGE));
    }
}
//@@author
