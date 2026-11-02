package mishmash.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds immutable dietary requirements that apply together to each selected recipe.
 *
 * <p>Ingredient exclusions use complete names after case and whitespace normalization.
 *
 * @param diet The named diet, or {@link Diet#NONE} for no named-diet restriction.
 * @param avoidedTags Additional excluded food categories.
 * @param excludedIngredients Additional excluded ingredient names.
 */
public record DietaryPreferences(Diet diet, Set<FoodTag> avoidedTags, Set<String> excludedIngredients) {
    /**
     * Creates a profile with independent, unmodifiable exclusion sets.
     *
     * @param diet The named diet applied alongside explicit exclusions.
     * @param avoidedTags The additional categories to exclude.
     * @param excludedIngredients The complete ingredient names to normalize and exclude.
     * @throws NullPointerException If any argument or collection element is null.
     * @throws IllegalArgumentException If an ingredient name is blank or repeated after normalization.
     */
    public DietaryPreferences {
        Objects.requireNonNull(diet, "diet");
        avoidedTags = Set.copyOf(Objects.requireNonNull(avoidedTags, "avoidedTags"));
        Objects.requireNonNull(excludedIngredients, "excludedIngredients");
        Set<String> normalizedIngredients = new HashSet<>();
        for (String ingredient : excludedIngredients) {
            String name = normalizeIngredient(ingredient);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Excluded ingredient names must not be blank.");
            }
            if (!normalizedIngredients.add(name)) {
                throw new IllegalArgumentException("Repeated excluded ingredient: " + name + ".");
            }
        }
        excludedIngredients = Set.copyOf(normalizedIngredients);
    }

    /**
     * Creates a profile without named-diet, category, or ingredient restrictions.
     *
     * @return A profile that also permits unclassified recipes.
     */
    public static DietaryPreferences createUnrestricted() {
        return new DietaryPreferences(Diet.NONE, Set.of(), Set.of());
    }

    /**
     * Checks whether any dietary requirement is active.
     *
     * @return True if a named diet or at least one explicit exclusion is present.
     */
    public boolean isRestricted() {
        return diet != Diet.NONE || !avoidedTags.isEmpty() || !excludedIngredients.isEmpty();
    }

    /**
     * Checks whether recipe category declarations are needed to verify the current profile.
     *
     * @return True if a named diet or avoided food category is active.
     */
    public boolean hasCategoryRestrictions() {
        return diet != Diet.NONE || !avoidedTags.isEmpty();
    }

    /**
     * Checks a recipe against every active requirement.
     *
     * <p>Unclassified recipes are rejected whenever any restriction is active. Classification is based
     * on declared categories; ingredient names are not used to infer missing food tags.
     *
     * @param recipe The recipe to evaluate without modifying it.
     * @return True if the recipe satisfies the complete profile.
     * @throws NullPointerException If the recipe is null.
     */
    public boolean isAllowed(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return matchesRestrictions(recipe, recipe.getIngredients());
    }

    /**
     * Checks a planned meal using its source recipe's category declaration and its effective ingredients.
     *
     * @param meal The planned meal to evaluate without modifying it.
     * @return True if its source classification and effective ingredients satisfy the complete profile.
     * @throws NullPointerException If the meal is null.
     */
    public boolean isPlannedMealAllowed(PlannedMeal meal) {
        Objects.requireNonNull(meal, "meal");
        return matchesRestrictions(meal.getRecipe(), meal.getIngredients());
    }

    /**
     * Checks source classification together with the ingredients effective for one occurrence.
     */
    private boolean matchesRestrictions(Recipe recipe, Collection<GroceryItem> ingredients) {
        if (!isRestricted()) {
            return true;
        }
        FoodInfo foodInfo = recipe.getFoodInfo();
        if (!foodInfo.isClassified()) {
            return false;
        }
        for (FoodTag tag : foodInfo.tags()) {
            if (diet.isExcluded(tag) || avoidedTags.contains(tag)) {
                return false;
            }
        }
        return ingredients.stream()
                .noneMatch(item -> excludedIngredients.contains(normalizeIngredient(item.getName())));
    }

    /**
     * Normalizes a complete ingredient name for exact matching.
     *
     * @param name The ingredient name to normalize.
     * @return The lowercase name with trimmed and collapsed whitespace.
     * @throws NullPointerException If the name is null.
     */
    private static String normalizeIngredient(String name) {
        return Objects.requireNonNull(name, "ingredient").trim()
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Formats the current profile in a stable order for {@code view-diet}.
     *
     * @return The diet and sorted exclusion lists, with explicit empty-state labels.
     */
    public String describe() {
        return String.join(System.lineSeparator(),
                "Diet: " + diet.getLabel(),
                "Avoided food categories: " + formatValues(avoidedTags.stream().map(FoodTag::getLabel).toList()),
                "Excluded ingredients: " + formatValues(excludedIngredients));
    }

    /**
     * Formats sorted labels while making an empty exclusion list explicit.
     *
     * @param values The normalized labels to display.
     * @return Comma-separated sorted labels, or {@code none} when empty.
     */
    private static String formatValues(Collection<String> values) {
        return values.isEmpty() ? "none" : values.stream().sorted().collect(Collectors.joining(", "));
    }
}
