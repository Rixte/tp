package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Checks position, ingredient, and defensive-copy invariants of customization requests.
 */
class IngredientCustomizationTest {
    private final GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");

    @Test
    void constructor_mutableIngredients_storesIndependentUnmodifiableSnapshot() {
        List<GroceryItem> ingredients = new ArrayList<>(List.of(rice));
        IngredientCustomization customization = new IngredientCustomization(1, 2, ingredients);
        ingredients.clear();

        assertEquals(List.of(rice), customization.ingredients());
        assertThrows(UnsupportedOperationException.class, () -> customization.ingredients().clear());
    }

    @Test
    void constructor_invalidValues_rejectsRequest() {
        GroceryItem zero = new GroceryItem("Water", BigDecimal.ZERO, "ml");

        assertThrows(IllegalArgumentException.class, () -> new IngredientCustomization(0, 1, List.of(rice)));
        assertThrows(IllegalArgumentException.class, () -> new IngredientCustomization(1, 0, List.of(rice)));
        assertThrows(IllegalArgumentException.class, () -> new IngredientCustomization(1, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IngredientCustomization(1, 1, List.of(zero)));
        assertThrows(NullPointerException.class, () -> new IngredientCustomization(1, 1, null));
        assertThrows(NullPointerException.class,
                () -> new IngredientCustomization(1, 1, Arrays.asList(rice, null)));
    }
}
