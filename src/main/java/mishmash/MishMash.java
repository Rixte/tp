package mishmash;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import mishmash.command.Command;
import mishmash.command.CommandResult;
import mishmash.exception.MishMashException;
import mishmash.model.ApplicationState;
import mishmash.model.RecipeBook;
import mishmash.parser.Parser;
import mishmash.storage.JsonSessionStorage;
import mishmash.storage.LoadResult;
import mishmash.storage.SessionPersistence;
import mishmash.storage.StorageException;
import mishmash.ui.CliFormatter;
import mishmash.ui.ConsoleInput;
import mishmash.ui.ConsoleInputs;
import mishmash.ui.ScannerConsoleInput;

/**
 * Runs the MishMash command-line application.
 */
public class MishMash {
    /**
     * Records application lifecycle events and command execution diagnostics.
     */
    private static final Logger LOGGER = Logger.getLogger(MishMash.class.getName());

    /**
     * Shares the file handler and logging level with loggers in all MishMash subpackages.
     */
    private static final Logger APP_LOGGER = Logger.getLogger("mishmash");

    /**
     * Limits the approximate size of each rotating log file in bytes.
     */
    private static final int LOG_FILE_SIZE_LIMIT_BYTES = 1_000_000;

    /**
     * Limits the number of rotating log files retained across sessions.
     */
    private static final int LOG_FILE_COUNT = 3;

    /**
     * Borrowed console input; the caller remains responsible for closing it.
     */
    private final ConsoleInput input;
    /**
     * Destination for user-facing output, independent of diagnostic logging.
     */
    private final PrintStream output;
    private final Parser parser;
    private final RecipeBook recipeBook;
    private SessionPersistence persistence;

    /**
     * Creates an application session without changing global console streams or logging configuration.
     * The caller owns the input and output, so running a session does not close either resource.
     *
     * @param scanner The session's input and confirmation answers.
     * @param output The destination for prompts, results, and recoverable errors.
     * @param parser The parser connected to the session's shared application state.
     * @param recipeBook The recipe book passed to commands in this session.
     * @throws NullPointerException If any dependency is null.
     */
    public MishMash(Scanner scanner, PrintStream output, Parser parser, RecipeBook recipeBook) {
        this(new ScannerConsoleInput(scanner, output), output, parser, recipeBook);
    }

