package mishmash.command;

import java.util.Objects;

import mishmash.model.Nutrient;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.model.TargetSettings;

//@@author neil0618
/**
 * Shows the daily targets saved with {@code set-targets}. This command is read-only.
 */
public class ViewTargetsCommand implements Command {
    public static final String COMMAND_WORD = "view-targets";
    public static final String USAGE = "Format: " + COMMAND_WORD;

    private final TargetSettings settings;

    /**
     * Creates a command that reads the shared session targets when executed.
     *
     * @param settings The targets shared with {@code set-targets}.
     * @throws NullPointerException If the settings are null.
     */
    public ViewTargetsCommand(TargetSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Lists each nutrient with its saved target, or says that none are saved.
     *
     * @param recipeBook The shared command context; it is not used or modified.
     * @return The saved targets, one per line.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        NutritionTargets targets = settings.get();
        if (!targets.hasAnyTarget()) {
            return CommandResult.note("No daily targets saved. Use 'set-targets' to set them.");
        }
        StringBuilder text = new StringBuilder("Daily targets:");
        for (Nutrient nutrient : Nutrient.values()) {
            Integer target = targets.get(nutrient);
            text.append(System.lineSeparator()).append(nutrient.getLabel()).append(": ")
                    .append(target == null ? "not set" : target + " " + nutrient.getUnit());
        }
        return CommandResult.info(text.toString());
    }
}
//@@author
