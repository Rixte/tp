package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import mishmash.command.Command;
import mishmash.command.ListRecipeCommand;
import mishmash.command.ViewListCommand;
import mishmash.command.ViewPlanCommand;
import mishmash.exception.MishMashException;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.ui.MealPlanViewer.MealSummary;

/**
 * Checks recipe and viewing-command parsing, including rejection of invalid arguments.
 */
class ParserTest {
    private static final String VALID_COMMAND =
            "add-recipe n/Chicken and Rice cal/650 p/50 c/70 f/15"
            + " i/Chicken Breast:200g i/White Rice:150g";

    private final Parser parser = new Parser();

    @Test
    void parseCommand_invalidArguments_preservesCauseAndUsage() {
        String[][] invalidCommands = {
            {"generate-plan d/0", Parser.GENERATE_PLAN_USAGE},
            {"delete-recipe 0", Parser.DELETE_RECIPE_USAGE},
            {"list-recipes 0", Parser.LIST_RECIPE_USAGE}
        };

        for (String[] input : invalidCommands) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(input[0]), input[0]);

            IllegalArgumentException cause = assertInstanceOf(
                    IllegalArgumentException.class, exception.getCause(), input[0]);
            assertTrue(exception.getMessage().contains(cause.getMessage()), input[0]);
            assertTrue(exception.getMessage().endsWith(input[1]), input[0]);
        }
    }

    //@@author peilingggg
    @Test
    void parseCommand_validInput_preservesRecipeFields() throws MishMashException {
        RecipeBook recipeBook = new RecipeBook();

        parser.parseCommand(VALID_COMMAND).execute(recipeBook);

        assertEquals(1, recipeBook.getRecipes().size());
        Recipe recipe = recipeBook.getRecipes().get(0);
        assertEquals("Chicken and Rice", recipe.getName());
        assertEquals(650, recipe.getCalories());
        assertEquals(50, recipe.getProtein());
        assertEquals(70, recipe.getCarbs());
        assertEquals(15, recipe.getFats());
        assertEquals(2, recipe.getIngredients().size());
        assertEquals("Chicken Breast", recipe.getIngredients().get(0).getName());
        assertEquals(new BigDecimal("200"), recipe.getIngredients().get(0).getQuantity());
        assertEquals("g", recipe.getIngredients().get(0).getUnit());
    }

    @Test
    void parseCommand_reorderedFields_acceptsDecimalIngredient() throws MishMashException {
        RecipeBook recipeBook = new RecipeBook();

        parser.parseCommand("add-recipe i/Olive Oil:2.5ml f/2 c/0 p/0 cal/20 n/Oil")
                .execute(recipeBook);

        assertEquals("Oil", recipeBook.getRecipes().get(0).getName());
        assertEquals(new BigDecimal("2.5"),
                recipeBook.getRecipes().get(0).getIngredients().get(0).getQuantity());
    }
    //@@author

    @Test
    void parseCommand_missingField_throwsMishMashException() {
        String[] requiredFields = {
            "n/Chicken and Rice",
            "cal/650",
            "p/50",
            "c/70",
            "f/15"
        };

        for (String field : requiredFields) {
            assertThrows(MishMashException.class,
                    () -> parser.parseCommand(VALID_COMMAND.replace(field, "")));
        }

        assertThrows(MishMashException.class,
                () -> parser.parseCommand("add-recipe n/Rice cal/200 p/4 c/45 f/1"));
    }

    @Test
    void parseCommand_invalidValues_throwsMishMashException() {
        String[] invalidCommands = {
            VALID_COMMAND.replace("cal/650", "cal/abc"),
            VALID_COMMAND.replace("p/50", "p/-1"),
            VALID_COMMAND.replace("c/70", "c/1.5"),
            VALID_COMMAND.replace("f/15", "f/2147483648"),
            VALID_COMMAND.replace("n/Chicken and Rice", "n/ "),
            VALID_COMMAND + " cal/100",
            VALID_COMMAND + " x/unknown",
            VALID_COMMAND + " i/Rice",
            VALID_COMMAND + " i/Rice:10",
            VALID_COMMAND + " i/Rice:0g",
            VALID_COMMAND + " i/Rice:-10g"
        };

        for (String input : invalidCommands) {
            assertThrows(MishMashException.class, () -> parser.parseCommand(input));
        }
    }

    @Test
    void parseCommand_invalidRecipe_includesUsage() {
        MishMashException exception = assertThrows(MishMashException.class,
                () -> parser.parseCommand("add-recipe"));

        assertTrue(exception.getMessage().contains(Parser.ADD_RECIPE_USAGE));
    }

    //@@author peilingggg
    @Test
    void parseCommand_invalidBetweenValid_preservesRecipeBook() throws MishMashException {
        RecipeBook recipeBook = new RecipeBook();
        parser.parseCommand(VALID_COMMAND).execute(recipeBook);

        assertThrows(MishMashException.class,
                () -> parser.parseCommand(VALID_COMMAND + " i/Broken").execute(recipeBook));

        assertEquals(1, recipeBook.getRecipes().size());

        parser.parseCommand(VALID_COMMAND.replace("Chicken and Rice", "Second recipe"))
                .execute(recipeBook);

        assertEquals(2, recipeBook.getRecipes().size());
        assertEquals("Chicken and Rice", recipeBook.getRecipes().get(0).getName());
        assertEquals("Second recipe", recipeBook.getRecipes().get(1).getName());
    }
    //@@author

    @Test
    void parseCommand_emptyOrUnknown_throwsMishMashException() {
        assertThrows(MishMashException.class, () -> parser.parseCommand(""));
        assertThrows(MishMashException.class, () -> parser.parseCommand(null));
        assertThrows(MishMashException.class, () -> parser.parseCommand("unknown"));
    }

    @Test
    void parseCommand_viewingCommands_createsCorrectCommandTypes() throws MishMashException {
        assertInstanceOf(ViewPlanCommand.class, parser.parseCommand("view-plan"));
        assertInstanceOf(ViewPlanCommand.class, parser.parseCommand("view-plan d/2"));
        assertInstanceOf(ViewListCommand.class, parser.parseCommand("view-list"));
    }

    @Test
    void parseCommand_viewPlanWhitespace_preservesSelectedDay() throws MishMashException {
        Parser connectedParser = new Parser(() -> List.of(
                List.of(new MealSummary("Rice", 200, 4, 45, 1)),
                List.of(new MealSummary("Beans", 300, 20, 40, 5))), () -> Optional.empty());

        String display = connectedParser.parseCommand(" \tview-plan\t d/ 002 \t").execute(new RecipeBook());

        assertTrue(display.startsWith("================ Day 2 ================"));
        assertTrue(display.contains("Meal 1: Beans"));
        assertFalse(display.contains("Rice"));
    }

    @Test
    void parseCommand_invalidViewPlanArguments_includesUsage() {
        String[] invalidArguments = {"2", "d/", "d/0", "d/-1", "d/+1", "d/1.5", "d/abc",
            "d/2147483648", "d/1 d/2", "d/1 extra", "x/1", "D/1", "d/1 cal/200"};
        for (String arguments : invalidArguments) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand("view-plan " + arguments), arguments);
            assertTrue(exception.getMessage().contains(Parser.VIEW_PLAN_USAGE), arguments);
        }
    }

    @Test
    void parseCommand_invalidNumericDay_preservesCauseForDiagnostics() {
        MishMashException overflow = assertThrows(MishMashException.class,
                () -> parser.parseCommand("view-plan d/2147483648"));
        assertInstanceOf(NumberFormatException.class, overflow.getCause());

        MishMashException nonPositiveDay = assertThrows(MishMashException.class,
                () -> parser.parseCommand("view-plan d/0"));
        assertInstanceOf(IllegalArgumentException.class, nonPositiveDay.getCause());
    }

    @Test
    void parseCommand_viewListArguments_includesUsage() {
        for (String arguments : List.of("extra", "d/1", "i/Rice:200g")) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand("view-list " + arguments));
            assertTrue(exception.getMessage().contains(Parser.VIEW_LIST_USAGE));
        }
    }

    @Test
    void parseCommand_viewingCommands_readsDataOnlyWhenExecuted() throws MishMashException {
        AtomicInteger sourceReadCount = new AtomicInteger();
        Parser connectedParser = new Parser(() -> {
            sourceReadCount.incrementAndGet();
            return List.of();
        }, () -> {
            sourceReadCount.incrementAndGet();
            return Optional.of(List.of());
        });

        Command allDaysCommand = connectedParser.parseCommand("view-plan");
        Command selectedDayCommand = connectedParser.parseCommand("view-plan d/1");
        Command groceryCommand = connectedParser.parseCommand(" \tview-list \t");
        assertThrows(MishMashException.class, () -> connectedParser.parseCommand("view-plan d/0"));
        assertThrows(MishMashException.class, () -> connectedParser.parseCommand("view-list extra"));
        assertEquals(0, sourceReadCount.get());

        allDaysCommand.execute(new RecipeBook());
        selectedDayCommand.execute(new RecipeBook());
        assertTrue(groceryCommand.execute(new RecipeBook()).endsWith("Total items to purchase: 0"));
        assertEquals(3, sourceReadCount.get());
    }

    @Test
    void parseCommand_largeDay_checksActivePlanDuringExecution() throws MishMashException {
        Parser connectedParser = new Parser(() -> List.of(List.of(new MealSummary("Rice", 200, 4, 45, 1))),
                () -> Optional.empty());
        Command command = connectedParser.parseCommand("view-plan d/2147483647");

        MishMashException exception = assertThrows(MishMashException.class,
                () -> command.execute(new RecipeBook()));

        assertTrue(exception.getMessage().contains("Day number must be between 1 and 1."));
    }

    @Test
    void constructor_nullDataSource_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Parser(null, () -> Optional.empty()));
        assertThrows(NullPointerException.class, () -> new Parser(() -> List.of(), null));
    }

    @Test
    void parseCommand_validDeleteIndex_deletesThatRecipeWhenExecuted() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        String result = parser.parseCommand("delete-recipe 2").execute(book);

        assertEquals("Recipe deleted!" + System.lineSeparator() + "Second recipe"
                + System.lineSeparator() + "Recipes remaining: 1", result);
        assertEquals(1, book.getRecipes().size());
        assertEquals("Chicken and Rice", book.getRecipes().get(0).getName());
    }

    @Test
    void parseCommand_deleteIndex_asksForConfirmationNamingTheRecipe() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        Command command = parser.parseCommand("delete-recipe 1");

        assertEquals("Are you sure you want to delete recipe 1: Chicken and Rice? (y/n)",
                command.confirmationPrompt(book).orElseThrow());
        assertEquals(2, book.getRecipes().size());
    }

    @Test
    void parseCommand_deleteWithExtraWhitespaceOrLeadingZeros_acceptsIndex() throws MishMashException {
        String[] inputs = {"  delete-recipe \t 1  ", "delete-recipe 0001", "delete-recipe    1"};

        for (String input : inputs) {
            RecipeBook book = createBookWithTwoRecipes();

            parser.parseCommand(input).execute(book);

            assertEquals(1, book.getRecipes().size(), input);
            assertEquals("Second recipe", book.getRecipes().get(0).getName(), input);
        }
    }

    @Test
    void parseCommand_invalidDeleteArguments_throwsWithDeleteUsageOnly() {
        String[][] invalidCases = {
                {"delete-recipe", "Recipe index is required."},
                {"delete-recipe 0", "Recipe index must be at least 1."},
                {"delete-recipe 000", "Recipe index must be at least 1."},
                {"delete-recipe -1", "Recipe index must be a single positive whole number."},
                {"delete-recipe +1", "Recipe index must be a single positive whole number."},
                {"delete-recipe 1.5", "Recipe index must be a single positive whole number."},
                {"delete-recipe abc", "Recipe index must be a single positive whole number."},
                {"delete-recipe 1 2", "Recipe index must be a single positive whole number."},
                {"delete-recipe 1,2", "Recipe index must be a single positive whole number."},
                {"delete-recipe n/Rice", "Recipe index must be a single positive whole number."},
                {"delete-recipe 2147483648", "Recipe index must not exceed 2147483647."}
        };

        for (String[] invalidCase : invalidCases) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(invalidCase[0]), invalidCase[0]);

            String message = exception.getMessage();
            assertTrue(message.contains(invalidCase[1]), message);
            assertTrue(message.contains(Parser.DELETE_RECIPE_USAGE), message);
            assertFalse(message.contains("Format: add-recipe"), message);
        }
    }

    @Test
    void parseCommand_largestIntIndex_parsesAndFailsOnlyWhenExecuted() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        Command command = parser.parseCommand("delete-recipe 2147483647");

        MishMashException exception = assertThrows(MishMashException.class,
                () -> command.execute(book));
        assertEquals("Recipe index must be between 1 and 2.", exception.getExplanation());
        assertEquals(2, book.getRecipes().size());
    }

    @Test
    void parseCommand_deleteLookalikes_areUnknownCommands() {
        String[] inputs = {"delete-recipes", "Delete-Recipe 1", "delete 1"};

        for (String input : inputs) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(input), input);

            assertTrue(exception.getMessage().startsWith("Unknown command: "), input);
        }
    }

    @Test
    void commandUsage_listsDeleteRecipeUsage() {
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.ADD_RECIPE_USAGE));
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.DELETE_RECIPE_USAGE));
    }

    @Test
    void parseCommand_invalidAddRecipe_doesNotShowDeleteUsage() {
        MishMashException exception = assertThrows(MishMashException.class,
                () -> parser.parseCommand("add-recipe"));

        assertTrue(exception.getMessage().contains(Parser.ADD_RECIPE_USAGE));
        assertFalse(exception.getMessage().contains(Parser.DELETE_RECIPE_USAGE));
    }

    @Test
    void parseCommand_addRecipe_needsNoConfirmation() throws MishMashException {
        RecipeBook book = new RecipeBook();

        assertFalse(parser.parseCommand(VALID_COMMAND).confirmationPrompt(book).isPresent());
    }

    @Test
    void parseCommand_listRecipeWithoutIndex_listsAllRecipes() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        String result = parser.parseCommand("list-recipes").execute(book);

        assertEquals("Recipes:" + System.lineSeparator()
                + "1. Chicken and Rice (650 kcal)" + System.lineSeparator()
                + "2. Second recipe (650 kcal)", result);
        assertEquals(2, book.getRecipes().size());
    }

    @Test
    void parseCommand_listRecipeWithIndex_showsThatRecipeInFull() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        String result = parser.parseCommand("list-recipes 2").execute(book);

        assertEquals("Second recipe" + System.lineSeparator()
                + "650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g" + System.lineSeparator()
                + "Ingredients:" + System.lineSeparator()
                + "- Chicken Breast: 200g" + System.lineSeparator()
                + "- White Rice: 150g" + System.lineSeparator()
                + "Food tags: Unclassified", result);
    }

    @Test
    void parseCommand_listRecipeVariations_areAccepted() throws MishMashException {
        String[] inputs = {"  list-recipes \t 1  ", "list-recipes 0001", "list-recipes    1"};

        for (String input : inputs) {
            String result = parser.parseCommand(input).execute(createBookWithTwoRecipes());

            assertTrue(result.startsWith("Chicken and Rice" + System.lineSeparator()), input);
        }
        assertInstanceOf(ListRecipeCommand.class, parser.parseCommand("list-recipes"));
        assertInstanceOf(ListRecipeCommand.class, parser.parseCommand("  list-recipes   "));
    }

    @Test
    void parseCommand_invalidListRecipeArguments_throwsWithListUsageOnly() {
        String[][] invalidCases = {
                {"list-recipes 0", "Recipe index must be at least 1."},
                {"list-recipes -1", "Recipe index must be a single positive whole number."},
                {"list-recipes 1.5", "Recipe index must be a single positive whole number."},
                {"list-recipes abc", "Recipe index must be a single positive whole number."},
                {"list-recipes 1 2", "Recipe index must be a single positive whole number."},
                {"list-recipes n/Rice", "Recipe index must be a single positive whole number."},
                {"list-recipes 2147483648", "Recipe index must not exceed 2147483647."}
        };

        for (String[] invalidCase : invalidCases) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(invalidCase[0]), invalidCase[0]);

            String message = exception.getMessage();
            assertTrue(message.contains(invalidCase[1]), message);
            assertTrue(message.contains(Parser.LIST_RECIPE_USAGE), message);
            assertFalse(message.contains("Format: add-recipe"), message);
            assertFalse(message.contains("Format: delete-recipe"), message);
        }
    }

    @Test
    void parseCommand_listRecipeLargestIndex_parsesAndFailsOnlyWhenExecuted() throws MishMashException {
        RecipeBook book = createBookWithTwoRecipes();

        Command command = parser.parseCommand("list-recipes 2147483647");

        MishMashException exception = assertThrows(MishMashException.class,
                () -> command.execute(book));
        assertEquals("Recipe index must be between 1 and 2.", exception.getExplanation());
    }

    @Test
    void parseCommand_listRecipeLookalikes_areUnknownCommands() {
        String[] inputs = {"list", "list-recipe", "list-recipess", "List-Recipes", "list-recipes1"};

        for (String input : inputs) {
            MishMashException exception = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(input), input);

            assertTrue(exception.getMessage().startsWith("Unknown command: "), input);
        }
    }

    @Test
    void commandUsage_listsListRecipeUsage() {
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.LIST_RECIPE_USAGE));
    }

    /**
     * Creates a book with "Chicken and Rice" at index 1 and "Second recipe" at index 2.
     */
    private RecipeBook createBookWithTwoRecipes() throws MishMashException {
        RecipeBook book = new RecipeBook();
        parser.parseCommand(VALID_COMMAND).execute(book);
        parser.parseCommand(VALID_COMMAND.replace("Chicken and Rice", "Second recipe")).execute(book);
        return book;
    }
}
