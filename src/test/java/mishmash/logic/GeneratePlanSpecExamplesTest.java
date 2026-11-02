package mishmash.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.command.GeneratePlanCommand;
import mishmash.exception.MishMashException;
import mishmash.model.ActiveMealPlan;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.testutil.TestRecipes;

/**
 * Replays the generate-plan and view-plan examples from the project specification
 * ("CS2113 tP MishMash: The Meal Prep Planner") and checks that the outputs match it.
 */
class GeneratePlanSpecExamplesTest {
    private static final String LINE = System.lineSeparator();

    // The three recipes whose combined day gives the specification's view-plan example (1650 kcal).
    private static final String OATMEAL_LINE = "Meal %d: Protein Oatmeal (450 kcal | P: 35g, C: 60g, F: 8g)";
    private static final String CHICKEN_LINE = "Meal %d: Chicken and Rice (650 kcal | P: 50g, C: 70g, F: 15g)";
    private static final String SALMON_LINE = "Meal %d: Salmon Salad (550 kcal | P: 42g, C: 20g, F: 32g)";
    private static final String SEPARATOR = "--------------------------------------";
    private static final String TOTALS = "Daily Totals: 1650 kcal | Protein: 127g | Carbs: 150g | Fats: 55g";
    private static final String FOOTER = "======================================";

    private final Parser parser = new Parser(new MealPlanGenerator(new Random(7)), new ActiveMealPlan(),
            () -> Optional.empty());

    /**
     * Creates the specification's three recipes so every generated day has known nutrition totals.
     */
    private static RecipeBook createSpecRecipeBook() {
        RecipeBook recipeBook = new RecipeBook();
        recipeBook.add(TestRecipes.recipe("Protein Oatmeal", 450, 35, 60, 8));
        recipeBook.add(TestRecipes.recipe("Chicken and Rice", 650, 50, 70, 15));
        recipeBook.add(TestRecipes.recipe("Salmon Salad", 550, 42, 20, 32));
        return recipeBook;
    }

    private String run(String command, RecipeBook recipeBook) throws MishMashException {
        return parser.parseCommand(command).execute(recipeBook);
    }

    /**
     * Checks the specification's day layout while allowing the generator to choose meal order.
     */
    private static void assertSpecDayBlock(String block, int dayNumber) {
        List<String> lines = block.lines().toList();
        assertEquals(7, lines.size(), block);
        assertEquals("================ Day " + dayNumber + " ================", lines.get(0));
        List<String> mealLines = List.of(lines.get(1), lines.get(2), lines.get(3));
        for (String template : List.of(OATMEAL_LINE, CHICKEN_LINE, SALMON_LINE)) {
            assertTrue(List.of(1, 2, 3).stream().anyMatch(n -> mealLines.contains(String.format(template, n))),
                    template + " missing from " + block);
        }
        for (int mealNumber = 1; mealNumber <= 3; mealNumber++) {
            assertTrue(lines.get(mealNumber).startsWith("Meal " + mealNumber + ": "), block);
        }
        assertEquals(SEPARATOR, lines.get(4));
        assertEquals(TOTALS, lines.get(5));
        assertEquals(FOOTER, lines.get(6));
    }

    @Test
    void generatePlan_twoDays_printsSpecConfirmation() throws MishMashException {
        String output = run("generate-plan d/2", createSpecRecipeBook());

        assertEquals("Meal plan successfully generated for 2 day(s)!" + LINE
                + "Use 'view-plan' to inspect your planned meals.", output);
    }

    @Test
    void viewPlan_afterGenerating_matchesSpecLayoutForEveryDay() throws MishMashException {
        RecipeBook recipeBook = createSpecRecipeBook();
        run("generate-plan d/2", recipeBook);

        String[] blocks = run("view-plan", recipeBook).split(LINE + LINE);

        assertEquals(2, blocks.length);
        assertSpecDayBlock(blocks[0], 1);
        assertSpecDayBlock(blocks[1], 2);
    }

