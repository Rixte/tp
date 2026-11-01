package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Checks whole-portion scaling, validation, and immutability of planned meals.
 */
class PlannedMealTest {
    private final Recipe recipe = new Recipe("Rice bowl", 500, 20, 80, 10,
            List.of(new GroceryItem("Rice", new BigDecimal("125.5"), "g")));

    @Test
    void constructor_invalidArguments_rejectsBeforeCreatingMeal() {
        assertThrows(NullPointerException.class, () -> new PlannedMeal(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new PlannedMeal(recipe, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlannedMeal(recipe, -1));
    }

    @Test
    void nutritionAndIngredients_multiplePortions_scalesExactlyWithoutChangingRecipe() {
        PlannedMeal meal = new PlannedMeal(recipe, 3);

        assertEquals(1500, meal.getCalories());
        assertEquals(60, meal.getProtein());
        assertEquals(240, meal.getCarbs());
        assertEquals(30, meal.getFats());
        assertEquals(new BigDecimal("376.5"), meal.getScaledIngredients().get(0).getQuantity());
        assertEquals(new BigDecimal("125.5"), recipe.getIngredients().get(0).getQuantity());
        assertThrows(UnsupportedOperationException.class, () -> meal.getScaledIngredients().clear());
    }

    @Test
    void withPortionCount_sameAndDifferentCounts_reusesOnlyUnchangedMeal() {
        PlannedMeal original = PlannedMeal.onePortionOf(recipe);

        assertSame(original, original.withPortionCount(1));
        PlannedMeal changed = original.withPortionCount(2);
        assertSame(recipe, changed.getRecipe());
        assertEquals(2, changed.getPortionCount());
        assertEquals(1, original.getPortionCount());
    }

    @Test
    void withIngredients_replacementIsOccurrenceOnlyAndScalesWithPortions() {
        PlannedMeal original = new PlannedMeal(recipe, 2);
        GroceryItem tofu = new GroceryItem("Tofu", new BigDecimal("75.5"), "g");

        PlannedMeal customized = original.withIngredients(List.of(tofu));

        assertTrue(customized.hasCustomizedIngredients());
        assertEquals(List.of(tofu), customized.getIngredients());
        assertEquals(new BigDecimal("151.0"), customized.getScaledIngredients().get(0).getQuantity());
        assertEquals(recipe.getIngredients(), original.getIngredients());
        assertFalse(original.hasCustomizedIngredients());
        assertEquals(original.getCalories(), customized.getCalories());
        PlannedMeal resized = customized.withPortionCount(3);
        assertTrue(resized.hasCustomizedIngredients());
        assertEquals(new BigDecimal("226.5"), resized.getScaledIngredients().get(0).getQuantity());
    }

    @Test
    void withIngredients_equalValuesIsNoOpAndSourceValuesRestoreCustomization() {
        GroceryItem numericallyEqualRice = new GroceryItem("Rice", new BigDecimal("125.50"), "g");
        PlannedMeal original = PlannedMeal.onePortionOf(recipe);
        assertSame(original, original.withIngredients(List.of(numericallyEqualRice)));
        PlannedMeal customized = original.withIngredients(
                List.of(new GroceryItem("Tofu", BigDecimal.ONE, "g")));

        PlannedMeal restored = customized.withIngredients(List.of(numericallyEqualRice));

        assertFalse(restored.hasCustomizedIngredients());
        assertEquals(recipe.getIngredients(), restored.getIngredients());
    }

    @Test
    void withIngredients_reorderedOrRecasedValuesRemainMeaningfulCustomizations() {
        Recipe multiIngredientRecipe = new Recipe("Bowl", 500, 20, 80, 10, List.of(
                new GroceryItem("Brown Rice", new BigDecimal("100"), "g"),
                new GroceryItem("Tofu", new BigDecimal("50"), "G"),
                new GroceryItem("Tofu", new BigDecimal("25"), "g")));
        PlannedMeal meal = PlannedMeal.onePortionOf(multiIngredientRecipe);
        List<GroceryItem> reordered = List.of(
                new GroceryItem("tofu", new BigDecimal("25.0"), "G"),
                new GroceryItem("  brown   RICE ", new BigDecimal("100.00"), "G"),
                new GroceryItem("TOFU", new BigDecimal("50.0"), "g"));

        PlannedMeal customized = meal.withIngredients(reordered);

        assertNotSame(meal, customized);
        assertTrue(customized.hasCustomizedIngredients());
        assertEquals(reordered, customized.getIngredients());

        List<GroceryItem> wrongDuplicateQuantity = List.of(
                new GroceryItem("tofu", new BigDecimal("50"), "g"),
                new GroceryItem("brown rice", new BigDecimal("100"), "g"),
                new GroceryItem("tofu", new BigDecimal("50"), "g"));
        assertNotSame(meal, meal.withIngredients(wrongDuplicateQuantity));
    }

    @Test
    void withIngredients_invalidReplacement_rejectsWithoutChangingMeal() {
        PlannedMeal meal = PlannedMeal.onePortionOf(recipe);
        GroceryItem zero = new GroceryItem("Water", BigDecimal.ZERO, "ml");

        assertThrows(NullPointerException.class, () -> meal.withIngredients(null));
        assertThrows(IllegalArgumentException.class, () -> meal.withIngredients(List.of()));
        assertThrows(IllegalArgumentException.class, () -> meal.withIngredients(List.of(zero)));
        assertFalse(meal.hasCustomizedIngredients());
    }
}
