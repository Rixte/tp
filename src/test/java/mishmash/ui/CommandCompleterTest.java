package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser.ParseContext;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

import mishmash.parser.Parser;

//@@author Rixte
/**
 * Checks completion against supported syntax without invoking or mutating application commands.
 */
class CommandCompleterTest {
    private final CommandCompleter completer = new CommandCompleter();
    private final DefaultParser parser = new DefaultParser().quoteChars(new char[0]).escapeChars(new char[0]);

    @Test
    void complete_emptyCommand_includesEveryDocumentedCommandExactlyOnce() {
        List<String> expected = Pattern.compile("(?m)^Format: ([a-z][a-z-]*)")
                .matcher(Parser.COMMAND_USAGE).results().map(match -> match.group(1)).distinct().toList();

        assertEquals(expected, values(""));
        assertTrue(candidates("").stream().allMatch(Candidate::complete));
    }

    @Test
    void complete_commandPrefix_isCaseSensitiveAndDoesNotSuggestUnrelatedCommands() {
        assertEquals(List.of("generate-plan", "generate-list"), values("generate-"));
        assertEquals(List.of("check-shortfall"), values("check-sh"));
        assertEquals(List.of(), values("CHECK"));
        assertEquals(List.of(), values("unknown "));
        assertEquals(List.of(), values("bye "));
    }

    @Test
    void complete_argumentPrefixes_stayOpenAndExcludePreviouslySuppliedScalarArguments() {
        assertEquals(List.of("d/", "cal/", "p/", "c/", "f/"), values("generate-plan "));
        assertEquals(List.of("p/", "c/", "f/"), values("generate-plan d/2 cal/1500 "));
        assertEquals(List.of("cal/"), values("generate-plan d/2 ca"));
        assertFalse(candidates("generate-plan ca").getFirst().complete());
        assertEquals(List.of(), values("generate-plan cal/1500"));
    }

    @Test
    void complete_repeatedIngredients_remainAvailableWithoutDuplicateCandidates() {
        List<String> suggestions = values("add-recipe n/Soup i/Rice:100g ");

        assertFalse(suggestions.contains("n/"));
        assertTrue(suggestions.contains("i/"));
        assertTrue(suggestions.contains("food/"));
        assertEquals(suggestions.size(), suggestions.stream().distinct().count());
        assertEquals(List.of("d/", "m/", "portions/"), values("adjust-portion "));
        assertEquals(List.of("d/", "m/", "i/"), values("customize-meal "));
    }

    @Test
    void complete_editingAnEarlierArgument_doesNotTreatCurrentWordAsAlreadySupplied() {
        String line = "generate-plan ca d/2";
        ParsedLine parsed = parser.parse(line, "generate-plan ca".length(), ParseContext.COMPLETE);
        List<Candidate> candidates = new ArrayList<>();

        completer.complete(null, parsed, candidates);

        assertEquals(List.of("cal/"), candidates.stream().map(Candidate::value).toList());
    }

    /**
     * Returns candidate text for a cursor positioned at the end of the supplied line.
     */
    private List<String> values(String line) {
        return candidates(line).stream().map(Candidate::value).toList();
    }

    /**
     * Parses completion input with the same literal-quote behavior used by the terminal editor.
     */
    private List<Candidate> candidates(String line) {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parser.parse(line, line.length(), ParseContext.COMPLETE), candidates);
        return candidates;
    }
}
//@@author
