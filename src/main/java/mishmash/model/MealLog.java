package mishmash.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

//@@author GauthamNandi
/**
 * Holds retained meals the user has recorded as eaten, in the order they were recorded.
 *
 * <p>This is separate from the active meal plan, which holds meals the user intends to eat. A recorded meal
 * keeps the recipe's nutrition as it was when it was recorded, because {@link Recipe} cannot be changed.
 * The storage layer retains the log as part of a complete application snapshot.</p>
 */
public class MealLog {
    private final List<MealEntry> entries = new ArrayList<>();

    /**
     * Adds an entry to the end of the log.
     *
     * @param entry The meal that was eaten.
     * @throws NullPointerException If the entry is null.
     */
    public void record(MealEntry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    /**
     * Replaces the entry at a position, keeping its place in the log so other meal numbers do not change.
     *
     * @param index The zero-based position.
     * @param entry The corrected entry.
     * @throws NullPointerException If the entry is null.
     * @throws IndexOutOfBoundsException If the position does not exist.
     */
    public void replace(int index, MealEntry entry) {
        entries.set(index, Objects.requireNonNull(entry, "entry"));
    }

    /**
     * Removes the entry at a position; later entries move up by one.
     *
     * @param index The zero-based position.
     * @return The removed entry.
     * @throws IndexOutOfBoundsException If the position does not exist.
     */
    public MealEntry remove(int index) {
        return entries.remove(index);
    }

    /**
     * Returns a read-only snapshot of the recorded entries, oldest recording first.
     * Later changes do not appear in a snapshot that has already been returned.
     */
    public List<MealEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Adds up the nutrition of every recorded meal, recomputing it from the log each time so it cannot
     * differ from the recorded meals.
     *
     * @return The totals, which are all zero if no meal has been recorded.
     */
    public NutritionTotals getTotals() {
        return sum(entries);
    }

    /**
     * Adds up the nutrition of the meals recorded for one date.
     *
     * @param date The date to total.
     * @return The totals, which are all zero if no meal was recorded for that date.
     * @throws NullPointerException If the date is null.
     */
    public NutritionTotals getTotalsOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return sum(entries.stream().filter(entry -> entry.date().equals(date)).toList());
    }

    /**
     * Sums entries as long values, so several valid integer values cannot overflow an int.
     */
    private static NutritionTotals sum(List<MealEntry> selected) {
        List<NutritionTotals> nutrition = selected.stream().map(MealEntry::nutrition).toList();
        return new NutritionTotals(
                nutrition.stream().mapToLong(NutritionTotals::calories).sum(),
                nutrition.stream().mapToLong(NutritionTotals::protein).sum(),
                nutrition.stream().mapToLong(NutritionTotals::carbs).sum(),
                nutrition.stream().mapToLong(NutritionTotals::fats).sum());
    }
}
//@@author
