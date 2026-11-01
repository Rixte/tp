package mishmash.command;

import java.util.List;
import java.util.Objects;

import mishmash.exception.MishMashException;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;
import mishmash.ui.RecipeViewer;

//@@author neil0618
/**
 * Shows the recipes in the book: a short numbered list of all of them, or the full details of one.
 */
public class ListRecipeCommand implements Command {
    private final Integer index;

    /**
     * Creates a command that lists every recipe's number, name and calories.
     */
    public ListRecipeCommand() {
        index = null;
    }

    /**
     * Creates a command that shows the full details of one recipe.
     *
     * @param index The position as typed by the user, where the first recipe is 1.
     * @throws IllegalArgumentException If the index is less than 1.
     */
    public ListRecipeCommand(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("Recipe index must be at least 1.");
        }
        this.index = index;
    }

    /**
     * Builds the text to show, without changing the recipe book.
     *
     * @param recipeBook The recipe book to read from.
     * @return The numbered list of recipes, or the details of the chosen recipe.
     * @throws MishMashException If a recipe was chosen but the book is empty or has no recipe at that index.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");
        List<Recipe> recipes = recipeBook.getRecipes();

        if (index == null) {
            String feedback = listAll(recipes);
            return recipes.isEmpty() ? CommandResult.note(feedback) : CommandResult.info(feedback);
        }
        if (recipes.isEmpty()) {
            throw new MishMashException("There are no recipes to show. Use 'add-recipe' to add one.");
        }
        if (index > recipes.size()) {
            throw new MishMashException("Recipe index must be between 1 and " + recipes.size() + ".");
        }
        // The user counts from 1, but the list counts from 0.
        return CommandResult.info(new RecipeViewer().view(recipes.get(index - 1)));
    }

    /**
     * Lists each recipe on its own line with the number used by commands such as delete-recipe.
     */
    private String listAll(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            return "No recipes yet. Use 'add-recipe' to add one.";
        }
        StringBuilder result = new StringBuilder("Recipes:");
        for (int i = 0; i < recipes.size(); i++) {
            Recipe recipe = recipes.get(i);
            result.append(System.lineSeparator())
                    .append(i + 1).append(". ")
                    .append(recipe.getName())
                    .append(" (").append(recipe.getCalories()).append(" kcal)");
        }
        return result.toString();
    }
}
//@@author
