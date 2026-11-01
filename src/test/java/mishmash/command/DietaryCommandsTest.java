package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.GroceryListGenerator;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.FoodInfo;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;

/**
 * Exercises dietary commands through the same parser and shared state used by the CLI.
 */
class DietaryCommandsTest {
    private final ActiveMealPlan active = new ActiveMealPlan();
    private final MealPlanGenerator generator = new MealPlanGenerator(new Random(42));
    private final Parser parser = new Parser(generator, active);
    private final RecipeBook book = new RecipeBook();

    /**
     * Creates a recipe with a known ingredient quantity for grocery-update assertions.
     */
    private Recipe createRecipe(String name, int calories, String tags) {
        return new Recipe(name, calories, 10, 20, 5,
                List.of(new GroceryItem(name, new BigDecimal("2"), "g")), FoodInfo.parseField(tags));
    }

    /**
     * Adds three compatible recipes to the shared book and returns them in insertion order.
     */
    private List<Recipe> addSuitableRecipes() {
        List<Recipe> recipes = List.of(createRecipe("Rice", 600, "none"), createRecipe("Beans", 600, "legume"),
                createRecipe("Tofu", 600, "soy,legume"));
        recipes.forEach(book::add);
        return recipes;
    }

    /**
     * Parses and executes one command against the same shared state used by the test.
     */
    private String execute(String input) throws MishMashException {
        return parser.parseCommand(input).execute(book);
    }

    @Test
    void viewPlan_dietaryRepair_recalculatesWarningsUsingOriginalTargets() throws MishMashException {
        List<Recipe> suitable = addSuitableRecipes();
        Recipe milk = createRecipe("Milk", 650, "milk");
        MealPlan plan = new MealPlan(List.of(new DayPlan(List.of(suitable.get(0), suitable.get(1), milk))));
        GeneratePlanRequest request = new GeneratePlanRequest(1, new NutritionTargets(1700, null, null, null));
        active.setGenerated(plan, request, Optional.empty());
        Command view = parser.parseCommand("view-plan");
        assertTrue(view.execute(book).contains("Warning: Calories exceed target by 150 kcal"
                + " (1850 kcal actual; 1700 kcal target)."));

        assertTrue(execute("set-diet diet/vegan").contains("Conflicting days were replaced."));

        String display = view.execute(book);
        assertTrue(display.contains("Warning: Calories exceed target by 100 kcal"
                + " (1800 kcal actual; 1700 kcal target)."), display);
        assertFalse(display.contains("1850"), display);
        assertEquals(request, active.getRequest().orElseThrow());

        assertTrue(execute("set-diet avoid/soy").contains("have been cleared"));
        assertEquals("No active meal plan. Use 'generate-plan' first.", view.execute(book));
    }

    @Test
    void setDiet_beforeGeneration_filtersFuturePlans() throws MishMashException {
        addSuitableRecipes();
        book.add(createRecipe("Milk", 600, "milk"));
        book.add(createRecipe("Unknown", 600, null));
        execute("set-diet diet/vegan");
        execute("generate-plan d/7 cal/1800");
        assertTrue(execute("view-diet").contains("Diet: vegan"));
        for (DayPlan day : active.get().orElseThrow().getDays()) {
            assertEquals(Set.of("Rice", "Beans", "Tofu"),
                    Set.copyOf(day.getMeals().stream().map(Recipe::getName).toList()));
        }
        assertEquals(7, active.getRequest().orElseThrow().days());
    }