    /**
     * Creates a session with a terminal-independent input adapter.
     *
     * @param input The borrowed source of commands and confirmation answers.
     * @param output The destination for application feedback.
     * @param parser The parser connected to this session's shared state.
     * @param recipeBook The recipes used by commands in this session.
     * @throws NullPointerException If a dependency is missing.
     */
    public MishMash(ConsoleInput input, PrintStream output, Parser parser, RecipeBook recipeBook) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.recipeBook = Objects.requireNonNull(recipeBook, "recipeBook");
    }

    /**
     * Reads and executes commands until the user exits or input ends.
     *
     * @param args Command-line arguments, which are not used.
     */
    public static void main(String[] args) {
        try (JsonSessionStorage storage = JsonSessionStorage.open(JsonSessionStorage.DEFAULT_DATA_FILE)) {
            LoadResult loaded = storage.load();
            loaded.warning().ifPresent(message -> System.out.println(
                    CliFormatter.feedback(CommandResult.warning(message))));
            ApplicationState state = loaded.state();
            runMain("mishmash.%g.log", new Parser(state), state.getRecipeBook(),
                    new SessionPersistence(state, storage));
        } catch (StorageException e) {
            LOGGER.log(Level.FINE, "Storage startup or cleanup failed", e);
            System.out.println(CliFormatter.error(e.getMessage()));
        }
    }

    /**
     * Runs the entry point with a supplied log location for testing startup and shutdown failures.
     *
     * @param logPattern The rotating log destination.
     */
    static void runMain(String logPattern) {
        runMain(logPattern, new Parser(), new RecipeBook(), null);
    }

    /**
     * Runs the launcher with explicit state; legacy test launchers remain isolated from disk.
     */
    private static void runMain(String logPattern, Parser parser, RecipeBook recipes,
            SessionPersistence persistence) {
        Level originalLogLevel = APP_LOGGER.getLevel();
        boolean wasUsingParentHandlers = APP_LOGGER.getUseParentHandlers();
        FileHandler logFile = configureLogging(logPattern);
        boolean hasStarted = false;
        try (ConsoleInput input = ConsoleInputs.open()) {
            MishMash session = new MishMash(input, System.out, parser, recipes);
            session.persistence = persistence;
            hasStarted = true;
            session.run();
        } catch (RuntimeException | AssertionError e) {
            // run() logs its own failures; input or session construction failures are logged here instead.
            if (!hasStarted) {
                LOGGER.log(Level.SEVERE, "Unexpected application startup failure", e);
            }
            throw e;
        } finally {
            if (logFile != null) {
                APP_LOGGER.removeHandler(logFile);
                logFile.close();
                APP_LOGGER.setLevel(originalLogLevel);
                APP_LOGGER.setUseParentHandlers(wasUsingParentHandlers);
            }
        }
    }

    //@@author Rixte
    /**
     * Runs the greeting and command loop using the supplied dependencies, leaving setup to the caller.
     * Logs unexpected failures once before rethrowing the same exception or assertion error.
     */
    public void run() {
        try {
            LOGGER.info("MishMash started");
            greet();
            output.println(CliFormatter.welcome());
            readCommands();
        } catch (RuntimeException | AssertionError e) {
            // Log unexpected failures once at the application boundary before ending the session.
            LOGGER.log(Level.SEVERE, "Unexpected application failure", e);
            throw e;
        } finally {
            LOGGER.info("MishMash stopped");
        }
    }

    /**
     * Writes package diagnostics to rotating files without mixing them into console interaction.
     * Logging failure leaves the application usable with its existing console logging configuration.
     *
     * @param pattern The rotating file pattern accepted by {@link FileHandler}.
     * @return The file handler to close at shutdown, or null if its file could not be opened.
     */
    private static FileHandler configureLogging(String pattern) {
        try {
            // Append across sessions and rotate files when the size limit is reached.
            FileHandler logFile = new FileHandler(pattern,
                    LOG_FILE_SIZE_LIMIT_BYTES, LOG_FILE_COUNT, true);
            logFile.setFormatter(new SimpleFormatter());
            logFile.setLevel(Level.FINE);
            APP_LOGGER.addHandler(logFile);
            APP_LOGGER.setLevel(Level.FINE);
            APP_LOGGER.setUseParentHandlers(false);
            return logFile;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not open the log file; continuing without file logging", e);
            return null;
        }
    }
    //@@author

    /**
     * Reads commands until the user exits or console input ends.
     */
    private void readCommands() {
        while (true) {
            Optional<String> nextLine = input.readCommand(CliFormatter.commandPrompt());
            if (nextLine.isEmpty()) {
                LOGGER.fine("Console input ended");
                flushPendingChanges();
                return;
            }
            // Keep the original text for validation: trim() would silently remove NUL and other controls.
            String commandLine = nextLine.get();
            if (commandLine.matches("\\s*bye\\s*")) {
                LOGGER.fine("Exit requested with bye");
                flushPendingChanges();
                output.println(CliFormatter.goodbye());
                return;
            }
            executeCommand(commandLine);
        }
    }

    /**
     * Executes one command and displays recoverable errors so the session can continue.
     * A command that cannot be undone, such as a delete, is only run after the user agrees.
     *
     * @param input The original command input, validated by the parser before trimming.
     */
    private void executeCommand(String input) {
        try {
            Command command = parseRequiredCommand(input);
            Optional<String> question = getConfirmationQuestion(command);
            if (question.isPresent() && !userConfirms(question.get())) {
                LOGGER.fine(() -> "Cancelled " + command.getClass().getSimpleName());
                output.println(CliFormatter.cancellation());
                return;
            }
            executeAndDisplayFeedback(command);
        } catch (MishMashException e) {
            LOGGER.log(Level.FINE, "Command rejected", e);
            displayError(e);
        }
    }

    /**
     * Retries unsaved changes before either normal exit route completes.
     */
    private void flushPendingChanges() {
        if (persistence != null) {
            persistence.flush().ifPresent(message -> output.println(CliFormatter.feedback(
                    CommandResult.warning("Exiting with unsaved changes. " + message))));
        }
    }

    /**
     * Returns a parsed command, reporting a missing parser result as a recoverable error.
     */
    private Command parseRequiredCommand(String input) throws MishMashException {
        Command command = parser.parseCommand(input);
        if (command == null) {
            throw new MishMashException("The parser did not return a command.");
        }
        return command;
    }

    /**
     * Returns the command's optional question after checking the confirmation contract.
     */
    private Optional<String> getConfirmationQuestion(Command command) throws MishMashException {
        Optional<String> question = command.confirmationPrompt(recipeBook);
        if (question == null) {
            throw new MishMashException("The command did not return a confirmation prompt.");
        }
        if (question.isPresent() && question.get().isBlank()) {
            throw new MishMashException("Confirmation prompts must not be blank.");
        }
        return question;
    }

    /**
     * Executes an accepted command and displays its feedback, rejecting a missing result.
     */
    private void executeAndDisplayFeedback(Command command) throws MishMashException {
        LOGGER.fine(() -> "Executing " + command.getClass().getSimpleName());
        CommandResult result = command.executeWithResult(recipeBook);
        if (result == null) {
            throw new MishMashException("The command did not return a result.");
        }
        if (result.feedback().isBlank()) {
            throw new MishMashException("The command did not return any feedback.");
        }
        if (persistence != null) {
            result = persistence.afterCommand(result);
        }
        output.println(CliFormatter.feedback(result));
        LOGGER.fine(() -> "Completed " + command.getClass().getSimpleName());
    }

    /**
     * Displays an error, adding general command help if no specific usage was supplied.
     *
     * @param exception The recoverable command error.
     */
    private void displayError(MishMashException exception) {
        output.println(CliFormatter.error(exception.getExplanation()));
        output.println(CliFormatter.usage(exception.getUsage().orElse(Parser.COMMAND_USAGE)));
    }

    /**
     * Asks a yes/no question and reads the answer from the next input line.
     * Only "y" or "yes" (in any letter case) counts as agreement; anything else, including
     * the end of input, counts as "no" so that a destructive action never runs by accident.
     *
     * @param question The question to display.
     * @return True if the user agreed.
     */
    private boolean userConfirms(String question) {
        Optional<String> nextLine = input.readAnswer(CliFormatter.confirmation(question));
        if (nextLine.isEmpty()) {
            return false;
        }
        String answer = nextLine.get().trim();
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    /**
     * Displays the banner and reads the user's name using this session's input and output.
     */
    private void greet() {
        output.println(CliFormatter.banner());
        Optional<String> name = input.readAnswer(CliFormatter.namePrompt());
        if (name.isPresent()) {
            output.println(CliFormatter.greeting(name.get()));
        }
    }
}

