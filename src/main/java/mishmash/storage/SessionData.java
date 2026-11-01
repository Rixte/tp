package mishmash.storage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import mishmash.model.ActiveMealPlan;
import mishmash.model.ApplicationState;
import mishmash.model.DayPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.DietarySettings;
import mishmash.model.FoodInfo;
import mishmash.model.FoodTag;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealLog;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.PlannedMeal;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.model.TargetSettings;

/** Defines the versioned JSON representation without coupling domain classes to Jackson. */
record SessionData(int schemaVersion, List<RecipeData> recipes, DietaryData dietaryPreferences,
                   TargetsData dailyTargets, ActivePlanData activePlan, List<RecipeData> recordedMeals) {
    static final int CURRENT_SCHEMA_VERSION = 1;

    /** Converts all live model roots into an immutable persistence snapshot. */
    static SessionData fromState(ApplicationState state) {
        Objects.requireNonNull(state, "state");
        return new SessionData(CURRENT_SCHEMA_VERSION,
                state.getRecipeBook().getRecipes().stream().map(RecipeData::fromRecipe).toList(),
                DietaryData.fromPreferences(state.getDietarySettings().get()),
                TargetsData.fromTargets(state.getTargetSettings().get()),
                ActivePlanData.fromActivePlan(state.getActiveMealPlan()),
                state.getMealLog().getMeals().stream().map(RecipeData::fromRecipe).toList());
    }

    /** Reconstructs domain objects through their validating constructors and methods. */
    ApplicationState toState() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported data schema version: " + schemaVersion + ".");
        }

        RecipeBook recipeBook = new RecipeBook();
        for (RecipeData recipe : requiredList(recipes, "recipes")) {
            recipeBook.add(required(recipe, "recipe").toRecipe());
        }

        DietarySettings dietarySettings = new DietarySettings();
        dietarySettings.set(required(dietaryPreferences, "dietaryPreferences").toPreferences());

        TargetSettings targetSettings = new TargetSettings();
        targetSettings.set(required(dailyTargets, "dailyTargets").toTargets());

        ActiveMealPlan activeMealPlan = new ActiveMealPlan();
        if (activePlan != null) {
            activePlan.restoreInto(activeMealPlan);
        }

        MealLog mealLog = new MealLog();
        for (RecipeData meal : requiredList(recordedMeals, "recordedMeals")) {
            mealLog.record(required(meal, "recorded meal").toRecipe());
        }

        return new ApplicationState(recipeBook, activeMealPlan, dietarySettings, targetSettings, mealLog);
    }

    /** Rejects missing values with a field-specific diagnostic. */
    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required data field: " + field + ".");
        }
        return value;
    }

    /** Rejects missing lists and null elements before conversion. */
    private static <T> List<T> requiredList(List<T> values, String field) {
        required(values, field);
        for (T value : values) {
            required(value, field + " item");
        }
        return List.copyOf(values);
    }

    /** Stores one grocery or ingredient item without losing decimal scale or precision. */
    record GroceryData(String name, String quantity, String unit) {
        static GroceryData fromItem(GroceryItem item) {
            return new GroceryData(item.getName(), item.getQuantity().toPlainString(), item.getUnit());
        }

        GroceryItem toItem() {
            try {
                return new GroceryItem(required(name, "grocery name"),
                        new BigDecimal(required(quantity, "grocery quantity")),
                        required(unit, "grocery unit"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid grocery quantity: " + quantity + ".", e);
            }
        }
    }

    /** Stores the complete immutable value of a recipe snapshot. */
    record RecipeData(String name, int calories, int protein, int carbs, int fats,
                      List<GroceryData> ingredients, boolean foodClassified, List<String> foodTags) {
        static RecipeData fromRecipe(Recipe recipe) {
            FoodInfo foodInfo = recipe.getFoodInfo();
            return new RecipeData(recipe.getName(), recipe.getCalories(), recipe.getProtein(), recipe.getCarbs(),
                    recipe.getFats(), recipe.getIngredients().stream().map(GroceryData::fromItem).toList(),
                    foodInfo.isClassified(),
                    foodInfo.tags().stream().map(FoodTag::getLabel).sorted().toList());
        }

        Recipe toRecipe() {
            List<GroceryItem> restoredIngredients = requiredList(ingredients, "recipe ingredients").stream()
                    .map(GroceryData::toItem).toList();
            Set<FoodTag> restoredTags = requiredList(foodTags, "foodTags").stream()
                    .map(FoodTag::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new Recipe(required(name, "recipe name"), calories, protein, carbs, fats,
                    restoredIngredients, new FoodInfo(foodClassified, restoredTags));
        }
    }

    /** Stores normalized dietary settings using stable public labels. */
    record DietaryData(String diet, List<String> avoidedTags, List<String> excludedIngredients) {
        static DietaryData fromPreferences(DietaryPreferences preferences) {
            return new DietaryData(preferences.diet().getLabel(),
                    preferences.avoidedTags().stream().map(FoodTag::getLabel).sorted().toList(),
                    preferences.excludedIngredients().stream().sorted().toList());
        }

        DietaryPreferences toPreferences() {
            Set<FoodTag> restoredTags = requiredList(avoidedTags, "avoidedTags").stream()
                    .map(FoodTag::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> restoredIngredients = Set.copyOf(requiredList(excludedIngredients,
                    "excludedIngredients"));
            return new DietaryPreferences(Diet.parse(required(diet, "diet")), restoredTags, restoredIngredients);
        }
    }

    /** Stores nullable nutrition targets without sentinel values. */
    record TargetsData(Integer calories, Integer protein, Integer carbs, Integer fats) {
        static TargetsData fromTargets(NutritionTargets targets) {
            return new TargetsData(targets.getCalories(), targets.getProtein(), targets.getCarbs(), targets.getFats());
        }

        NutritionTargets toTargets() {
            return new NutritionTargets(calories, protein, carbs, fats);
        }
    }

    /** Stores one planned occurrence and its optional ingredient override. */
    record PlannedMealData(RecipeData recipe, int portionCount, List<GroceryData> customizedIngredients) {
        static PlannedMealData fromMeal(PlannedMeal meal) {
            List<GroceryData> customization = meal.hasCustomizedIngredients()
                    ? meal.getIngredients().stream().map(GroceryData::fromItem).toList()
                    : null;
            return new PlannedMealData(RecipeData.fromRecipe(meal.getRecipe()), meal.getPortionCount(),
                    customization);
        }

        PlannedMeal toMeal() {
            PlannedMeal restored = new PlannedMeal(required(recipe, "planned recipe").toRecipe(), portionCount);
            if (customizedIngredients == null) {
                return restored;
            }
            List<GroceryItem> customization = requiredList(customizedIngredients, "customizedIngredients")
                    .stream().map(GroceryData::toItem).toList();
            PlannedMeal customized = restored.withIngredients(customization);
            if (!customized.hasCustomizedIngredients()) {
                throw new IllegalArgumentException("A saved customization must differ from its source recipe.");
            }
            return customized;
        }
    }

    /** Stores one day's ordered planned occurrences. */
    record DayData(List<PlannedMealData> meals) {
        static DayData fromDay(DayPlan day) {
            return new DayData(day.getPlannedMeals().stream().map(PlannedMealData::fromMeal).toList());
        }

        DayPlan toDay() {
            return DayPlan.fromPlannedMeals(requiredList(meals, "planned meals").stream()
                    .map(PlannedMealData::toMeal).toList());
        }
    }

    /** Stores the generated plan together with the request and optional grocery snapshot it belongs to. */
    record ActivePlanData(GenerateRequestData request, List<DayData> days, List<GroceryData> groceries) {
        static ActivePlanData fromActivePlan(ActiveMealPlan activePlan) {
            Optional<MealPlan> plan = activePlan.get();
            if (plan.isEmpty()) {
                if (activePlan.getRequest().isPresent() || activePlan.getGroceryList().isPresent()) {
                    throw new IllegalStateException("Plan metadata cannot exist without an active plan.");
                }
                return null;
            }
            GeneratePlanRequest request = activePlan.getRequest().orElseThrow(
                    () -> new IllegalStateException("A retained active plan must have its generation request."));
            List<GroceryData> groceries = activePlan.getGroceryList()
                    .map(items -> items.stream().map(GroceryData::fromItem).toList()).orElse(null);
            return new ActivePlanData(GenerateRequestData.fromRequest(request),
                    plan.get().getDays().stream().map(DayData::fromDay).toList(), groceries);
        }

        void restoreInto(ActiveMealPlan destination) {
            GeneratePlanRequest restoredRequest = required(request, "generation request").toRequest();
            MealPlan restoredPlan = new MealPlan(requiredList(days, "plan days").stream()
                    .map(DayData::toDay).toList());
            Optional<List<GroceryItem>> restoredGroceries = groceries == null
                    ? Optional.empty()
                    : Optional.of(requiredList(groceries, "groceries").stream().map(GroceryData::toItem).toList());
            destination.setGenerated(restoredPlan, restoredRequest, restoredGroceries);
        }
    }

    /** Stores the request needed for dietary repair and target warnings. */
    record GenerateRequestData(int days, TargetsData targets) {
        static GenerateRequestData fromRequest(GeneratePlanRequest request) {
            return new GenerateRequestData(request.days(), TargetsData.fromTargets(request.targets()));
        }

        GeneratePlanRequest toRequest() {
            return new GeneratePlanRequest(days, required(targets, "generation targets").toTargets());
        }
    }
}
