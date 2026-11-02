package mishmash.model;

import java.util.List;
import java.util.Objects;

/**
 * Holds one-based plan positions and a complete replacement ingredient list for one planned meal.
 *
 * @param dayNumber The selected day, counting from one.
 * @param mealNumber The selected meal within that day, counting from one.
 * @param ingredients The nonempty one-portion ingredient replacement.
 */
public record IngredientCustomization(int dayNumber, int mealNumber, List<GroceryItem> ingredients) {
    /**
     * Creates a customization with positive positions and an independent ingredient snapshot.
     *
     * @throws NullPointerException If the list or any ingredient is null.
     * @throws IllegalArgumentException If a position is not positive, the list is empty, or a quantity is not
     *         positive.
     */
    public IngredientCustomization {
        if (dayNumber < 1) {
            throw new IllegalArgumentException("Day number must be at least 1.");
        }
        if (mealNumber < 1) {
            throw new IllegalArgumentException("Meal number must be at least 1.");
        }
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("At least one customised ingredient is required.");
        }
        for (GroceryItem ingredient : ingredients) {
            if (ingredient.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException("Customised ingredient quantities must be positive.");
            }
        }
    }
}
