package mishmash.model;

//@@author neil0618
/**
 * Holds validated one-based positions for replacing one planned meal with a recipe from the recipe book.
 *
 * @param dayNumber The selected day, counting from one.
 * @param mealNumber The selected meal within that day, counting from one.
 * @param recipeNumber The displayed recipe index (as shown by list-recipes), counting from one.
 */
public record MealReplacement(int dayNumber, int mealNumber, int recipeNumber) {
    /**
     * Creates a replacement containing only positive positions.
     *
     * @throws IllegalArgumentException If any value is less than one.
     */
    public MealReplacement {
        if (dayNumber < 1) {
            throw new IllegalArgumentException("Day number must be at least 1.");
        }
        if (mealNumber < 1) {
            throw new IllegalArgumentException("Meal number must be at least 1.");
        }
        if (recipeNumber < 1) {
            throw new IllegalArgumentException("Recipe number must be at least 1.");
        }
    }
}
//@@author
