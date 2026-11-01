package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

//@@author Rixte
/**
 * Checks model validation and immutable ingredient edits independently of command parsing.
 */
class RecipeChangesTest {
    @Test
    void constructor_noEditsOrBlankName_rejectsInvalidChanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, null, null, null, null, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges("  ", null, null, null, null, List.of(), null));
    }

    @Test
    void constructor_negativeNutrition_rejectsEveryField() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, -1, null, null, null, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, null, -1, null, null, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, null, null, -1, null, List.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, null, null, null, -1, List.of(), null));
    }

    @Test
    void constructor_invalidIngredients_rejectsNullAndZeroQuantity() {
        assertThrows(NullPointerException.class,
                () -> new RecipeChanges("Rice", null, null, null, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new RecipeChanges("Rice", null, null, null, null, Arrays.asList((GroceryItem) null), null));
        assertThrows(IllegalArgumentException.class, () -> new RecipeChanges(null, null, null, null, null,
                List.of(new GroceryItem("Rice", BigDecimal.ZERO, "g")), null));
    }

    @Test
    void constructor_ingredientListIsCopied_callerCannotChangePendingEdit() {
        GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");
        List<GroceryItem> source = new ArrayList<>(List.of(rice));
        RecipeChanges changes = new RecipeChanges(null, null, null, null, null, source, null);
        source.clear();

        assertEquals(List.of(rice), changes.ingredients());
        assertThrows(UnsupportedOperationException.class, () -> changes.ingredients().clear());
        assertThrows(NullPointerException.class, () -> changes.applyTo(null));
    }

    @Test
    void applyTo_allFields_buildsCompleteReplacementWithoutChangingOriginal() {
        GroceryItem rice = new GroceryItem("Rice", new BigDecimal("150"), "g");
        Recipe original = new Recipe("Rice Bowl", 200, 4, 45, 1, List.of(rice), FoodInfo.parseField("soy"));
        GroceryItem water = new GroceryItem("Water", new BigDecimal("250"), "ml");
        RecipeChanges changes = new RecipeChanges("  Water  ", 0, 0, 0, 0,
                List.of(water), FoodInfo.parseField("none"));

        Recipe edited = changes.applyTo(original);

        assertEquals("Water", edited.getName());
        assertEquals(0, edited.getCalories());
        assertEquals(0, edited.getProtein());
        assertEquals(0, edited.getCarbs());
        assertEquals(0, edited.getFats());
        assertEquals(List.of(water), edited.getIngredients());
        assertEquals(FoodInfo.parseField("none"), edited.getFoodInfo());
        assertEquals("Rice Bowl", original.getName());
        assertEquals(200, original.getCalories());
        assertEquals(4, original.getProtein());
        assertEquals(45, original.getCarbs());
        assertEquals(1, original.getFats());
        assertEquals(List.of(rice), original.getIngredients());
        assertEquals(FoodInfo.parseField("soy"), original.getFoodInfo());
        assertThrows(UnsupportedOperationException.class, () -> edited.getIngredients().clear());
    }

    @Test
    void applyTo_nameOnly_preservesMaximumNutritionAndUnclassifiedFood() {
        GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");
        Recipe original = new Recipe("Rice", Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(rice));

        Recipe edited = new RecipeChanges("New Name", null, null, null, null, List.of(), null).applyTo(original);

        assertEquals("New Name", edited.getName());
        assertEquals(Integer.MAX_VALUE, edited.getCalories());
        assertEquals(Integer.MAX_VALUE, edited.getProtein());
        assertEquals(Integer.MAX_VALUE, edited.getCarbs());
        assertEquals(Integer.MAX_VALUE, edited.getFats());
        assertEquals(List.of(rice), edited.getIngredients());
        assertEquals(FoodInfo.createUnknown(), edited.getFoodInfo());
    }

    @Test
    void constructor_validIngredientFollowedByNullOrZero_rejectsWholeReplacement() {
        GroceryItem rice = new GroceryItem("Rice", BigDecimal.ONE, "g");
        GroceryItem zero = new GroceryItem("Water", BigDecimal.ZERO, "ml");

        assertThrows(NullPointerException.class,
                () -> new RecipeChanges(null, null, null, null, null, Arrays.asList(rice, null), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeChanges(null, null, null, null, null, List.of(rice, zero), null));
    }

    @Test
    void constructor_nullIngredientList_identifiesInvalidDependency() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new RecipeChanges("New name", null, null, null, null, null, null));

        assertEquals("ingredients", error.getMessage());
    }

    @Test
    void constructor_nullIngredientEntry_identifiesInvalidElement() {
        GroceryItem valid = new GroceryItem("Rice", BigDecimal.ONE, "g");
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> new RecipeChanges(null, null, null, null, null, Arrays.asList(valid, null), null));

        assertEquals("ingredient", error.getMessage());
    }
}
//@@author
