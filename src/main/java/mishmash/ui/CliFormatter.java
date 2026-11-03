package mishmash.ui;

import java.util.Locale;
import java.util.Objects;

import mishmash.command.CommandResult;

/**
 * Formats console messages with MishMash's ANSI kitchen theme.
 * Terminal styling is kept here so commands and models remain independent of console concerns.
 * Printable Unicode is preserved, and untrusted controls are escaped before application styling is added.
 */
public final class CliFormatter {
    private static final String ESCAPE = "\u001B[";
    private static final String RESET = ESCAPE + "0m";
    private static final String BOLD_YELLOW = ESCAPE + "1;93m";
    private static final String BOLD_MAGENTA = ESCAPE + "1;95m";
    private static final String BOLD_CYAN = ESCAPE + "1;96m";
    private static final String BOLD_GREEN = ESCAPE + "1;92m";
    private static final String BOLD_RED = ESCAPE + "1;91m";
    private static final String YELLOW = ESCAPE + "93m";
    private static final String DIM = ESCAPE + "2m";
    private static final int COMMAND_CARD_WIDTH = 56;
    private static final int COMMAND_COLUMN_WIDTH = 20;
    private static final int DESCRIPTION_COLUMN_WIDTH = 31;

    private CliFormatter() {
    }

    /**
     * Returns the compact startup banner and kitchen-themed tagline.
     *
     * @return The ANSI-styled banner without a trailing line separator.
     */
    public static String banner() {
        return String.join(System.lineSeparator(),
                style("            (       )     (       )", DIM),
                style("             )     (       )     (", DIM),
                style("            (       )     (       )", DIM),
                style("        .-~~~~~~~~~~~~~~~~~~~~~~~~~~~-.", BOLD_MAGENTA),
                style("       /                               \\", BOLD_MAGENTA),
                style("      /          M I S H M A S H        \\", BOLD_YELLOW),
                style("     |       PLAN  |  PREP  |  ENJOY     |", BOLD_CYAN),
                style("      \\_________________________________/", BOLD_MAGENTA),
                style("        \\_____________________________/", BOLD_MAGENTA),
                style("               \\_____________/", BOLD_MAGENTA));
    }

    /**
     * Returns the prompt that asks who is using the current session.
     *
     * @return The ANSI-styled name prompt.
     */
    public static String namePrompt() {
        return style("Who's cooking today?", BOLD_MAGENTA);
    }

    /**
     * Returns a personalized greeting with terminal controls displayed as escaped text.
     *
     * @param name The name entered by the user.
     * @return The ANSI-styled greeting.
     */
    public static String greeting(String name) {
        Objects.requireNonNull(name, "name");
        return style("Hello " + name + " - apron on, let's get cooking!", BOLD_YELLOW);
    }

    /**
     * Returns the short introduction displayed before the first command prompt.
     *
     * @return The ANSI-styled welcome and navigation hint.
     */
    public static String welcome() {
        return style("[KITCHEN OPEN]", BOLD_GREEN) + " Welcome to MishMash!"
                + System.lineSeparator()
                + style("Pick a command to start:", DIM)
                + System.lineSeparator()
                + commandCard();
    }

    /**
     * Returns the compact prompt displayed before each command.
     *
     * @return The ANSI-styled command prompt.
     */
    public static String commandPrompt() {
        return style("mishmash >", BOLD_CYAN);
    }

