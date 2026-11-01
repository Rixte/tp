package mishmash.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Holds the active meal plan and its generated grocery snapshot in memory.
 *
 * <p>Only one plan is active at a time; generating a new plan replaces the old one.
 * The storage layer retains the plan as part of a complete application snapshot.</p>
 *
 * <p>This is a plain object rather than a static singleton so that the app can
 * create one instance and share it, and tests can create a fresh one each time.</p>
 */
public class ActiveMealPlan {
    private MealPlan plan;
    /** Original generation settings; absent for no plan or a legacy supplied plan. */
    private Optional<GeneratePlanRequest> request = Optional.empty();
    // Absence means groceries have not been generated for the current plan.
    private Optional<List<GroceryItem>> groceryList = Optional.empty();

    /**
     * Makes the given plan active and clears groceries derived from the previous plan.
     *
     * @param plan The new active plan.
     * @throws NullPointerException If the plan is null.
     */
    public void set(MealPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        request = Optional.empty();
        groceryList = Optional.empty();
    }

    /**
     * Replaces the current plan after a local edit while retaining its generation settings.
     * Clears groceries because they were derived from the previous plan contents.
     *
     * <p>Validation completes before any field changes, so a rejected replacement preserves all state.
     *
     * @param replacement The edited plan with the same number of days as the active plan.
     * @throws NullPointerException If the replacement is null.
     * @throws IllegalStateException If no plan is active.
     * @throws IllegalArgumentException If the replacement changes the plan length.
     */
    public void update(MealPlan replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (plan == null) {
            throw new IllegalStateException("An active meal plan is required before it can be updated.");
        }
        if (replacement.getNumberOfDays() != plan.getNumberOfDays()) {
            throw new IllegalArgumentException("An in-place plan update must preserve the number of days.");
        }
        if (request.isPresent() && replacement.getNumberOfDays() != request.get().days()) {
            throw new IllegalArgumentException("Updated plan length must match its generation request.");
        }
        if (replacement == plan) {
            return;
        }

        plan = replacement;
        groceryList = Optional.empty();
    }

    /**
     * Returns the original settings retained for dietary plan repair.
     *
     * @return The generation request, or an empty Optional for no plan or a legacy supplied plan.
     */
    public Optional<GeneratePlanRequest> getRequest() {
        return request;
    }

    /**
     * Publishes a generated plan, its original settings, and a copied grocery snapshot.
     *
     * <p>All validation and copying finish before existing state changes, so failed validation preserves
     * the previous plan, request, and groceries. An empty Optional means groceries are not generated.
     *
     * @param plan The replacement active plan.
     * @param request The original number of days and daily nutrition targets.
     * @param groceries The optional grocery snapshot corresponding to the replacement plan.
     * @throws NullPointerException If an argument or grocery item is null.
     * @throws IllegalArgumentException If the plan length differs from the request.
     */
    public void setGenerated(MealPlan plan, GeneratePlanRequest request,
            Optional<List<GroceryItem>> groceries) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(request, "request");
        if (plan.getNumberOfDays() != request.days()) {
            throw new IllegalArgumentException("Plan length must match its generation request.");
        }
        Optional<List<GroceryItem>> snapshot = Objects.requireNonNull(groceries, "groceries").map(List::copyOf);
        this.plan = plan;
        this.request = Optional.of(request);
        this.groceryList = snapshot;
    }

    /**
     * Clears the plan, its generation request, and all derived groceries.
     *
     * <p>Calling this method when no plan is active has no additional effect.
     */
    public void clear() {
        plan = null;
        request = Optional.empty();
        groceryList = Optional.empty();
    }

    /**
     * Returns the active plan, or an empty Optional if no plan has been generated yet.
     */
    public Optional<MealPlan> get() {
        return Optional.ofNullable(plan);
    }

    /**
     * Stores a read-only snapshot after grocery generation succeeds.
     * Copying before assignment preserves the old list if validation fails.
     *
     * @param items The generated groceries in display order.
     * @throws NullPointerException If the list or any item is null.
     */
    public void setGroceryList(List<GroceryItem> items) {
        groceryList = Optional.of(List.copyOf(items));
    }

    /**
     * Returns the generated snapshot, distinguishing an empty list from no generated list.
     *
     * @return The optional read-only grocery list for the active plan.
     */
    public Optional<List<GroceryItem>> getGroceryList() {
        return groceryList;
    }
}
