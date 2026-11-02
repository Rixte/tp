package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DietarySettings;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.model.TargetSettings;
import mishmash.parser.GeneratePlanParser;
import mishmash.parser.SetTargetsParser;
import mishmash.testutil.TestRecipes;

//@@author neil0618
/**
 * Checks set-targets and view-targets, and how generate-plan combines saved targets with typed ones.
 */
class SetTargetsCommandTest {
    private static final String NEWLINE = System.lineSeparator();
    private static final String NOTE = "They apply to the next 'generate-plan'; your current meal plan is unchanged.";
    private static final String UNCHANGED = "Daily targets are unchanged. Nothing was changed.";

    private final TargetSettings settings = new TargetSettings();
    private final RecipeBook book = new RecipeBook();
    private final ActiveMealPlan activePlan = new ActiveMealPlan();

    private SetTargetsCommand set(String arguments) {
        return new SetTargetsCommand(new SetTargetsParser().parse(arguments), settings);
    }

    private GeneratePlanCommand generate(String arguments) {
        return new GeneratePlanCommand(new GeneratePlanParser().parse(arguments),
                new MealPlanGenerator(new Random(1)), activePlan, new DietarySettings(), settings);
    }

    private void addRecipes() {
        // Three recipes add up to 1000 kcal / 90 g protein; the decoy is far from any sensible target.
        book.add(TestRecipes.recipe("A1", 300, 30, 40, 8));
        book.add(TestRecipes.recipe("A2", 300, 30, 40, 8));
        book.add(TestRecipes.recipe("A3", 400, 30, 40, 8));
        book.add(TestRecipes.recipe("Decoy", 5000, 1, 1, 1));
    }

    @Test
    void execute_savesTargetsAndConfirms() {
        String message = set("cal/2000 p/150").execute(book);

        assertEquals("Daily targets saved: 2000 kcal, 150 g protein." + NEWLINE + NOTE, message);
        assertEquals(2000, settings.get().getCalories());
        assertEquals(150, settings.get().getProtein());
    }

    @Test
    void execute_partialUpdate_keepsEarlierTargets() {
        set("cal/2000 p/150").execute(book);
        String message = set("f/60").execute(book);

        assertTrue(message.startsWith("Daily targets saved: 2000 kcal, 150 g protein, 60 g fats."));
        assertEquals(2000, settings.get().getCalories());
    }

    @Test
    void execute_replacesAnExistingTarget() {
        set("cal/2000").execute(book);
        set("cal/1800").execute(book);

        assertEquals(1800, settings.get().getCalories());
    }

    @Test
    void execute_none_clearsOneTarget() {
        set("cal/2000 p/150").execute(book);
        String message = set("cal/none").execute(book);

        assertEquals("Daily targets saved: 150 g protein." + NEWLINE + NOTE, message);
        assertNull(settings.get().getCalories());
    }

    @Test
    void execute_clearingLastTarget_saysAllCleared() {
        set("p/150").execute(book);
        String message = set("p/none").execute(book);

        assertEquals("All daily targets cleared." + NEWLINE + NOTE, message);
        assertTrue(!settings.get().hasAnyTarget());
    }

    @Test
    void execute_clearingWhenNothingSaved_changesNothing() {
        SetTargetsCommand command = set("f/none");

        CommandResult result = command.executeWithResult(book);

        assertEquals(CommandResult.Status.NOTE, result.status());
        assertEquals(UNCHANGED, result.feedback());
        assertTrue(!settings.get().hasAnyTarget());
    }

    @Test
    void execute_sameValueAgain_changesNothing() {
        set("cal/1500 p/80").execute(book);

        CommandResult result = set("p/80 cal/1500").executeWithResult(book);

        assertEquals(CommandResult.Status.NOTE, result.status());
        assertEquals(UNCHANGED, result.feedback());
        assertEquals(1500, settings.get().getCalories());
        assertEquals(80, settings.get().getProtein());
    }

    @Test
    void execute_changeMixedWithSameValue_isStillSaved() {
        set("cal/1500").execute(book);

        CommandResult result = set("cal/1500 p/80").executeWithResult(book);

        assertEquals(CommandResult.Status.DONE, result.status());
        assertTrue(result.feedback().startsWith("Daily targets saved: 1500 kcal, 80 g protein."));
    }

    @Test
    void execute_doesNotTouchRecipesOrPlan() throws MishMashException {
        addRecipes();
        generate("d/1").execute(book);
        MealPlan before = activePlan.get().orElseThrow();
        GeneratePlanRequest requestBefore = activePlan.getRequest().orElseThrow();

        set("cal/2000").execute(book);

        assertSame(before, activePlan.get().orElseThrow());
        assertSame(requestBefore, activePlan.getRequest().orElseThrow());
        assertEquals(4, book.getRecipes().size());
    }

    @Test
    void constructorsAndExecute_rejectNulls() {
        assertThrows(NullPointerException.class, () -> new SetTargetsCommand(null, settings));
        assertThrows(NullPointerException.class,
                () -> new SetTargetsCommand(new SetTargetsParser().parse("p/1"), null));
        assertThrows(NullPointerException.class, () -> set("p/1").execute(null));
        assertThrows(NullPointerException.class, () -> new ViewTargetsCommand(null));
        assertThrows(NullPointerException.class, () -> new ViewTargetsCommand(settings).execute(null));
    }

    @Test
    void viewTargets_nothingSaved() {
        assertEquals("No daily targets saved. Use 'set-targets' to set them.",
                new ViewTargetsCommand(settings).execute(book));
    }

