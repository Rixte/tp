package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.command.HelpCommand;
import mishmash.command.RecordMealCommand;
import mishmash.command.ViewMealsCommand;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.MealLog;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author GauthamNandi
/**
 * Checks record-meal and view-meals syntax, their error messages, and the meal log shared through the parser.
 */
class ParserRecordMealTest {
    private final Recipe chickenRice = TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15);
    private final Recipe oatmeal = TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8);
    private final MealLog log = new MealLog();
    private final Parser parser = new Parser(log);

    private RecipeBook bookOf(Recipe... recipes) {
        RecipeBook book = new RecipeBook();
        for (Recipe recipe : recipes) {
            book.add(recipe);
        }
        return book;
    }

    @Test
    void parseCommand_recordMealWithIndex_createsCommandWithoutChangingLog() throws MishMashException {
        assertInstanceOf(RecordMealCommand.class, parser.parseCommand("record-meal 2"));

        assertTrue(TestRecipes.recordedRecipes(log).isEmpty());
    }

    @Test
    void parseCommand_recordMealWithSurroundingWhitespace_isAccepted() throws MishMashException {
        assertInstanceOf(RecordMealCommand.class, parser.parseCommand(" \trecord-meal \t 3 \t "));
    }

    @Test
    void parseCommand_recordMealThenExecute_recordsTheChosenRecipeInTheSharedLog() throws MishMashException {
        RecipeBook book = bookOf(chickenRice, oatmeal);

        parser.parseCommand("record-meal 2").execute(book);

        assertEquals(List.of(oatmeal), TestRecipes.recordedRecipes(log));
    }

    @Test
    void parseCommand_recordMealThenViewMeals_showsTheRecordedMeal() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);

        parser.parseCommand("record-meal 1").execute(book);
        String view = parser.parseCommand("view-meals").execute(book);

        assertTrue(view.contains("Meal 1: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)"), view);
        assertTrue(view.contains("Total: 650 kcal | Protein: 50g | Carbs: 70g | Fats: 15g"), view);
    }

    @Test
    void parseCommand_defaultParser_keepsOneLogAcrossCommands() throws MishMashException {
        Parser defaultParser = new Parser();
        RecipeBook book = bookOf(chickenRice);

        defaultParser.parseCommand("record-meal 1").execute(book);
        defaultParser.parseCommand("record-meal 1").execute(book);

        String view = defaultParser.parseCommand("view-meals").execute(book);
        assertTrue(view.contains("Meal 2: Chicken and Rice"), view);
        assertTrue(view.contains("Total: 1300 kcal"), view);
    }

    @Test
    void parseCommand_separateParsers_doNotShareRecordedMeals() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);
        new Parser().parseCommand("record-meal 1").execute(book);

        assertEquals("No meals recorded yet.", new Parser().parseCommand("view-meals").execute(book));
    }

    @Test
    void parseCommand_otherConstructors_alsoSupportMealRecording() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);
        Parser withPlan = new Parser(new MealPlanGenerator(new Random(1)), new ActiveMealPlan());
        Parser withSources = new Parser(() -> List.of(), () -> java.util.Optional.empty());

        withPlan.parseCommand("record-meal 1").execute(book);
        withSources.parseCommand("record-meal 1").execute(book);

        assertTrue(withPlan.parseCommand("view-meals").execute(book).contains("Meal 1: Chicken and Rice"));
        assertTrue(withSources.parseCommand("view-meals").execute(book).contains("Meal 1: Chicken and Rice"));
    }

    @Test
    void parseCommand_recordMealWithInvalidIndex_rejectsWithSpecificMessageAndUsage() {
        String usage = System.lineSeparator() + Parser.RECORD_MEAL_USAGE;
        String[][] cases = {
            {"record-meal", "Recipe index is required."},
            {"record-meal    ", "Recipe index is required."},
            {"record-meal 0", "Recipe index must be at least 1."},
            {"record-meal 000", "Recipe index must be at least 1."},
            {"record-meal -1", "Recipe index must be a single positive whole number."},
            {"record-meal +1", "Recipe index must be a single positive whole number."},
            {"record-meal 1.5", "Recipe index must be a single positive whole number."},
            {"record-meal two", "Recipe index must be a single positive whole number."},
            {"record-meal 1 2", "Recipe index must be a single positive whole number."},
            {"record-meal n/Rice", "Recipe index is required."},
            {"record-meal 1 S/2", "Unknown field: S/"},
            {"record-meal 1/2", "Recipe index must be a single positive whole number."},
            {"record-meal 2147483648", "Recipe index must not exceed 2147483647."},
        };

        for (String[] testCase : cases) {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> parser.parseCommand(testCase[0]), testCase[0]);
            assertEquals(testCase[1] + usage, error.getMessage(), testCase[0]);
        }
        assertTrue(TestRecipes.recordedRecipes(log).isEmpty());
    }

    @Test
    void parseCommand_recordMealWithInvalidIndex_preservesTheUnderlyingCause() {
        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("record-meal abc"));

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void parseCommand_recordMealCommandWord_isCaseSensitive() {
        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("Record-Meal 1"));

        assertEquals("Unknown command: Record-Meal", error.getMessage());
    }

    @Test
    void parseCommand_largestIndex_parsesButIsRejectedWhenRun() throws MishMashException {
        var command = parser.parseCommand("record-meal 2147483647");

        assertThrows(MishMashException.class, () -> command.execute(bookOf(chickenRice)));
        assertTrue(TestRecipes.recordedRecipes(log).isEmpty());
    }

    @Test
    void parseCommand_viewMeals_createsCommandWithoutReadingLog() throws MishMashException {
        assertInstanceOf(ViewMealsCommand.class, parser.parseCommand("view-meals"));
        assertInstanceOf(ViewMealsCommand.class, parser.parseCommand(" \tview-meals \t "));
    }

    @Test
    void parseCommand_viewMealsWithArguments_rejectsWithUsage() {
        for (String arguments : List.of("1", "extra", "d/1", "-1")) {
            MishMashException error = assertThrows(MishMashException.class,
                    () -> parser.parseCommand("view-meals " + arguments), arguments);

            assertEquals("view-meals accepts only an optional date/YYYY-MM-DD."
                    + System.lineSeparator() + Parser.VIEW_MEALS_USAGE, error.getMessage(), arguments);
        }
    }

    @Test
    void parseCommand_viewMealsRejected_doesNotChangeRecordedMeals() throws MishMashException {
        RecipeBook book = bookOf(chickenRice);
        parser.parseCommand("record-meal 1").execute(book);

        assertThrows(MishMashException.class, () -> parser.parseCommand("view-meals now"));

        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
    }

    @Test
    void parseCommand_recordedMeals_areSeparateFromTheActivePlan() throws MishMashException {
        ActiveMealPlan activePlan = new ActiveMealPlan();
        Parser sharedPlanParser = new Parser(log, new MealPlanGenerator(new Random(1)), activePlan);

        sharedPlanParser.parseCommand("record-meal 1").execute(bookOf(chickenRice));

        assertTrue(activePlan.get().isEmpty());
        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
    }

    @Test
    void constructor_nullDependencies_throwNullPointerException() {
        assertThrows(NullPointerException.class, () -> new Parser((MealLog) null));
        assertThrows(NullPointerException.class,
                () -> new Parser(null, new MealPlanGenerator(new Random(1)), new ActiveMealPlan()));
        assertThrows(NullPointerException.class,
                () -> new Parser(new MealLog(), new MealPlanGenerator(new Random(1)), null));
    }

    @Test
    void usage_isListedInTheGeneralCommandUsage() {
        assertEquals("Format: record-meal INDEX [date/YYYY-MM-DD] [s/SERVINGS]", Parser.RECORD_MEAL_USAGE);
        assertEquals("Format: view-meals [date/YYYY-MM-DD]", Parser.VIEW_MEALS_USAGE);
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.RECORD_MEAL_USAGE));
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.VIEW_MEALS_USAGE));
    }

    @Test
    void help_listsBothMealCommandsWithTheirFormats() throws MishMashException {
        var command = parser.parseCommand("help");
        String help = command.execute(new RecipeBook());

        assertInstanceOf(HelpCommand.class, command);
        assertTrue(help.contains("record-meal - Record a recipe you have eaten"), help);
        assertTrue(help.contains(Parser.RECORD_MEAL_USAGE), help);
        assertTrue(help.contains("view-meals - View the meals you have recorded"), help);
        assertTrue(help.contains(Parser.VIEW_MEALS_USAGE), help);
    }

    @Test
    void parseCommand_mealLogConstructor_stillWarnsWhenPlanExceedsTargets() throws MishMashException {
        Parser sharedPlanParser = new Parser(log, new MealPlanGenerator(new Random(1)), new ActiveMealPlan());
        RecipeBook recipes = bookOf(TestRecipes.recipe("A", 500, 20, 50, 10),
                TestRecipes.recipe("B", 600, 30, 60, 15), TestRecipes.recipe("C", 700, 40, 70, 20));

        sharedPlanParser.parseCommand("generate-plan d/1 cal/1700 p/85 c/180 f/45").execute(recipes);
        String display = sharedPlanParser.parseCommand("view-plan").execute(recipes);

        assertTrue(display.contains("Warning: Calories exceed target by 100 kcal"
                + " (1800 kcal actual; 1700 kcal target)."), display);
        assertTrue(display.contains("Warning: Protein exceeds target by 5 g (90 g actual; 85 g target)."), display);
    }

    @Test
    void parseCommand_recordMealAfterGeneratePlan_leavesPlanAndShortfallUnchanged() throws MishMashException {
        Parser sharedPlanParser = new Parser(log, new MealPlanGenerator(new Random(1)), new ActiveMealPlan());
        RecipeBook recipes = bookOf(chickenRice, oatmeal, TestRecipes.recipe("Salad", 200, 5, 20, 8));
        sharedPlanParser.parseCommand("generate-plan d/1").execute(recipes);
        String planBefore = sharedPlanParser.parseCommand("view-plan").execute(recipes);
        String shortfallBefore = sharedPlanParser.parseCommand("check-shortfall cal/5000").execute(recipes);

        sharedPlanParser.parseCommand("record-meal 1").execute(recipes);

        assertEquals(planBefore, sharedPlanParser.parseCommand("view-plan").execute(recipes));
        assertEquals(shortfallBefore, sharedPlanParser.parseCommand("check-shortfall cal/5000").execute(recipes));
        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
    }

    @Test
    void parseCommand_generatePlanAfterRecording_keepsTheRecordedMeals() throws MishMashException {
        Parser sharedPlanParser = new Parser(log, new MealPlanGenerator(new Random(1)), new ActiveMealPlan());
        RecipeBook recipes = bookOf(chickenRice, oatmeal, TestRecipes.recipe("Salad", 200, 5, 20, 8));
        sharedPlanParser.parseCommand("record-meal 2").execute(recipes);

        sharedPlanParser.parseCommand("generate-plan d/3").execute(recipes);

        assertEquals(List.of(oatmeal), TestRecipes.recordedRecipes(log));
    }

    @Test
    void parseCommand_editRecipeAfterRecording_keepsTheNutritionThatWasRecorded() throws MishMashException {
        RecipeBook recipes = bookOf(chickenRice);
        parser.parseCommand("record-meal 1").execute(recipes);

        parser.parseCommand("edit-recipe 1 cal/100").execute(recipes);
        String view = parser.parseCommand("view-meals").execute(recipes);

        assertEquals(100, recipes.getRecipes().get(0).getCalories());
        assertTrue(view.contains("Chicken and Rice (650 kcal"), view);
        assertFalse(view.contains("100 kcal"), view);

        // A meal recorded after the edit uses the edited nutrition, and totals add both entries.
        String second = parser.parseCommand("record-meal 1").execute(recipes);
        assertTrue(second.contains("Total for " + LocalDate.now() + ": 750 kcal"), second);
    }

    @Test
    void parseCommand_deleteRecipeAfterRecording_keepsMealButCannotRecordAgain() throws MishMashException {
        RecipeBook recipes = bookOf(chickenRice);
        parser.parseCommand("record-meal 1").execute(recipes);

        parser.parseCommand("delete-recipe 1").execute(recipes);

        assertTrue(parser.parseCommand("view-meals").execute(recipes).contains("Meal 1: Chicken and Rice"));
        assertThrows(MishMashException.class, () -> parser.parseCommand("record-meal 1").execute(recipes));
        assertEquals(List.of(chickenRice), TestRecipes.recordedRecipes(log));
    }

    //@@author GauthamNandi
    @Test
    void parseCommand_fixedClock_decidesTodayAndWhatCountsAsTheFuture() throws MishMashException {
        Clock clock = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);
        Parser fixedParser = new Parser(log, clock);
        RecipeBook recipes = new RecipeBook();
        recipes.add(oatmeal);

        fixedParser.parseCommand("record-meal 1").execute(recipes);

        assertEquals(LocalDate.of(2026, 3, 15), log.getEntries().getFirst().date());
        MishMashException e = assertThrows(MishMashException.class,
                () -> fixedParser.parseCommand("record-meal 1 date/2026-03-16"));
        assertEquals("Date must not be in the future.", e.getExplanation());
    }
    //@@author
}
//@@author
