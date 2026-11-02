package mishmash.command;

import java.util.Objects;

import mishmash.model.Nutrient;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.model.TargetChanges;
import mishmash.model.TargetSettings;

//@@author neil0618
/**
 * Saves daily calorie and macronutrient targets for later meal plans.
 *
 * <p>Only the nutrients named in the command change. The command does not touch the active meal plan or
 * the recipe book: a plan that already exists keeps the targets it was generated with.</p>
 */
public class SetTargetsCommand implements Command {
    public static final String COMMAND_WORD = "set-targets";

    private final TargetChanges changes;
    private final TargetSettings settings;

    /**
     * Creates a command that applies the changes to the shared session targets when executed.
     *
     * @param changes The validated changes.
     * @param settings The targets shared with {@code generate-plan} and {@code view-targets}.
     * @throws NullPointerException If any argument is null.
     */
    public SetTargetsCommand(TargetChanges changes, TargetSettings settings) {
        this.changes = Objects.requireNonNull(changes, "changes");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Applies the changes to the saved targets.
     *
     * @param recipeBook The shared command context; it is not used or modified.
     * @return A confirmation listing every target now saved, or a note that nothing changed.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        NutritionTargets current = settings.get();
        NutritionTargets updated = changes.applyTo(current);
        if (hasSameTargets(current, updated)) {
            return CommandResult.note("Daily targets are unchanged. Nothing was changed.");
        }
        settings.set(updated);
        String saved = updated.hasAnyTarget()
                ? "Daily targets saved: " + updated.summarise() + "."
                : "All daily targets cleared.";
        return CommandResult.done(saved + System.lineSeparator()
                + "They apply to the next 'generate-plan'; your current meal plan is unchanged.");
    }

    /**
     * Returns whether two sets of targets hold the same value for every nutrient.
     *
     * @param first The first targets.
     * @param second The second targets.
     * @return True if no nutrient differs between them.
     */
    private static boolean hasSameTargets(NutritionTargets first, NutritionTargets second) {
        for (Nutrient nutrient : Nutrient.values()) {
            if (!Objects.equals(first.get(nutrient), second.get(nutrient))) {
                return false;
            }
        }
        return true;
    }
}
//@@author
