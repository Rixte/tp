package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.command.HelpCommand;
import mishmash.command.SetTargetsCommand;
import mishmash.command.ViewTargetsCommand;
import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.RecipeBook;
import mishmash.testutil.TestRecipes;

//@@author neil0618
/**
 * Checks that set-targets and view-targets are wired into the parser, share one set of saved targets with
 * generate-plan, report errors with their own usage, and appear in the usage list and help.
 */
class ParserTargetsTest {
    private final RecipeBook book = new RecipeBook();
    private final ActiveMealPlan activePlan = new ActiveMealPlan();
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(1)), activePlan);

    private String run(String input) throws MishMashException {
        return parser.parseCommand(input).execute(book);
    }

    @Test
    void parseCommand_returnsTheTargetCommands() throws MishMashException {
        assertInstanceOf(SetTargetsCommand.class, parser.parseCommand("set-targets cal/2000"));
        assertInstanceOf(ViewTargetsCommand.class, parser.parseCommand("view-targets"));
        assertInstanceOf(ViewTargetsCommand.class, parser.parseCommand("  view-targets   "));
    }

    @Test
    void parseCommand_parsingDoesNotChangeTargets() throws MishMashException {
        parser.parseCommand("set-targets cal/2000");

        assertEquals("No daily targets saved. Use 'set-targets' to set them.", run("view-targets"));
    }

    @Test
    void setThenView_sharesTargetsAcrossCommands() throws MishMashException {
        run("set-targets cal/2000 p/150");

        assertTrue(run("view-targets").contains("Calories: 2000 kcal"));
        assertTrue(run("view-targets").contains("Protein: 150 g"));
    }

    @Test
    void generatePlan_usesTargetsSavedThroughTheSameParser() throws MishMashException {
        book.add(TestRecipes.recipe("A", 300, 30, 40, 8));
        book.add(TestRecipes.recipe("B", 300, 30, 40, 8));
        book.add(TestRecipes.recipe("C", 400, 30, 40, 8));
        run("set-targets cal/1000");

        run("generate-plan d/1");

        assertEquals(1000, activePlan.getRequest().orElseThrow().targets().getCalories());
    }

    @Test
    void setTargets_errorsShowOnlySetTargetsUsage() {
        for (String input : new String[] {"set-targets", "set-targets cal/0", "set-targets x/1",
                "set-targets cal/1 cal/2", "set-targets 2000"}) {
            MishMashException error = assertThrows(MishMashException.class, () -> parser.parseCommand(input), input);
            assertEquals(Parser.SET_TARGETS_USAGE, error.getUsage().orElseThrow(), input);
            assertFalse(error.getExplanation().contains("Format:"), error.getExplanation());
        }
    }

    @Test
    void viewTargets_rejectsArguments() {
        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("view-targets now"));

        assertEquals("view-targets does not accept arguments.", error.getExplanation());
        assertEquals(Parser.VIEW_TARGETS_USAGE, error.getUsage().orElseThrow());
    }

    @Test
    void commandWords_areCaseSensitive() {
        assertThrows(MishMashException.class, () -> parser.parseCommand("Set-Targets cal/1"));
        assertThrows(MishMashException.class, () -> parser.parseCommand("set-target cal/1"));
        assertThrows(MishMashException.class, () -> parser.parseCommand("view-target"));
    }

    @Test
    void usageAndHelp_listBothCommands() throws MishMashException {
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.SET_TARGETS_USAGE));
        assertTrue(Parser.COMMAND_USAGE.contains(Parser.VIEW_TARGETS_USAGE));
        assertEquals("Format: set-targets [cal/CALORIES|none] [p/PROTEIN|none] [c/CARBS|none] [f/FATS|none]",
                Parser.SET_TARGETS_USAGE);
        assertEquals("Format: view-targets", Parser.VIEW_TARGETS_USAGE);

        String help = new HelpCommand().execute(book);
        assertTrue(help.contains("set-targets - "));
        assertTrue(help.contains(Parser.SET_TARGETS_USAGE));
        assertTrue(help.contains("view-targets - "));
        assertTrue(help.contains(Parser.VIEW_TARGETS_USAGE));
    }
}
//@@author