    /**
     * Formats feedback according to the command's explicit semantic result.
     *
     * @param result The non-null feedback and presentation status returned by the command.
     * @return The styled feedback without a trailing line separator.
     */
    public static String feedback(CommandResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result.status()) {
            case DONE -> addStatus("DONE", BOLD_GREEN, result.feedback());
            case NOTE -> addStatus("NOTE", BOLD_YELLOW, result.feedback());
            case WARNING -> addStatus("WARN", BOLD_YELLOW, result.feedback());
            case INFO -> style(result.feedback(), BOLD_CYAN);
            case HELP -> style(result.feedback(), BOLD_YELLOW);
        };
    }

    /**
     * Returns a recoverable error with a prominent status label.
     *
     * @param explanation The user-facing explanation of the rejected command.
     * @return The styled error line.
     */
    public static String error(String explanation) {
        Objects.requireNonNull(explanation, "explanation");
        return status("ERROR", BOLD_RED) + " Error: " + escapeControls(explanation);
    }

    /**
     * Returns usage guidance beneath a recoverable error.
     *
     * @param usage The relevant command syntax or complete command list.
     * @return The styled usage guidance.
     */
    public static String usage(String usage) {
        Objects.requireNonNull(usage, "usage");
        return status("TRY", BOLD_CYAN) + " " + style(usage, DIM);
    }

    /**
     * Returns a safe-default confirmation prompt.
     *
     * @param question The command's original yes/no question.
     * @return The styled question ending in {@code [y/N]:}.
     */
    public static String confirmation(String question) {
        Objects.requireNonNull(question, "question");
        String conciseQuestion = question.endsWith(" (y/n)")
                ? question.substring(0, question.length() - " (y/n)".length())
                : question;
        return status("CONFIRM", BOLD_RED) + " " + escapeControls(conciseQuestion) + " " + style("[y/N]:", YELLOW);
    }

    /**
     * Returns the message shown when a confirmation is declined.
     *
     * @return The styled cancellation message.
     */
    public static String cancellation() {
        return status("NOTE", BOLD_YELLOW) + " Cancelled. Nothing was changed.";
    }

    /**
     * Returns the final kitchen-themed sign-off while leaving {@code Goodbye!} as plain text.
     *
     * @return The styled sign-off.
     */
    public static String goodbye() {
        return style("[KITCHEN CLOSED]", BOLD_MAGENTA) + " Goodbye!";
    }

    /**
     * Returns a bordered list of useful commands for a new session.
     */
    private static String commandCard() {
        return String.join(System.lineSeparator(),
                cardHeading("QUICK COMMANDS"),
                commandRow("help", "Full kitchen manual"),
                commandRow("list-recipes", "Browse saved recipes"),
                commandRow("generate-plan d/3", "Build a three-day plan"),
                commandRow("view-plan", "Review the active plan"),
                commandRow("bye", "Exit MishMash"),
                style("+" + "-".repeat(COMMAND_CARD_WIDTH - 2) + "+", BOLD_MAGENTA));
    }

    /**
     * Builds the top border of the common-command card.
     */
    private static String cardHeading(String heading) {
        String prefix = "+-- " + heading + " ";
        return style(prefix + "-".repeat(COMMAND_CARD_WIDTH - prefix.length() - 1) + "+", BOLD_MAGENTA);
    }

    /**
     * Builds one aligned command-description row without counting ANSI codes as visible width.
     */
    private static String commandRow(String command, String description) {
        String commandColumn = String.format(Locale.ROOT, "%-" + COMMAND_COLUMN_WIDTH + "s", command);
        String descriptionColumn = String.format(Locale.ROOT, "%-" + DESCRIPTION_COLUMN_WIDTH + "s", description);
        return "|  " + style(commandColumn, BOLD_CYAN) + " " + descriptionColumn + "|";
    }

    /**
     * Adds one semantic status label to the first line of a multiline response.
     */
    private static String addStatus(String label, String color, String feedback) {
        return status(label, color) + " " + escapeControls(feedback);
    }

    /**
     * Builds a colored square-bracketed status label.
     */
    private static String status(String label, String color) {
        return style("[" + label + "]", color);
    }

    /**
     * Wraps text in one ANSI style and resets the terminal immediately afterward.
     */
    private static String style(String text, String style) {
        return style + escapeControls(text) + RESET;
    }

    /**
     * Makes untrusted terminal controls visible while preserving Unicode, tabs and multiline output.
     * Application-owned ANSI styling is added only after this escaping step.
     */
    private static String escapeControls(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            boolean isLineBreak = character == '\n'
                    || (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n');
            if (Character.isISOControl(character) && character != '\t' && !isLineBreak) {
                escaped.append(String.format(Locale.ROOT, "\\u%04X", (int) character));
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
