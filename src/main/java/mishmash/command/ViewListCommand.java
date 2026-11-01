package mishmash.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import mishmash.model.GroceryItem;
import mishmash.model.RecipeBook;
import mishmash.ui.GroceryListViewer;

//@@author Rixte
/**
 * Displays an already generated grocery list without generating or storing groceries.
 */
public class ViewListCommand implements Command {
    /**
     * Describes the command's syntax without arguments.
     */
    public static final String USAGE = "Format: view-list";

    private static final Logger LOGGER = Logger.getLogger(ViewListCommand.class.getName());

    private static final String NO_LIST_MESSAGE = "No grocery list generated. Use 'generate-list' first.";

    /**
     * Reads the current list when the command executes; an empty Optional means no list has been generated.
     */
    private final Supplier<Optional<List<GroceryItem>>> groceryListSource;

    /**
     * Creates a viewing command connected to the caller's generated-list data.
     * A present but empty list is valid and displays zero items.
     *
     * @param groceryListSource A function returning the generated list, or {@code Optional.empty()} if absent.
     *         The function must never return {@code null}.
     * @throws NullPointerException If the source is null.
     */
    public ViewListCommand(Supplier<Optional<List<GroceryItem>>> groceryListSource) {
        this.groceryListSource = Objects.requireNonNull(groceryListSource, "groceryListSource");
    }

    /**
     * Displays the latest supplied groceries, including a present list with zero items.
     *
     * @param recipeBook The shared command context; viewing does not read or modify recipes.
     * @return The formatted list or a prompt when no list has been generated.
     * @throws IllegalStateException If the source returns {@code null} instead of an Optional.
     * @throws NullPointerException If a grocery item is null.
     */
    @Override
    public String execute(RecipeBook recipeBook) {
        return executeWithResult(recipeBook).feedback();
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        Optional<List<GroceryItem>> groceries = groceryListSource.get();
        if (groceries == null) {
            throw new IllegalStateException("Grocery list source returned null; use Optional.empty() for no list.");
        }
        if (groceries.isEmpty()) {
            LOGGER.fine("view-list requested before a grocery list was generated");
            return CommandResult.note(NO_LIST_MESSAGE);
        }
        String result = new GroceryListViewer().view(groceries.get());
        LOGGER.fine(() -> "view-list displayed " + groceries.get().size() + " items");
        return CommandResult.info(result);
    }
}
//@@author
