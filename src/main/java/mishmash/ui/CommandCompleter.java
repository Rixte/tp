package mishmash.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import mishmash.parser.Parser;

//@@author Rixte
/**
 * Suggests command names and argument prefixes using the parser's existing usage descriptions.
 * Completion supplies text only; the application parser remains responsible for validation.
 */
public final class CommandCompleter implements Completer {
    private static final Pattern COMMAND_PATTERN = Pattern.compile("(?m)^Format: ([a-z][a-z-]*)([^\\r\\n]*)");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("\\b([a-z]+/)");
    private static final Map<String, List<String>> COMMAND_PREFIXES = collectPrefixes();

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        boolean isCommandName = line.wordIndex() == 0;
        List<String> suggestions = isCommandName ? new ArrayList<>(COMMAND_PREFIXES.keySet())
                : COMMAND_PREFIXES.getOrDefault(line.words().getFirst(), List.of());
        for (String suggestion : suggestions) {
            if (suggestion.startsWith(line.word()) && (isCommandName || !isAlreadySupplied(suggestion, line))) {
                // A prefix stays open so typing its value does not require removing an inserted space.
                candidates.add(new Candidate(suggestion, suggestion, null, null, null, null, isCommandName));
            }
        }
    }

    /**
     * Identifies earlier scalar arguments while allowing the repeated ingredient prefix.
     */
    private boolean isAlreadySupplied(String prefix, ParsedLine line) {
        if (prefix.equals("i/")) {
            return false;
        }
        return line.words().subList(1, line.wordIndex()).stream().anyMatch(word -> word.startsWith(prefix));
    }

    /**
     * Builds immutable suggestions from the same syntax used by help and validation errors.
     */
    private static Map<String, List<String>> collectPrefixes() {
        Map<String, List<String>> commands = new LinkedHashMap<>();
        Matcher command = COMMAND_PATTERN.matcher(Parser.COMMAND_USAGE);
        while (command.find()) {
            List<String> prefixes = new ArrayList<>();
            Matcher prefix = PREFIX_PATTERN.matcher(command.group(2));
            while (prefix.find()) {
                prefixes.add(prefix.group(1));
            }
            commands.put(command.group(1), prefixes.stream().distinct().toList());
        }
        return Collections.unmodifiableMap(commands);
    }
}
//@@author