    @Test
    void setDiet_partialRepair_preservesDaysAndRebuildsGroceryQuantities() throws MishMashException {
        List<Recipe> suitableRecipes = addSuitableRecipes();
        DayPlan unchanged = new DayPlan(suitableRecipes);
        Recipe milk = createRecipe("Milk", 600, "milk");
        DayPlan conflicting = new DayPlan(List.of(suitableRecipes.get(0), suitableRecipes.get(1), milk));
        MealPlan original = new MealPlan(List.of(unchanged, conflicting, unchanged));
        GeneratePlanRequest request = new GeneratePlanRequest(3, new NutritionTargets(1800, null, null, null));
        active.setGenerated(original, request,
                Optional.of(new GroceryListGenerator().generateForPlan(original)));
        List<GroceryItem> oldGroceries = active.getGroceryList().orElseThrow();

        String message = execute("set-diet avoid/milk");

        MealPlan updated = active.get().orElseThrow();
        assertSame(unchanged, updated.getDays().get(0));
        assertSame(unchanged, updated.getDays().get(2));
        assertNotSame(conflicting, updated.getDays().get(1));
        assertEquals(request, active.getRequest().orElseThrow());
        assertTrue(updated.getDays().stream().allMatch(request.targets()::isSatisfiedBy));
        assertTrue(message.contains("grocery list has also been updated"));
        assertEquals(List.of("Rice: 6g", "Beans: 6g", "Tofu: 6g"),
                active.getGroceryList().orElseThrow().stream().map(GroceryItem::toString).toList());
        assertTrue(oldGroceries.stream().anyMatch(item -> item.getName().equals("Milk")));
    }

    @Test
    void setDiet_noExistingGroceries_updatesPlanWithoutCreatingList() throws MishMashException {
        List<Recipe> recipes = addSuitableRecipes();
        MealPlan plan = new MealPlan(List.of(new DayPlan(
                List.of(recipes.get(0), recipes.get(1), createRecipe("Egg", 600, "egg")))));
        active.setGenerated(plan, new GeneratePlanRequest(1, NutritionTargets.none()), Optional.empty());
        assertTrue(execute("set-diet diet/vegan").contains("generate-list"));
        assertTrue(active.getGroceryList().isEmpty());
        execute("generate-list");
        assertFalse(active.getGroceryList().orElseThrow().stream().anyMatch(item -> item.getName().equals("Egg")));
    }

