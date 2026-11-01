package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import mishmash.exception.MishMashException;
import mishmash.logic.MealPlanGenerator;
import mishmash.model.ActiveMealPlan;
import mishmash.model.DayPlan;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;

/**
 * Exercises dietary safety and recovery across real parsed commands and shared session state.
 */
class DietaryIntegrationTest {
    private final ActiveMealPlan active = new ActiveMealPlan();
    private final Parser parser = new Parser(new MealPlanGenerator(new Random(42)), active);
    private final RecipeBook book = new RecipeBook();

    /**
     * Runs commands through the real parser without replacing any application collaborators.
     */
    private String execute(String input) throws MishMashException {
        return parser.parseCommand(input).execute(book);
    }

    /**
     * Adds a classified recipe through the same validation path used by the CLI.
     */
    private void addRecipe(String name, String tags) throws MishMashException {
        execute("add-recipe n/" + name + " cal/600 p/10 c/20 f/5 i/" + name + ":2g food/" + tags);
    }

    /**
     * Creates the smallest pool that can generate a complete day.
     */
    private void addInitialRecipes() throws MishMashException {
        addRecipe("Rice", "none");
        addRecipe("Beans", "legume");
        addRecipe("Tofu", "soy,legume");
    }

    @Test
    void dietaryFailure_addedReplacement_recoversWithoutLosingRestrictions() throws MishMashException {
        addInitialRecipes();
        execute("generate-plan d/2");
        execute("generate-list");
        MealPlan original = active.get().orElseThrow();
        List<GroceryItem> originalGroceries = active.getGroceryList().orElseThrow();

        assertTrue(execute("set-diet avoid/soya").contains("have been cleared"));
        assertTrue(active.get().isEmpty());
        assertTrue(active.getRequest().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
        String profile = execute("view-diet");
        assertTrue(profile.contains("Avoided food categories: soy"));
        assertThrows(MishMashException.class, () -> execute("generate-plan d/2"));
        assertTrue(active.get().isEmpty());
        assertTrue(active.getRequest().isEmpty());
        assertTrue(active.getGroceryList().isEmpty());
        assertEquals(profile, execute("view-diet"));

        addRecipe("Corn", "corn");
        execute("generate-plan d/2");
        execute("generate-list");

        for (DayPlan day : active.get().orElseThrow().getDays()) {
            assertEquals(3, day.getMeals().size());
            assertEquals(Set.of("Rice", "Beans", "Corn"),
                    Set.copyOf(day.getMeals().stream().map(Recipe::getName).toList()));
        }
        assertEquals(List.of("Beans: 4g", "Corn: 4g", "Rice: 4g"),
                active.getGroceryList().orElseThrow().stream().map(GroceryItem::toString).sorted().toList());
        assertEquals(profile, execute("view-diet"));
        assertEquals(2, original.getNumberOfDays());
        assertTrue(originalGroceries.stream().anyMatch(item -> item.getName().equals("Tofu")));
    }

    @Test
    void dietaryChange_customizedConflict_repairsOnlyAffectedDay() throws MishMashException {
        addInitialRecipes();
        execute("generate-plan d/2");
        execute("customize-meal d/1 m/1 i/Salt:2g");
        execute("adjust-portion d/2 m/1 portions/2");
        execute("generate-list");
        MealPlan original = active.get().orElseThrow();
        DayPlan preserved = original.getDays().get(1);
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> oldGroceries = active.getGroceryList().orElseThrow();
        Command pending = parser.parseCommand("set-diet exclude/salt");
        assertTrue(pending.confirmationPrompt(book).isPresent());
        assertSame(original, active.get().orElseThrow());

        assertTrue(pending.execute(book).contains("Conflicting days were replaced."));

        MealPlan repaired = active.get().orElseThrow();
        assertSame(preserved, repaired.getDays().get(1));
        assertEquals(2400, preserved.getTotalCalories());
        assertSame(request, active.getRequest().orElseThrow());
        assertTrue(repaired.getDays().stream().flatMap(day -> day.getPlannedMeals().stream())
                .flatMap(meal -> meal.getIngredients().stream())
                .noneMatch(item -> item.getName().equalsIgnoreCase("salt")));
        assertFalse(active.getGroceryList().orElseThrow().stream()
                .anyMatch(item -> item.getName().equalsIgnoreCase("salt")));
        assertTrue(oldGroceries.stream().anyMatch(item -> item.getName().equals("Salt")));
        assertEquals("Salt", original.getDays().get(0).getPlannedMeals().get(0).getIngredients().get(0).getName());
        assertTrue(execute("view-diet").contains("Excluded ingredients: salt"));
    }

    @Test
    void customization_rejectedByDiet_preservesStateAndAllowsSafeRetry() throws MishMashException {
        addInitialRecipes();
        execute("set-diet exclude/salt");
        execute("generate-plan d/1");
        execute("generate-list");
        MealPlan original = active.get().orElseThrow();
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        String profile = execute("view-diet");

        assertThrows(MishMashException.class, () -> execute("customize-meal d/1 m/1 i/SALT:2g"));
        assertSame(original, active.get().orElseThrow());
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        assertEquals(profile, execute("view-diet"));

        execute("customize-meal d/1 m/1 i/Pepper:3g");
        assertTrue(active.getGroceryList().isEmpty());
        execute("generate-list");
        assertTrue(active.getGroceryList().orElseThrow().stream()
                .anyMatch(item -> item.getName().equals("Pepper")
                        && item.getQuantity().intValueExact() == 3));
        assertSame(request, active.getRequest().orElseThrow());
        assertEquals(profile, execute("view-diet"));
        assertFalse(book.getRecipes().stream().flatMap(recipe -> recipe.getIngredients().stream())
                .anyMatch(item -> item.getName().equals("Pepper")));
    }

    @Test
    void combinedRestrictions_generationAndViewing_enforceEveryRequirement() throws MishMashException {
        addInitialRecipes();
        addRecipe("Corn", "corn");
        addRecipe("Milk", "milk");
        addRecipe("Salt", "none");
        execute("add-recipe n/Unknown cal/600 p/10 c/20 f/5 i/Unknown:2g");
        execute("set-diet diet/vegan avoid/soya exclude/SALT");
        String profile = String.join(System.lineSeparator(), "Diet: vegan",
                "Avoided food categories: soy", "Excluded ingredients: salt");
        assertEquals(profile, execute("view-diet"));

        execute("generate-plan d/3 cal/1800");
        execute("generate-list");

        MealPlan plan = active.get().orElseThrow();
        GeneratePlanRequest request = active.getRequest().orElseThrow();
        List<GroceryItem> groceries = active.getGroceryList().orElseThrow();
        for (DayPlan day : plan.getDays()) {
            assertEquals(3, day.getMeals().size());
            assertEquals(Set.of("Rice", "Beans", "Corn"),
                    Set.copyOf(day.getMeals().stream().map(Recipe::getName).toList()));
            assertTrue(request.targets().isSatisfiedBy(day));
        }
        assertEquals(List.of("Beans: 6g", "Corn: 6g", "Rice: 6g"),
                groceries.stream().map(GroceryItem::toString).sorted().toList());
        assertEquals(profile, execute("view-diet"));
        assertSame(plan, active.get().orElseThrow());
        assertSame(request, active.getRequest().orElseThrow());
        assertSame(groceries, active.getGroceryList().orElseThrow());
        assertEquals(7, book.size());
    }
}
