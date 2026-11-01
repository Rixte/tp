package mishmash.model;

import java.util.Objects;

/**
 * Owns the mutable model roots that make up one MishMash user's retained data.
 * Keeping these references together prevents the parser, application, and storage layer from
 * accidentally using different instances of what should be shared state.
 */
public final class ApplicationState {
    private final RecipeBook recipeBook;
    private final ActiveMealPlan activeMealPlan;
    private final DietarySettings dietarySettings;
    private final TargetSettings targetSettings;
    private final MealLog mealLog;

    /** Creates an empty application state. */
    public ApplicationState() {
        this(new RecipeBook(), new ActiveMealPlan(), new DietarySettings(), new TargetSettings(), new MealLog());
    }

    /**
     * Creates application state from validated model roots.
     *
     * @param recipeBook The ordered recipe collection.
     * @param activeMealPlan The active plan, request, and grocery snapshot.
     * @param dietarySettings The current dietary profile.
     * @param targetSettings The saved daily targets.
     * @param mealLog The recorded meal snapshots.
     * @throws NullPointerException If any model root is null.
     */
    public ApplicationState(RecipeBook recipeBook, ActiveMealPlan activeMealPlan,
            DietarySettings dietarySettings, TargetSettings targetSettings, MealLog mealLog) {
        this.recipeBook = Objects.requireNonNull(recipeBook, "recipeBook");
        this.activeMealPlan = Objects.requireNonNull(activeMealPlan, "activeMealPlan");
        this.dietarySettings = Objects.requireNonNull(dietarySettings, "dietarySettings");
        this.targetSettings = Objects.requireNonNull(targetSettings, "targetSettings");
        this.mealLog = Objects.requireNonNull(mealLog, "mealLog");
    }

    /** Returns the shared recipe book. */
    public RecipeBook getRecipeBook() {
        return recipeBook;
    }

    /** Returns the shared active meal-plan holder. */
    public ActiveMealPlan getActiveMealPlan() {
        return activeMealPlan;
    }

    /** Returns the shared dietary settings. */
    public DietarySettings getDietarySettings() {
        return dietarySettings;
    }

    /** Returns the shared daily-target settings. */
    public TargetSettings getTargetSettings() {
        return targetSettings;
    }

    /** Returns the shared recorded-meal log. */
    public MealLog getMealLog() {
        return mealLog;
    }
}