    @Test
    void setDiet_compatiblePlan_preservesPlanAndGrocerySnapshot() throws MishMashException {
        addSuitableRecipes();
        execute("generate-plan d/2");
        execute("generate-list");
        MealPlan original = active.get().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        assertTrue(execute("set-diet diet/vegan").contains("already matches"));
        assertSame(original, active.get().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
    }

    @Test
    void setDiet_insufficientReplacement_savesPreferencesAndClearsAllPlanState() throws MishMashException {
        addSuitableRecipes();
        execute("generate-plan d/1");
        execute("generate-list");
        CommandResult result = parser.parseCommand("set-diet avoid/soy").executeWithResult(book);
        String message = result.feedback();
        assertEquals(CommandResult.Status.WARNING, result.status());
        assertTrue(message.contains("found 2"));
        assertTrue(message.contains("have been cleared"));
        assertTrue(active.get().isEmpty());
        assertTrue(active.getRequest().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
        assertTrue(execute("view-diet").contains("Avoided food categories: soy"));
        assertThrows(MishMashException.class, () -> execute("generate-plan d/1"));
        assertTrue(execute("view-plan").contains("No active meal plan"));
    }

    @Test
    void setDiet_unreachableTargets_savesPreferencesAndClearsLists() throws MishMashException {
        List<Recipe> recipes = addSuitableRecipes();
        MealPlan plan = new MealPlan(List.of(new DayPlan(
                List.of(recipes.get(0), recipes.get(1), createRecipe("Milk", 3800, "milk")))));
        active.setGenerated(plan, new GeneratePlanRequest(1, new NutritionTargets(5000, null, null, null)),
                Optional.of(new GroceryListGenerator().generateForPlan(plan)));
        String message = execute("set-diet diet/vegan");
        assertTrue(message.contains("daily targets"));
        assertTrue(active.get().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
        assertTrue(execute("view-diet").contains("Diet: vegan"));
    }

    @Test
    void setDiet_missingOriginalRequest_savesPreferencesAndClearsIncompatiblePlan() throws MishMashException {
        active.set(new MealPlan(List.of(new DayPlan(List.of(createRecipe("Egg", 600, "egg"))))));
        assertTrue(execute("set-diet diet/vegan").contains("settings are unavailable"));
        assertTrue(active.get().isEmpty());
        assertTrue(execute("view-diet").contains("Diet: vegan"));
    }

    @Test
    void parseDiet_commandsDoNotMutateUntilExecution() throws MishMashException {
        Command pending = parser.parseCommand("set-diet diet/vegan");
        assertTrue(execute("view-diet").contains("Diet: none"));
        assertTrue(pending.confirmationPrompt(book).isEmpty());
        pending.execute(book);
        assertTrue(execute("view-diet").contains("Diet: vegan"));
    }

    @Test
    void setDiet_repeatedCommands_replacePreviousRequirements() throws MishMashException {
        execute("set-diet diet/vegan avoid/soy exclude/salt");
        execute("set-diet avoid/milk");
        assertEquals(String.join(System.lineSeparator(), "Diet: none",
                "Avoided food categories: milk", "Excluded ingredients: none"), execute("view-diet"));
    }

    @Test
    void invalidDietaryCommands_preservePreferencesPlanAndGroceries() throws MishMashException {
        addSuitableRecipes();
        execute("set-diet diet/vegan");
        execute("generate-plan d/1");
        execute("generate-list");
        MealPlan plan = active.get().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        for (String command : List.of("set-diet", "set-diet unknown", "set-diet diet/unknown", "set-diet avoid/",
                "view-diet extra")) {
            MishMashException error = assertThrows(MishMashException.class, () -> execute(command));
            assertTrue(error.getMessage().contains("Format: " + command.split(" ")[0]));
            assertSame(plan, active.get().orElseThrow());
            assertSame(groceries, active.getGroceryList().orElseThrow());
            assertTrue(execute("view-diet").contains("Diet: vegan"));
        }
    }

    @Test
    void addRecipe_foodMetadata_isValidatedAndVisible() throws MishMashException {
        String base = "add-recipe n/Meal cal/600 p/10 c/20 f/5 i/Rice:2g";
        assertTrue(execute(base + " food/milk,egg").contains("Food tags: egg, milk"));
        assertTrue(execute(base + " food/none").contains("Food tags: None declared"));
        assertTrue(execute(base).contains("Food tags: Unclassified"));
        for (String tags : List.of("food/unknown", "food/milk,dairy", "food/none,egg", "food/none food/egg")) {
            assertThrows(MishMashException.class, () -> execute(base + " " + tags));
        }
        assertEquals(3, book.size());
        assertTrue(execute("list-recipes 1").contains("Food tags: egg, milk"));
    }

    @Test
    void help_dietaryCommands_explainsTagsAndClassification() throws MishMashException {
        String help = execute("help");
        assertTrue(help.contains("Format: set-diet"));
        assertTrue(help.contains("Format: view-diet"));
        assertTrue(help.contains("Unclassified recipes are excluded"));
        assertTrue(help.contains("tree-nut"));
    }

    @Test
    void parseCommand_unprefixedUnknownDiet_showsValidDietsExampleAndUsage() {
        MishMashException error = assertThrows(MishMashException.class,
                () -> parser.parseCommand("set-diet unknown"));
        assertTrue(error.getMessage().startsWith("Invalid dietary input 'unknown'."));
        assertTrue(error.getMessage().contains("Valid diets: " + Diet.getSupportedLabels() + "."));
        assertTrue(error.getMessage().contains("Example: set-diet diet/vegan"));
        assertTrue(error.getMessage().contains("Format: set-diet"));
        assertTrue(error.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void constructorAndExecution_nullDependencies_throwWithoutChangingSettings() {
        DietarySettings settings = new DietarySettings();
        DietaryPreferences preferences = DietaryPreferences.createUnrestricted();
        assertThrows(NullPointerException.class, () -> new SetDietCommand(null, settings, active, generator));
        assertThrows(NullPointerException.class, () -> new SetDietCommand(preferences, null, active, generator));
        assertThrows(NullPointerException.class, () -> new SetDietCommand(preferences, settings, null, generator));
        assertThrows(NullPointerException.class, () -> new SetDietCommand(preferences, settings, active, null));
        assertThrows(NullPointerException.class,
                () -> new SetDietCommand(preferences, settings, active, generator).execute(null));
        assertThrows(NullPointerException.class, () -> new GeneratePlanCommand(
                new GeneratePlanRequest(1, NutritionTargets.none()), generator, active, null));
        assertEquals(preferences, settings.get());
    }

    @Test
    void setDiet_ingredientExclusion_repairsPlanAndRemovesExcludedGroceries() throws MishMashException {
        List<Recipe> suitable = addSuitableRecipes();
        Recipe salt = createRecipe("Salt", 600, "none");
        DayPlan conflicting = new DayPlan(List.of(suitable.get(0), suitable.get(1), salt));
        MealPlan original = new MealPlan(List.of(conflicting));
        GeneratePlanRequest request = new GeneratePlanRequest(1, NutritionTargets.none());
        active.setGenerated(original, request,
                Optional.of(new GroceryListGenerator().generateForPlan(original)));

        assertTrue(execute("set-diet exclude/ SALT ").contains("Conflicting days were replaced."));

        assertEquals(Set.copyOf(suitable), Set.copyOf(active.get().orElseThrow().getDays().get(0).getMeals()));
        assertFalse(active.getGroceryList().orElseThrow().stream().anyMatch(item -> item.getName().equals("Salt")));
        assertTrue(execute("view-diet").contains("Excluded ingredients: salt"));
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(conflicting, original.getDays().get(0));
    }

    @Test
    void setDiet_invalidAliasDuplicates_preservesEveryStateField() throws MishMashException {
        addSuitableRecipes();
        execute("set-diet diet/vegan");
        execute("generate-plan d/1");
        execute("generate-list");
        MealPlan plan = active.get().orElseThrow();
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        String profile = execute("view-diet");

        for (String input : List.of("set-diet avoid/milk,dairy", "set-diet avoid/soy,soya",
                "set-diet exclude/Salt, SALT", "set-diet avoid/egg,")) {
            assertThrows(MishMashException.class, () -> execute(input));
            assertEquals(profile, execute("view-diet"));
            assertSame(plan, active.get().orElseThrow());
            assertSame(request, active.getRequest().orElseThrow());
            assertSame(groceries, active.getGroceryList().orElseThrow());
        }
    }

    @Test
    void setDiet_pendingCommand_evaluatesPlanAtExecution() throws MishMashException {
        List<Recipe> suitable = addSuitableRecipes();
        Command pending = parser.parseCommand("set-diet diet/vegan");
        Recipe milk = createRecipe("Milk", 600, "milk");
        MealPlan original = new MealPlan(List.of(new DayPlan(List.of(suitable.get(0), suitable.get(1), milk))));
        active.setGenerated(original, new GeneratePlanRequest(1, NutritionTargets.none()), Optional.empty());

        assertTrue(pending.confirmationPrompt(book).isPresent());
        assertSame(original, active.get().orElseThrow());
        assertTrue(execute("view-diet").contains("Diet: none"));
        assertTrue(pending.execute(book).contains("Conflicting days were replaced."));
        assertNotSame(original, active.get().orElseThrow());
        assertTrue(execute("view-diet").contains("Diet: vegan"));
    }

    @Test
    void setDiet_relaxedProfile_preservesExistingPlanAndGroceries() throws MishMashException {
        addSuitableRecipes();
        execute("set-diet diet/vegan avoid/milk exclude/salt");
        execute("generate-plan d/1");
        execute("generate-list");
        MealPlan plan = active.get().orElseThrow();
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();

        assertTrue(execute("set-diet diet/none").contains("already matches"));

        assertEquals(String.join(System.lineSeparator(), "Diet: none",
                "Avoided food categories: none", "Excluded ingredients: none"), execute("view-diet"));
        assertSame(plan, active.get().orElseThrow());
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
    }
}
