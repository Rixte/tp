package mishmash.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

import mishmash.model.DayPlan;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.Recipe;

/**
 * Generates meal plans of {@value #MEALS_PER_DAY} distinct recipes per day from a recipe list.
 *
 * <p>Randomness comes from an injected {@link Random}, so tests can pass a seeded
 * {@code new Random(42)} and get the same plan every run.</p>
 *
 * <p><b>Target-based generation.</b> When nutrition targets are given, the generator
 * builds a bounded pool of candidate 3-recipe combinations, keeps the ones within ±15% of
 * every target, and picks one at random for each day. If the recipe book is small enough
 * (at most {@value #MAX_CANDIDATES} combinations, i.e. about 32 recipes) every combination is
 * checked, so a valid one is never missed. For larger books it checks
 * {@value #MAX_CANDIDATES} random combinations instead, which keeps the work fixed no matter
 * how many recipes there are. A more advanced alternative would be a local search that
 * swaps meals to move towards the targets, but sampling is simpler and good enough here.</p>
 */
public class MealPlanGenerator {
    /** Fewest days a plan can cover. */
    public static final int MIN_DAYS = 1;

    /** Most days a plan can cover. */
    public static final int MAX_DAYS = 7;

    /** Number of meals in each day of a plan. */
    public static final int MEALS_PER_DAY = 3;

    /** Upper bound on how many recipe combinations are checked for target-based plans. */
    public static final int MAX_CANDIDATES = 5000;

    /**
     * Counts the orderings of three distinct recipes so each unordered combination is counted once.
     */
    private static final int TRIPLE_PERMUTATION_COUNT = 6;

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
     * @throws IllegalArgumentException If the number of days is out of range, there are too few
     *         recipes, or no combination meets the targets.
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
     * @throws IllegalArgumentException If there are fewer than {@value #MEALS_PER_DAY} recipes.
     */
    private static void checkEnoughRecipes(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Your recipe book is empty. Add some recipes before generating a meal plan.");
        }
        if (recipes.size() < MEALS_PER_DAY) {
            throw new IllegalArgumentException("You need at least " + MEALS_PER_DAY
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
     * Selects each day from candidate meals that meet every supplied nutrition target.
     *
     * @param recipes The validated recipe list.
     * @param days The supported number of days to generate.
     * @param targets The daily nutrition constraints.
     * @return A plan whose days all meet the supplied targets.
     * @throws IllegalArgumentException If no candidate day satisfies the targets.
     */
    private MealPlan generateTargetedPlan(List<Recipe> recipes, int days, NutritionTargets targets) {
        List<DayPlan> matching = new ArrayList<>();
        for (DayPlan candidate : buildCandidates(recipes)) {
            if (targets.isSatisfiedBy(candidate)) {
                matching.add(candidate);
            }
        }
        LOGGER.fine(() -> "Found " + matching.size() + " candidate combinations matching nutrition targets");
        if (matching.isEmpty()) {
            throw new IllegalArgumentException("Could not find " + MEALS_PER_DAY
                    + " recipes that meet your daily targets within 15%."
                    + " Try different targets or add more recipes.");
        }

        List<DayPlan> dayPlans = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dayPlans.add(matching.get(random.nextInt(matching.size())));
        }
        return new MealPlan(dayPlans);
    }

    /**
     * Returns every 3-recipe combination if there are at most {@link #MAX_CANDIDATES},
     * otherwise {@link #MAX_CANDIDATES} randomly chosen combinations.
     */
    private List<DayPlan> buildCandidates(List<Recipe> recipes) {
        assert recipes.size() >= MEALS_PER_DAY
                : "Recipe count should have been validated before building candidates";
        if (checksEveryCombination(recipes.size())) {
            return enumerateCandidates(recipes);
        }
        return sampleCandidates(recipes);
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
     * Returns every combination of three recipes in their original index order, without duplicates.
     *
     * @param recipes The recipes from which to choose meals.
     * @return All candidate days, each containing three distinct recipes.
     */
    private List<DayPlan> enumerateCandidates(List<Recipe> recipes) {
        List<DayPlan> candidates = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            for (int j = i + 1; j < recipes.size(); j++) {
                for (int k = j + 1; k < recipes.size(); k++) {
                    candidates.add(new DayPlan(List.of(recipes.get(i), recipes.get(j), recipes.get(k))));
                }
            }
        }
        return candidates;
    }

    /**
     * Samples a bounded number of candidate days when checking every recipe combination is too expensive.
     *
     * @param recipes The recipes to sample without changing the original list.
     * @return {@value #MAX_CANDIDATES} candidate days, which may repeat combinations.
     */
    private List<DayPlan> sampleCandidates(List<Recipe> recipes) {
        List<DayPlan> candidates = new ArrayList<>();
        List<Recipe> shuffled = new ArrayList<>(recipes);
        for (int i = 0; i < MAX_CANDIDATES; i++) {
            Collections.shuffle(shuffled, random);
            candidates.add(new DayPlan(shuffled.subList(0, MEALS_PER_DAY)));
        }
        return candidates;
    }
    //@@author
}
