package mishmash.command;

import java.util.Optional;

import mishmash.exception.MishMashException;
import mishmash.model.RecipeBook;

/**
 * Represents an application action, such as adding a recipe or viewing generated data.
 */
public interface Command {
    /**
     * Returns a question the user must answer before this action runs, for actions that cannot be undone.
     * Most commands need no confirmation, so the default is to ask nothing. The caller shows the question,
     * reads the answer, and calls {@link #execute(RecipeBook)} only if the user agrees.
     *
     * @param recipeBook The recipe book the action would change; it is not modified.
     * @return The non-blank question to ask, or empty if the action can run immediately.
     * @throws MishMashException If the action could not be carried out, so there is nothing to confirm.
     */
    default Optional<String> confirmationPrompt(RecipeBook recipeBook) throws MishMashException {
        return Optional.empty();
    }

    /**
     * Executes the action and returns feedback for display.
     *
     * @param recipeBook The recipe book available to the command.
     * @return The feedback to display to the user.
     * @throws MishMashException If the request cannot be executed with the current application data.
     */
    String execute(RecipeBook recipeBook) throws MishMashException;

    /**
     * Executes the action and reports its presentation status explicitly.
     *
     * <p>The safe default is unlabelled information. State-changing commands override this method to report
     * success, no-op, or warning outcomes without requiring the formatter to inspect class names or message text.
     *
     * @param recipeBook The recipe book available to the command.
     * @return The feedback and its semantic presentation status.
     * @throws MishMashException If the request cannot be executed with the current application data.
     */
    default CommandResult executeWithResult(RecipeBook recipeBook) throws MishMashException {
        String feedback = execute(recipeBook);
        return feedback == null ? null : CommandResult.info(feedback);
    }
}
