package mishmash.command;

import java.util.Objects;
import java.util.logging.Logger;

import mishmash.exception.MishMashException;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.model.RecipeChanges;
import mishmash.ui.RecipeViewer;

//@@author Rixte
/**
 * Replaces one recipe in place using validated edits, keeping all omitted fields.
 */
public class EditRecipeCommand implements Command {
    /**
     * Records successful edits and rejected positions without including recipe contents.
     */
    private static final Logger LOGGER = Logger.getLogger(EditRecipeCommand.class.getName());

    private final int index;
    private final RecipeChanges changes;

    /**
     * Creates an edit that looks up its target only when executed.
     *
     * @param index The recipe position displayed to the user, starting at 1.
     * @param changes The validated fields to replace.
     * @throws IllegalArgumentException If the index is less than 1.
     * @throws NullPointerException If changes is null.
     */
    public EditRecipeCommand(int index, RecipeChanges changes) {
        if (index < 1) {
            throw new IllegalArgumentException("Recipe index must be at least 1.");
        }
        this.index = index;
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    /**
     * Validates the current position and prepares the complete result before replacing the recipe.
     * Existing plans and groceries retain their original snapshots.
     *
     * @param recipeBook The recipe book containing the selected recipe.
     * @return Confirmation showing every field of the edited recipe.
     * @throws MishMashException If the book is empty or the selected index does not exist.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");
        int originalSize = recipeBook.size();
        if (originalSize == 0) {
            LOGGER.fine("Recipe edit rejected: recipe book is empty");
            throw new MishMashException("There are no recipes to edit.");
        }
        if (index > originalSize) {
            LOGGER.fine(() -> "Recipe edit rejected: index " + index + "; recipe count: " + originalSize);
            throw new MishMashException("Recipe index must be between 1 and " + originalSize + ".");
        }

        Recipe edited = changes.applyTo(recipeBook.getRecipes().get(index - 1));
        String confirmation = "Recipe updated!" + System.lineSeparator() + new RecipeViewer().view(edited)
                + System.lineSeparator() + "Existing meal plans and grocery lists are unchanged."
                + " Generate a new plan and grocery list to use this edit.";
        recipeBook.replace(index - 1, edited);
        // Editing replaces an entry; adding or removing an entry here would invalidate displayed indices.
        assert recipeBook.size() == originalSize : "Editing must preserve the recipe count.";
        LOGGER.fine(() -> "Recipe edited at index " + index + "; recipe count: " + recipeBook.size());
        return confirmation;
    }
}
//@@author
