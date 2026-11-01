package mishmash.command;

import java.util.Objects;
import java.util.Optional;

import mishmash.exception.MishMashException;
import mishmash.model.Recipe;
import mishmash.model.RecipeBook;

//@@author neil0618
/**
 * Deletes the recipe at a position chosen by the user and returns its confirmation.
 */
public class DeleteRecipeCommand implements Command {
    private final int index;

    /**
     * Creates a command that deletes the recipe at the supplied position.
     *
     * @param index The position as typed by the user, where the first recipe is 1.
     * @throws IllegalArgumentException If the index is less than 1.
     */
    public DeleteRecipeCommand(int index) {
        if (index < 1) {
            throw new IllegalArgumentException("Recipe index must be at least 1.");
        }
        this.index = index;
    }

    /**
     * Names the recipe that would be deleted so the user can check the index they typed.
     * An index that does not exist is reported now, rather than after the user has confirmed.
     *
     * @param recipeBook The recipe book to look the recipe up in; it is not modified.
     * @return The yes/no question naming the recipe at the chosen index.
     * @throws MishMashException If the book is empty or the index is larger than the number of recipes.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public Optional<String> confirmationPrompt(RecipeBook recipeBook) throws MishMashException {
        Recipe target = findTarget(recipeBook);
        return Optional.of("Are you sure you want to delete recipe " + index + ": "
                + target.getName() + "? (y/n)");
    }

    /**
     * Deletes the recipe, leaving the book unchanged if the index does not exist.
     * This does not ask for confirmation itself; the caller does that using {@link #confirmationPrompt}.
     *
     * @param recipeBook The recipe book to delete from.
     * @return The deleted recipe's name and how many recipes remain.
     * @throws MishMashException If the book is empty or the index is larger than the number of recipes.
     * @throws NullPointerException If the recipe book is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) throws MishMashException {
        findTarget(recipeBook);

        // The user counts from 1, but the book counts from 0.
        Recipe deleted = recipeBook.remove(index - 1);

        return "Recipe deleted!" + System.lineSeparator()
                + deleted.getName() + System.lineSeparator()
                + "Recipes remaining: " + recipeBook.size();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        return CommandResult.done(execute(recipeBook));
    }

    /**
     * Looks up the recipe at the chosen index without changing the book.
     *
     * @param recipeBook The recipe book to look in.
     * @return The recipe the index points to.
     * @throws MishMashException If the book is empty or the index is larger than the number of recipes.
     */
    private Recipe findTarget(RecipeBook recipeBook) throws MishMashException {
        Objects.requireNonNull(recipeBook, "recipeBook");

        int recipeCount = recipeBook.size();
        if (recipeCount == 0) {
            throw new MishMashException("There are no recipes to delete.");
        }
        if (index > recipeCount) {
            throw new MishMashException("Recipe index must be between 1 and " + recipeCount + ".");
        }
        return recipeBook.getRecipes().get(index - 1);
    }
}
//@@author
