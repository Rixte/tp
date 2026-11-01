package mishmash.command;

import java.util.Objects;
import java.util.logging.Logger;

import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.ui.RecipeViewer;

//@@author peilingggg
/**
 * Adds a validated recipe and returns its confirmation.
 */
public class AddRecipeCommand implements Command {
    /**
     * Records successful additions without including recipe contents.
     */
    private static final Logger LOGGER = Logger.getLogger(AddRecipeCommand.class.getName());

    private final Recipe recipe;

    /**
     * Creates a command that adds the supplied recipe.
     *
     * @param recipe The validated recipe to add.
     * @throws NullPointerException If the recipe is null.
     */
    public AddRecipeCommand(Recipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
    }

    @Override
    public String execute(RecipeBook recipeBook) {
        Objects.requireNonNull(recipeBook, "recipeBook");
        // Prepare the output first so a formatting failure cannot leave an unconfirmed addition.
        String confirmation = "New recipe added!" + System.lineSeparator() + new RecipeViewer().view(recipe);
        recipeBook.add(recipe);
        LOGGER.fine(() -> "Recipe added; recipe count: " + recipeBook.size());
        return confirmation;
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        return CommandResult.done(execute(recipeBook));
    }
}
//@@author

