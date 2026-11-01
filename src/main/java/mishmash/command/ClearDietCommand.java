package mishmash.command;

import java.util.Objects;
import java.util.logging.Logger;

import mishmash.model.DietarySettings;
import mishmash.model.RecipeBook;

/**
 * Clears every dietary preference and restriction without changing other session data.
 */
public final class ClearDietCommand implements Command {
    /** Command word shared by parsing and user guidance. */
    public static final String COMMAND_WORD = "clear-diet";

    /** Syntax for a command that deliberately accepts no arguments. */
    public static final String USAGE = "Format: " + COMMAND_WORD;

    private static final Logger LOGGER = Logger.getLogger(ClearDietCommand.class.getName());

    private final DietarySettings dietarySettings;

    /**
     * Creates a command connected only to dietary settings, preventing accidental plan mutation.
     *
     * @param dietarySettings The session profile to reset to unrestricted.
     * @throws NullPointerException If the settings holder is null.
     */
    public ClearDietCommand(DietarySettings dietarySettings) {
        this.dietarySettings = Objects.requireNonNull(dietarySettings, "dietarySettings");
    }

    /**
     * Resets the dietary profile while preserving recipes, plans, groceries, targets, and meal records.
     *
     * @param recipeBook The shared command context; it is validated but not changed.
     * @return A confirmation, or an idempotent message when no restrictions were set.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        boolean hadRestrictions = dietarySettings.clear();
        if (!hadRestrictions) {
            LOGGER.fine("clear-diet requested for an already unrestricted profile");
            return CommandResult.note("Dietary preferences are already clear. Nothing was changed.");
        }

        LOGGER.fine("Cleared all dietary preferences and restrictions");
        return CommandResult.done("Dietary preferences and restrictions cleared."
                + System.lineSeparator()
                + "Recipes, meal plans, groceries, targets, and recorded meals are unchanged.");
    }
}