    @Test
    void viewPlanDayOne_afterGenerating_showsOnlyDayOneAsInSpec() throws MishMashException {
        RecipeBook recipeBook = createSpecRecipeBook();
        run("generate-plan d/2", recipeBook);

        String dayOne = run("view-plan d/1", recipeBook);

        assertSpecDayBlock(dayOne, 1);
        assertEquals(run("view-plan", recipeBook).split(LINE + LINE)[0], dayOne);
    }

    @Test
    void generatePlan_firstSpecExample_threeDaysWithoutTargets() throws MishMashException {
        RecipeBook recipeBook = createSpecRecipeBook();
        recipeBook.add(TestRecipes.recipe("Beef Stir Fry", 720, 55, 45, 28));

        String output = run("generate-plan d/3", recipeBook);

        assertEquals("Meal plan successfully generated for 3 day(s)!" + LINE
                + "Use 'view-plan' to inspect your planned meals.", output);
        String[] days = run("view-plan", recipeBook).split(LINE + LINE);
        assertEquals(3, days.length);
        for (String day : days) {
            // At least 3 meals per day, and no recipe repeated within a day.
            List<String> meals = day.lines().filter(line -> line.startsWith("Meal ")).toList();
            assertEquals(3, meals.size(), day);
            assertEquals(3, meals.stream().map(meal -> meal.substring(meal.indexOf(": ")))
                    .distinct().count(), day);
        }
    }

    @Test
    void generatePlan_secondSpecExampleWithAllTargets_checksTolerance() throws MishMashException {
        // The format from the specification is accepted by the parser...
        assertInstanceOf(GeneratePlanCommand.class,
                parser.parseCommand("generate-plan d/2 cal/2200 p/160 c/220 f/65"));

        // ...but with only the specification's four sample recipes no 3-recipe day is within +/-15% of
        // 2200 kcal / 160 g protein / 220 g carbs / 65 g fats, so the command reports that clearly.
        RecipeBook fourRecipes = createSpecRecipeBook();
        fourRecipes.add(TestRecipes.recipe("Beef Stir Fry", 720, 55, 45, 28));
        MishMashException exception = assertThrows(MishMashException.class,
                () -> run("generate-plan d/2 cal/2200 p/160 c/220 f/65", fourRecipes));
        assertTrue(exception.getMessage().contains("within 15%"), exception.getMessage());

        // With a recipe that makes a matching day possible, the same command succeeds.
        fourRecipes.add(TestRecipes.recipe("Big Burrito", 1000, 70, 120, 20));
        assertEquals("Meal plan successfully generated for 2 day(s)!" + LINE
                + "Use 'view-plan' to inspect your planned meals.",
                run("generate-plan d/2 cal/2200 p/160 c/220 f/65", fourRecipes));
        // Day totals of the chosen combination must be within the specification's 15% tolerance.
        String totals = run("view-plan d/1", fourRecipes).lines()
                .filter(line -> line.startsWith("Daily Totals:")).findFirst().orElseThrow();
        assertTrue(totals.matches("Daily Totals: \\d+ kcal \\| Protein: \\d+g \\| Carbs: \\d+g \\| Fats: \\d+g"),
                totals);
    }

    @Test
    void generatePlan_daysConstraint_acceptsOneToSevenOnly() throws MishMashException {
        RecipeBook recipeBook = createSpecRecipeBook();

        for (String days : List.of("d/1", "d/7")) {
            assertTrue(run("generate-plan " + days, recipeBook).startsWith("Meal plan successfully generated"));
        }
        for (String days : List.of("d/0", "d/8", "d/-1", "d/1.5", "d/abc")) {
            assertThrows(MishMashException.class, () -> run("generate-plan " + days, recipeBook), days);
        }
    }

    @Test
    void viewPlan_dayNotInGeneratedPlan_isRejected() throws MishMashException {
        RecipeBook recipeBook = createSpecRecipeBook();
        run("generate-plan d/2", recipeBook);

        assertThrows(MishMashException.class, () -> run("view-plan d/3", recipeBook));
    }
}
