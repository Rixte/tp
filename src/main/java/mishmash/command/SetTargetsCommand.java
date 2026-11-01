package mishmash.command;

import java.util.Objects;

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
     * @return A confirmation listing every target now saved.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        NutritionTargets updated = changes.applyTo(settings.get());
        settings.set(updated);
        String saved = updated.hasAnyTarget()
                ? "Daily targets saved: " + updated.summarise() + "."
                : "All daily targets cleared.";
        return saved + System.lineSeparator()
                + "They apply to the next 'generate-plan'; your current meal plan is unchanged.";
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        return CommandResult.done(execute(recipeBook));
    }
}
//@@author
