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

        assertEquals(expected, getCandidateValues(""));
        assertTrue(getCandidates("").stream().allMatch(Candidate::complete));
    }

    @Test
    void complete_commandPrefix_isCaseSensitiveAndDoesNotSuggestUnrelatedCommands() {
        assertEquals(List.of("generate-plan", "generate-list"), getCandidateValues("generate-"));
        assertEquals(List.of("check-shortfall"), getCandidateValues("check-sh"));
        assertEquals(List.of(), getCandidateValues("CHECK"));
        assertEquals(List.of(), getCandidateValues("unknown "));
        assertEquals(List.of(), getCandidateValues("bye "));
    }

    @Test
    void complete_argumentPrefixes_stayOpenAndExcludePreviouslySuppliedScalarArguments() {
        assertEquals(List.of("d/", "cal/", "p/", "c/", "f/"), getCandidateValues("generate-plan "));
        assertEquals(List.of("p/", "c/", "f/"), getCandidateValues("generate-plan d/2 cal/1500 "));
        assertEquals(List.of("cal/"), getCandidateValues("generate-plan d/2 ca"));
        assertFalse(getCandidates("generate-plan ca").getFirst().complete());
        assertEquals(List.of(), getCandidateValues("generate-plan cal/1500"));
    }

    @Test
    void complete_repeatedIngredients_remainAvailableWithoutDuplicateCandidates() {
        List<String> suggestions = getCandidateValues("add-recipe n/Soup i/Rice:100g ");

        assertFalse(suggestions.contains("n/"));
        assertTrue(suggestions.contains("i/"));
        assertTrue(suggestions.contains("food/"));
        assertEquals(suggestions.size(), suggestions.stream().distinct().count());
        assertEquals(List.of("d/", "m/", "portions/"), getCandidateValues("adjust-portion "));
        assertEquals(List.of("d/", "m/", "i/"), getCandidateValues("customize-meal "));
    }

    @Test
    void complete_editingAnEarlierArgument_doesNotTreatCurrentWordAsAlreadySupplied() {
        String line = "generate-plan ca d/2";
        ParsedLine parsed = parser.parse(line, "generate-plan ca".length(), ParseContext.COMPLETE);
        List<Candidate> candidates = new ArrayList<>();

        completer.complete(null, parsed, candidates);

        assertEquals(List.of("cal/"), candidates.stream().map(Candidate::value).toList());
    }

    //@@author peilingggg
    @Test
    void complete_addRecipe_suggestsExactCommandAndAllPrefixes() {
        assertEquals(List.of("add-recipe"), getCandidateValues("add-r"));
        assertEquals(List.of("n/", "cal/", "p/", "c/", "f/", "i/", "food/"), getCandidateValues("add-recipe "));
        assertTrue(getCandidates("add-recipe ").stream().noneMatch(Candidate::complete));
    }

    @Test
    void complete_addRecipeWithAllScalars_keepsOnlyRepeatableIngredients() {
        String line = "add-recipe n/Soup cal/100 p/1 c/2 f/3 food/none i/Rice:10g i/Water:20ml ";

        assertEquals(List.of("i/"), getCandidateValues(line));
        assertEquals(List.of(), getCandidateValues(line + "fo"));
        assertFalse(getCandidates(line).getFirst().complete());
    }

    //@@author Rixte
    @Test
    void complete_mealTrackingCommands_suggestsMergedCommandsAndTheirExactPrefixes() {
        assertEquals(List.of("record-meal"), getCandidateValues("record-m"));
        assertEquals(List.of("edit-recipe", "edit-meal"), getCandidateValues("edit-"));
        assertEquals(List.of("delete-recipe", "delete-meal"), getCandidateValues("delete-"));
        assertEquals(List.of("date/", "s/"), getCandidateValues("record-meal 1 "));
        assertEquals(List.of("date/", "s/"), getCandidateValues("edit-meal 1 "));
        assertEquals(List.of("date/"), getCandidateValues("view-meals "));
        assertEquals(List.of(), getCandidateValues("delete-meal 1 "));
    }

    @Test
    void complete_mealTrackingValues_suppressesUsedFieldsWithoutCompletingValues() {
        assertEquals(List.of("s/"), getCandidateValues("record-meal 1 date/2026-03-02 "));
        assertEquals(List.of("date/"), getCandidateValues("edit-meal 1 s/2 "));
        assertEquals(List.of(), getCandidateValues("edit-meal 1 s/2 date/2026-03-02 "));
        assertEquals(List.of(), getCandidateValues("record-meal 1 date/2026-"));
        assertEquals(List.of(), getCandidateValues("record-meal 1 s/2"));
        assertFalse(getCandidates("record-meal 1 da").getFirst().complete());
    }

    @Test
    void complete_replaceMeal_suggestsOnlyUnusedPositionPrefixes() {
        assertEquals(List.of("replace-meal"), getCandidateValues("replace-m"));
        assertEquals(List.of("d/", "m/", "r/"), getCandidateValues("replace-meal "));
        assertEquals(List.of("m/", "r/"), getCandidateValues("replace-meal d/1 "));
        assertEquals(List.of("r/"), getCandidateValues("replace-meal m/2 d/1 r"));
        assertEquals(List.of(), getCandidateValues("replace-meal d/1 m/2 r/3 "));
        assertEquals(List.of(), getCandidateValues("replace-meal d/1 m/2 r/3"));
    }

    /**
     * Returns candidate text for a cursor positioned at the end of the supplied line.
     */
    private List<String> getCandidateValues(String line) {
        return getCandidates(line).stream().map(Candidate::value).toList();
    }

    /**
     * Parses completion input with the same literal-quote behavior used by the terminal editor.
     */
    private List<Candidate> getCandidates(String line) {
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parser.parse(line, line.length(), ParseContext.COMPLETE), candidates);
        return candidates;
    }
}
//@@author
