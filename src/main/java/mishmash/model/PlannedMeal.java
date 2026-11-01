package mishmash.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents one recipe occurrence in a meal plan together with its whole-recipe portion count.
 */
public final class PlannedMeal {
    private final Recipe recipe;
    private final int portionCount;
    /** Null means this occurrence still uses its source recipe's ingredients. */
    private final List<GroceryItem> customizedIngredients;

    /**
     * Creates a planned meal containing a positive whole number of recipe portions.
     *
     * @param recipe The immutable one-portion recipe used by this planned meal.
     * @param portionCount The number of whole recipe portions.
     * @throws NullPointerException If the recipe is null.
     * @throws IllegalArgumentException If the portion count is not positive.
     */
    public PlannedMeal(Recipe recipe, int portionCount) {
        this(recipe, portionCount, null);
    }

    /**
     * Creates a defensive planned-meal snapshot with an optional ingredient replacement.
     */
    private PlannedMeal(Recipe recipe, int portionCount, List<GroceryItem> customizedIngredients) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
        if (portionCount < 1) {
            throw new IllegalArgumentException("Portion count must be at least 1.");
        }
        this.portionCount = portionCount;
        this.customizedIngredients = copyCustomizedIngredients(customizedIngredients);
    }

    /**
     * Creates a one-portion planned meal from a recipe.
     *
     * @param recipe The recipe represented by the planned meal.
     * @return A planned meal with one portion.
     */
    public static PlannedMeal onePortionOf(Recipe recipe) {
        return new PlannedMeal(recipe, 1);
    }

    /**
     * Returns the underlying one-portion recipe.
     */
    public Recipe getRecipe() {
        return recipe;
    }

    /**
     * Returns the positive number of whole recipe portions.
     */
    public int getPortionCount() {
        return portionCount;
    }

    /**
     * Returns the one-portion ingredients effective for this planned occurrence.
     *
     * @return Customized ingredients when present, otherwise the source recipe's ingredients.
     */
    public List<GroceryItem> getIngredients() {
        return customizedIngredients == null ? recipe.getIngredients() : customizedIngredients;
    }

    /**
     * Returns whether this occurrence has ingredients independent of its source recipe.
     */
    public boolean hasCustomizedIngredients() {
        return customizedIngredients != null;
    }

    /**
     * Returns this meal's calories after multiplying by its portion count.
     */
    public long getCalories() {
        return multiplyNutrition(recipe.getCalories());
    }

    /**
     * Returns this meal's protein in grams after multiplying by its portion count.
     */
    public long getProtein() {
        return multiplyNutrition(recipe.getProtein());
    }

    /**
     * Returns this meal's carbohydrates in grams after multiplying by its portion count.
     */
    public long getCarbs() {
        return multiplyNutrition(recipe.getCarbs());
    }

    /**
     * Returns this meal's fats in grams after multiplying by its portion count.
     */
    public long getFats() {
        return multiplyNutrition(recipe.getFats());
    }

    /**
     * Returns new ingredient values scaled by this meal's portion count.
     *
     * @return An unmodifiable ingredient list in the recipe's original order.
     */
    public List<GroceryItem> getScaledIngredients() {
        BigDecimal multiplier = BigDecimal.valueOf(portionCount);
        List<GroceryItem> scaledIngredients = new ArrayList<>();
        for (GroceryItem ingredient : getIngredients()) {
            Objects.requireNonNull(ingredient, "ingredient");
            scaledIngredients.add(new GroceryItem(ingredient.getName(),
                    ingredient.getQuantity().multiply(multiplier), ingredient.getUnit()));
        }
        return List.copyOf(scaledIngredients);
    }

    /**
     * Returns an otherwise identical planned meal with the supplied portion count.
     *
     * @param newPortionCount The new positive whole-recipe portion count.
     * @return A new planned meal, or this instance when the count is unchanged.
     */
    public PlannedMeal withPortionCount(int newPortionCount) {
        if (newPortionCount == portionCount) {
            return this;
        }
        return new PlannedMeal(recipe, newPortionCount, customizedIngredients);
    }

    /**
     * Returns an otherwise identical meal using a complete one-portion ingredient replacement.
     * Supplying values equal to the source recipe removes an existing customization.
     *
     * @param newIngredients The nonempty replacement containing strictly positive quantities.
     * @return A new planned meal, or this instance when its effective ingredients are unchanged.
     * @throws NullPointerException If the list or any ingredient is null.
     * @throws IllegalArgumentException If the list is empty or a quantity is not positive.
     */
    public PlannedMeal withIngredients(List<GroceryItem> newIngredients) {
        List<GroceryItem> replacement = copyCustomizedIngredients(
                Objects.requireNonNull(newIngredients, "newIngredients"));
        if (sameIngredients(getIngredients(), replacement)) {
            return this;
        }
        if (sameIngredients(recipe.getIngredients(), replacement)) {
            return new PlannedMeal(recipe, portionCount);
        }
        return new PlannedMeal(recipe, portionCount, replacement);
    }

    /**
     * Multiplies one nonnegative recipe nutrition value without narrowing to an integer.
     */
    private long multiplyNutrition(int value) {
        assert value >= 0 : "Recipe nutrition must not be negative";
        return (long) value * portionCount;
    }

    /**
     * Copies and validates a customization, while preserving null as the no-customization marker.
     */
    private static List<GroceryItem> copyCustomizedIngredients(List<GroceryItem> ingredients) {
        if (ingredients == null) {
            return null;
        }
        List<GroceryItem> snapshot = List.copyOf(ingredients);
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("At least one customized ingredient is required.");
        }
        for (GroceryItem ingredient : snapshot) {
            if (ingredient.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException("Customized ingredient quantities must be positive.");
            }
        }
        return snapshot;
    }

    /**
     * Compares ingredients in order by exact name and unit and by numeric quantity.
     */
    private static boolean sameIngredients(List<GroceryItem> first, List<GroceryItem> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            GroceryItem firstItem = first.get(index);
            GroceryItem secondItem = second.get(index);
            if (!firstItem.getName().equals(secondItem.getName())
                    || firstItem.getQuantity().compareTo(secondItem.getQuantity()) != 0
                    || !firstItem.getUnit().equals(secondItem.getUnit())) {
                return false;
            }
        }
        return true;
    }
}
