package mishmash.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.NutritionTotals;
import mishmash.model.Recipe;

/**
 * Generates meal plans of {@value #MEALS_PER_DAY} distinct recipes per day from a recipe list.
 *
 * <p>Randomness comes from an injected {@link Random}, so tests can pass a seeded
 * {@code new Random(42)} and get the same plan every run.</p>
 *
 * <p><b>Target-based generation.</b> When nutrition targets are given, the generator keeps
 * combinations of three recipes within ±15% of every target, then deals them out to the days.
 * If the recipe book is small enough (at most {@value #MAX_CANDIDATES} combinations, i.e. about
 * 180 recipes) every combination is checked, so a valid one is never missed. For larger books it
 * checks up to {@value #MAX_CANDIDATES} random combinations instead, which keeps the work fixed no
 * matter how many recipes there are. Each check only adds up nutrition, and a {@link DayPlan} is built
 * only for a combination that is kept, which is what makes this many checks cheap. A more advanced
 * alternative would be a local search that swaps meals to move towards the targets, but checking
 * combinations directly is simpler and good enough here.</p>
 */
public class MealPlanGenerator {
    /** Fewest days a plan can cover. */
    public static final int MIN_DAYS = 1;

    /** Most days a plan can cover. */
    public static final int MAX_DAYS = 7;

    /** Number of meals in each day of a plan. */
    public static final int MEALS_PER_DAY = 3;

    /** Upper bound on how many recipe combinations are checked for target-based plans. */
    public static final int MAX_CANDIDATES = 1_000_000;

    /** Upper bound on how many matching combinations are kept, which limits memory use. */
    public static final int MAX_MATCHES = 5000;

    /**
     * Counts the orderings of three distinct recipes so each unordered combination is counted once.
     */
    private static final int TRIPLE_PERMUTATION_COUNT = 6;

    /** The tolerance as shown to users, derived so the message cannot disagree with the rule. */
    private static final long TOLERANCE_PERCENT = Math.round(NutritionTargets.TOLERANCE * 100);

    private static final Logger LOGGER = Logger.getLogger(MealPlanGenerator.class.getName());

    private final Random random;

    /**
     * Creates a generator that uses the given source of randomness.
     *
     * @param random Source of randomness; pass a seeded instance for repeatable results.
     * @throws NullPointerException If the random source is null.
     */
    public MealPlanGenerator(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Generates a meal plan.
     *
     * <p>Within a day the three recipes are always different; the same recipe may
     * appear again on other days.</p>
     *
     * @param recipes The recipes to choose from.
     * @param days Number of days, from {@value #MIN_DAYS} to {@value #MAX_DAYS}.
     * @param targets Daily nutrition targets; use {@link NutritionTargets#none()} for a random plan.
     * @return The generated plan.
     * @throws NullPointerException If the recipes, any recipe, or the targets are null.
     * @throws IllegalArgumentException If the number of days is out of range.
     * @throws PlanGenerationException If there are too few recipes or no combination meets the targets.
     */
    public MealPlan generate(List<Recipe> recipes, int days, NutritionTargets targets) {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(targets, "targets");
        for (Recipe recipe : recipes) {
            Objects.requireNonNull(recipe, "recipe");
        }
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("Days must be between " + MIN_DAYS + " and " + MAX_DAYS + ".");
        }
        checkEnoughRecipes(recipes);
        LOGGER.fine(() -> "Generating meal plan: days=" + days + ", recipes=" + recipes.size()
                + ", targets supplied=" + targets.hasAnyTarget());

        MealPlan plan;
        if (!targets.hasAnyTarget()) {
            plan = generateRandomPlan(recipes, days);
        } else {
            plan = generateTargetedPlan(recipes, days, targets);
        }
        assert plan.getNumberOfDays() == days : "Generated plan must contain the requested number of days";
        return plan;
    }

    /**
     * Rejects recipe books that cannot supply distinct meals for one day.
     *
     * @param recipes The recipes available for generation.
     * @throws PlanGenerationException If there are fewer than {@value #MEALS_PER_DAY} recipes.
     */
    private static void checkEnoughRecipes(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            throw new PlanGenerationException(
                    "Your recipe book is empty. Add some recipes before generating a meal plan.");
        }
        if (recipes.size() < MEALS_PER_DAY) {
            throw new PlanGenerationException("You need at least " + MEALS_PER_DAY
                    + " recipes to generate a meal plan, but you only have " + recipes.size() + ".");
        }
    }

    /**
     * Generates each day by shuffling a copy of the recipes and selecting distinct meals.
     *
     * @param recipes The validated recipe list, which is left unchanged.
     * @param days The supported number of days to generate.
     * @return A plan with random meals and no nutrition constraints.
     */
    private MealPlan generateRandomPlan(List<Recipe> recipes, int days) {
        List<DayPlan> dayPlans = new ArrayList<>();
        List<Recipe> shuffled = new ArrayList<>(recipes);
        for (int i = 0; i < days; i++) {
            // Shuffling and taking the first few gives distinct recipes for the day.
            Collections.shuffle(shuffled, random);
            dayPlans.add(new DayPlan(shuffled.subList(0, MEALS_PER_DAY)));
        }
        return new MealPlan(dayPlans);
    }

    /**
     * Selects each day from the combinations that meet every supplied nutrition target.
     * Days only repeat once every kept combination has been used.
     *
     * @param recipes The validated recipe list.
     * @param days The supported number of days to generate.
     * @param targets The daily nutrition constraints.
     * @return A plan whose days all meet the supplied targets.
     * @throws PlanGenerationException If no combination that was checked satisfies the targets.
     */
    private MealPlan generateTargetedPlan(List<Recipe> recipes, int days, NutritionTargets targets) {
        assert recipes.size() >= MEALS_PER_DAY : "Recipe count should have been validated before matching";
        List<DayPlan> matching = checksEveryCombination(recipes.size())
                ? findAmongAllCombinations(recipes, targets)
                : findAmongRandomCombinations(recipes, targets);
        LOGGER.fine(() -> "Kept " + matching.size() + " combinations matching nutrition targets");
        if (matching.isEmpty()) {
            throw new PlanGenerationException("Could not find " + MEALS_PER_DAY
                    + " recipes that meet your daily targets within " + TOLERANCE_PERCENT + "%."
                    + " Try different targets or add more recipes.");
        }

        Collections.shuffle(matching, random);
        List<DayPlan> dayPlans = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dayPlans.add(matching.get(i % matching.size()));
        }
        return new MealPlan(dayPlans);
    }

