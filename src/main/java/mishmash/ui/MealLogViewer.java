package mishmash.ui;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import mishmash.model.MealEntry;
import mishmash.model.NutritionTotals;

//@@author GauthamNandi
/**
 * Formats recorded meals and their nutrition for the meal-tracking commands without changing them.
 */
public class MealLogViewer {
    private static final String HEADER = "================ Recorded Meals ================";
    private static final String DIVIDER = "-".repeat(HEADER.length());
    private static final String FOOTER = "=".repeat(HEADER.length());

    /**
     * Returns every recorded meal in order with its own nutrition, followed by the totals of all of them.
     *
     * @param entries The whole meal log, oldest recording first.
     * @param totals The nutrition summed over those meals.
     * @return The formatted meals and totals without a trailing line separator.
     * @throws NullPointerException If either argument, or an entry, is null.
     */
    public String view(List<MealEntry> entries, NutritionTotals totals) {
        return render(entries, entry -> true, formatTotals("Total", totals));
    }

    /**
     * Returns the meals recorded for one date, followed by that date's totals.
     *
     * <p>Meals keep their numbers from the whole log, so a number shown here can be used directly with
     * {@code edit-meal} and {@code delete-meal}.</p>
     *
     * @param entries The whole meal log, oldest recording first.
     * @param date The date to show.
     * @param totals The nutrition summed over that date's meals.
     * @return The formatted meals and totals without a trailing line separator.
     * @throws NullPointerException If any argument, or an entry, is null.
     */
    public String viewDate(List<MealEntry> entries, LocalDate date, NutritionTotals totals) {
        Objects.requireNonNull(date, "date");
        return render(entries, entry -> date.equals(entry.date()), formatTotals("Total for " + date, totals));
    }

    /**
     * Returns the feedback shown right after a meal is recorded: the meal, then the totals for its date.
     *
     * @param entry The meal that was just recorded.
     * @param dayTotals The nutrition summed over every meal on that date, including this one.
     * @return The confirmation without a trailing line separator.
     * @throws NullPointerException If either argument is null.
     */
    public String viewRecorded(MealEntry entry, NutritionTotals dayTotals) {
        return "Meal recorded!" + System.lineSeparator()
                + describe(entry) + System.lineSeparator()
                + formatTotals("Total for " + entry.date(), dayTotals);
    }

    /**
     * Returns the feedback shown after a meal is corrected: the meal, then the totals for its date.
     *
     * @param entry The corrected meal.
     * @param dayTotals The nutrition summed over every meal on the corrected meal's date.
     * @return The confirmation without a trailing line separator.
     * @throws NullPointerException If either argument is null.
     */
    public String viewEdited(MealEntry entry, NutritionTotals dayTotals) {
        return "Meal updated!" + System.lineSeparator()
                + describe(entry) + System.lineSeparator()
                + formatTotals("Total for " + entry.date(), dayTotals);
    }

    /**
     * Returns the feedback shown after a meal is deleted: the meal, then the remaining totals for its date.
     *
     * @param entry The deleted meal.
     * @param dayTotals The nutrition summed over the meals still recorded on that date.
     * @return The confirmation without a trailing line separator.
     * @throws NullPointerException If either argument is null.
     */
    public String viewDeleted(MealEntry entry, NutritionTotals dayTotals) {
        return "Meal deleted!" + System.lineSeparator()
                + describe(entry) + System.lineSeparator()
                + formatTotals("Total for " + entry.date(), dayTotals);
    }

    /**
     * Describes one meal on a single line, in the same layout as the meal-plan display.
     * The nutrition shown is for all of the servings eaten.
     *
     * @param entry The meal to describe.
     * @return The meal's name, nutrition, serving count and date.
     * @throws NullPointerException If the entry is null.
     */
    public String describe(MealEntry entry) {
        Objects.requireNonNull(entry, "entry");
        NutritionTotals nutrition = entry.nutrition();
        String servings = entry.servings() + (entry.servings() == 1 ? " serving" : " servings");
        return entry.recipe().getName() + " (" + nutrition.calories() + " kcal | P: " + nutrition.protein()
                + "g, C: " + nutrition.carbs() + "g, F: " + nutrition.fats() + "g) | " + servings
                + " | " + entry.date();
    }

    /**
     * Lays out the selected meals between the header and the totals, numbering them by log position.
     */
    private String render(List<MealEntry> entries, Predicate<MealEntry> isShown, String totalsLine) {
        Objects.requireNonNull(entries, "entries");

        StringBuilder result = new StringBuilder(HEADER);
        int mealNumber = 1;
        for (MealEntry entry : entries) {
            if (isShown.test(Objects.requireNonNull(entry, "entry"))) {
                result.append(System.lineSeparator())
                        .append("Meal ").append(mealNumber).append(": ").append(describe(entry));
            }
            mealNumber++;
        }
        result.append(System.lineSeparator()).append(DIVIDER)
                .append(System.lineSeparator()).append(totalsLine)
                .append(System.lineSeparator()).append(FOOTER);
        return result.toString();
    }

    /**
     * Formats a labelled totals line.
     *
     * @param label The text before the colon, such as {@code Total}.
     * @throws NullPointerException If the totals are null.
     */
    private String formatTotals(String label, NutritionTotals totals) {
        Objects.requireNonNull(totals, "totals");
        return label + ": " + totals.calories() + " kcal | Protein: " + totals.protein()
                + "g | Carbs: " + totals.carbs() + "g | Fats: " + totals.fats() + "g";
    }
}
//@@author