    @Test
    void viewTargets_listsEveryNutrient() {
        set("cal/2000 p/150 f/70").execute(book);

        assertEquals("Daily targets:" + NEWLINE + "Calories: 2000 kcal" + NEWLINE + "Protein: 150 g" + NEWLINE
                + "Carbs: not set" + NEWLINE + "Fats: 70 g", new ViewTargetsCommand(settings).execute(book));
    }

    @Test
    void viewTargets_readsLatestSettingsAtExecution() {
        ViewTargetsCommand view = new ViewTargetsCommand(settings);
        set("cal/1500").execute(book);

        assertTrue(view.execute(book).contains("Calories: 1500 kcal"));
    }

    @Test
    void generate_withoutSavedTargets_isUnchanged() throws MishMashException {
        addRecipes();
        GeneratePlanRequest request = new GeneratePlanParser().parse("d/1");
        GeneratePlanCommand command = new GeneratePlanCommand(request, new MealPlanGenerator(new Random(1)),
                activePlan, new DietarySettings(), settings);

        String message = command.execute(book);

        assertEquals("Meal plan successfully generated for 1 day(s)!" + NEWLINE
                + "Use 'view-plan' to inspect your planned meals.", message);
        assertSame(request, activePlan.getRequest().orElseThrow());
    }

    @Test
    void generate_usesSavedTargets() throws MishMashException {
        addRecipes();
        set("cal/1000 p/90").execute(book);

        generate("d/1").execute(book);

        NutritionTargets stored = activePlan.getRequest().orElseThrow().targets();
        assertEquals(1000, stored.getCalories());
        assertEquals(90, stored.getProtein());
        assertEquals(1000, activePlan.get().orElseThrow().getDays().get(0).getTotalCalories());
    }

    @Test
    void generate_typedTargetOverridesSavedOne_andOthersStillApply() throws MishMashException {
        addRecipes();
        set("cal/5000 p/90").execute(book);

        // The typed calories replace the saved 5000, which the three small recipes could never meet.
        generate("d/1 cal/1000").execute(book);

        NutritionTargets stored = activePlan.getRequest().orElseThrow().targets();
        assertEquals(1000, stored.getCalories());
        assertEquals(90, stored.getProtein());
    }

    @Test
    void generate_typedTargetReplacesAnImpossibleSavedTarget() throws MishMashException {
        addRecipes();
        set("cal/50").execute(book);

        generate("d/1 cal/1000").execute(book);

        assertEquals(1000, activePlan.getRequest().orElseThrow().targets().getCalories());
    }

    @Test
    void generate_readsSavedTargetsWhenExecuted_notWhenParsed() throws MishMashException {
        addRecipes();
        GeneratePlanCommand command = generate("d/1");
        set("cal/1000").execute(book);

        command.execute(book);

        assertEquals(1000, activePlan.getRequest().orElseThrow().targets().getCalories());
    }

    @Test
    void generate_afterClearingTargets_ignoresThem() throws MishMashException {
        addRecipes();
        set("cal/5000").execute(book);
        set("cal/none").execute(book);

        generate("d/1").execute(book);

        assertTrue(!activePlan.getRequest().orElseThrow().targets().hasAnyTarget());
    }

    @Test
    void generate_impossibleSavedTarget_failsAndKeepsThePreviousPlan() throws MishMashException {
        addRecipes();
        generate("d/1").execute(book);
        MealPlan before = activePlan.get().orElseThrow();
        set("cal/50").execute(book);

        assertThrows(MishMashException.class, () -> generate("d/1").execute(book));

        assertSame(before, activePlan.get().orElseThrow());
    }

    @Test
    void generate_savedTargetsUsed_confirmationNamesThem() throws MishMashException {
        addRecipes();
        set("cal/1000 p/90").execute(book);

        String message = generate("d/1").execute(book);

        assertEquals("Meal plan successfully generated for 1 day(s)!" + NEWLINE
                + "Daily targets used: 1000 kcal, 90 g protein." + NEWLINE
                + "Use 'view-plan' to inspect your planned meals.", message);
    }

    @Test
    void generate_typedAndSavedTargets_confirmationListsTheMergedTargets() throws MishMashException {
        addRecipes();
        set("cal/5000 p/90").execute(book);

        String message = generate("d/1 cal/1000").execute(book);

        assertTrue(message.contains("Daily targets used: 1000 kcal, 90 g protein."), message);
    }

    @Test
    void generate_everySavedNutrientTyped_doesNotMentionSavedTargets() throws MishMashException {
        addRecipes();
        set("cal/5000").execute(book);

        String message = generate("d/1 cal/1000").execute(book);

        assertTrue(!message.contains("Daily targets used"), message);
    }

    @Test
    void generate_impossibleSavedTarget_errorNamesTheAppliedTargets() throws MishMashException {
        addRecipes();
        set("cal/50").execute(book);

        MishMashException error = assertThrows(MishMashException.class, () -> generate("d/1").execute(book));

        assertTrue(error.getMessage().contains("Daily targets applied: 50 kcal (including your saved targets)."),
                error.getMessage());
        assertTrue(error.getMessage().contains("set-targets"), error.getMessage());
    }

    @Test
    void generate_impossibleTypedTarget_errorDoesNotBlameSavedTargets() throws MishMashException {
        addRecipes();
        set("cal/1000").execute(book);

        // The typed calories replace the saved ones, so no saved target took part in the failure.
        MishMashException error = assertThrows(MishMashException.class,
                () -> generate("d/1 cal/50").execute(book));

        assertTrue(!error.getMessage().contains("including your saved targets"), error.getMessage());
    }

    @Test
    void generate_rejectsNullTargetSettings() {
        assertThrows(NullPointerException.class, () -> new GeneratePlanCommand(
                new GeneratePlanParser().parse("d/1"), new MealPlanGenerator(new Random(1)), activePlan,
                new DietarySettings(), null));
    }
}
//@@author