    /**
     * Returns whether target-based generation checks every combination of this many recipes,
     * rather than a random sample, so that a failure proves no valid combination exists.
     *
     * @param recipeCount The number of recipes available.
     * @return True if the number of 3-recipe combinations is at most {@link #MAX_CANDIDATES}.
     */
    public static boolean checksEveryCombination(int recipeCount) {
        long combinationCount = (long) recipeCount * (recipeCount - 1) * (recipeCount - 2)
                / TRIPLE_PERMUTATION_COUNT;
        return combinationCount <= MAX_CANDIDATES;
    }

    //@@author GauthamNandi
    /**
     * Checks every combination of three recipes, so a valid one is never missed.
     * Every match has the same chance of being kept, however many there are (reservoir sampling).
     *
     * @param recipes The recipes from which to choose meals.
     * @param targets The daily nutrition constraints.
     * @return At most {@value #MAX_MATCHES} distinct matching days.
     */
    private List<DayPlan> findAmongAllCombinations(List<Recipe> recipes, NutritionTargets targets) {
        List<DayPlan> kept = new ArrayList<>();
        int found = 0;
        for (int i = 0; i < recipes.size(); i++) {
            for (int j = i + 1; j < recipes.size(); j++) {
                for (int k = j + 1; k < recipes.size(); k++) {
                    List<Recipe> meals = List.of(recipes.get(i), recipes.get(j), recipes.get(k));
                    if (targets.isSatisfiedBy(totalOf(meals))) {
                        found++;
                        keepFairly(kept, found, meals);
                    }
                }
            }
        }
        return kept;
    }

    /**
     * Keeps a match if there is room, and otherwise swaps it in with probability
     * {@value #MAX_MATCHES} out of {@code found}, so all matches are equally likely to be kept.
     */
    private void keepFairly(List<DayPlan> kept, int found, List<Recipe> meals) {
        if (kept.size() < MAX_MATCHES) {
            kept.add(new DayPlan(meals));
            return;
        }
        int slot = random.nextInt(found);
        if (slot < MAX_MATCHES) {
            kept.set(slot, new DayPlan(meals));
        }
    }

    /**
     * Checks random combinations when there are too many to check them all. Stops early once enough
     * matches are found; the draws are random, so stopping does not favour any recipe.
     *
     * @param recipes The recipes to draw from, which are left unchanged.
     * @param targets The daily nutrition constraints.
     * @return At most {@value #MAX_MATCHES} distinct matching days.
     */
    private List<DayPlan> findAmongRandomCombinations(List<Recipe> recipes, NutritionTargets targets) {
        List<DayPlan> matches = new ArrayList<>();
        // A combination can be drawn twice, so remember the matches to keep the days distinct.
        Set<List<Integer>> seen = new HashSet<>();
        for (int i = 0; i < MAX_CANDIDATES && matches.size() < MAX_MATCHES; i++) {
            int[] picks = random.ints(0, recipes.size()).distinct().limit(MEALS_PER_DAY).sorted().toArray();
            List<Recipe> meals = List.of(recipes.get(picks[0]), recipes.get(picks[1]), recipes.get(picks[2]));
            if (targets.isSatisfiedBy(totalOf(meals)) && seen.add(Arrays.stream(picks).boxed().toList())) {
                matches.add(new DayPlan(meals));
            }
        }
        return matches;
    }

    /**
     * Adds up the nutrition of one portion of each meal.
     */
    private static NutritionTotals totalOf(List<Recipe> meals) {
        long calories = 0;
        long protein = 0;
        long carbs = 0;
        long fats = 0;
        for (Recipe meal : meals) {
            calories += meal.getCalories();
            protein += meal.getProtein();
            carbs += meal.getCarbs();
            fats += meal.getFats();
        }
        return new NutritionTotals(calories, protein, carbs, fats);
    }
    //@@author
}
