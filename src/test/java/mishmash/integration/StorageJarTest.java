package mishmash.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mishmash.storage.JsonSessionStorage;

/**
 * Exercises autosave, EOF, and restart using only the packaged JAR from an independent working directory.
 */
class StorageJarTest {
    private static final int PROCESS_TIMEOUT_SECONDS = 30;
    private static final int TERMINATION_TIMEOUT_SECONDS = 10;
    private static final int CLEANUP_TIMEOUT_SECONDS = 5;
    private static final int OUTPUT_POLL_INTERVAL_MILLIS = 20;
    private static final String ADD_RECIPE =
            "add-recipe n/A cal/100 p/10 c/10 f/1 i/Rice:100.001g food/none";
    @TempDir
    Path directory;

    @Test
    void jar_uncommonInputsAndUnicode_remainRecoverableAndRetainLiteralData() throws Exception {
        String name = "\u7C73\u996D \uD83E\uDD55 Caf\u00E9 literal_\";$(whoami)|&<>{}[]";
        String ingredient = "\u7C73\u0301";
        String first = runJar(String.join("\n", "Tester\u001B[2J",
                "add-recipe n/" + name + " cal/2147483647 p/0 c/0 f/0 i/" + ingredient + ":0.125g food/none",
                ADD_RECIPE.replace("n/A", "n/B"), ADD_RECIPE.replace("n/A", "n/C"),
                "generate-plan d/2", "generate-list", "record-meal 1",
                "adjust-portion d/1 m/1 portions/-1", "replace-meal d/1 m/1 r/2147483648",
                "customize-meal d/1 m/1 i/Test:NaNg", "record-meal 1 date/2026-02-30",
                "set-targets cal/\uFF11", "list-recipes\u001B[2J", "bye\u0000", "help", "bye", ""));
        assertTrue(first.contains("\\u001B[2J"), first);
        assertFalse(first.contains("\u001B[2J"), first);
        assertTrue(first.contains("[ERROR]"), first);
        assertTrue(first.contains("MISHMASH KITCHEN MANUAL"), first);

        String restored = runJar("Tester\nlist-recipes\nview-plan\nview-list\nview-meals\nbye\n");
        assertFalse(restored.contains("[ERROR]"), restored);
        assertFalse(restored.contains("[WARN]"), restored);
        assertTrue(restored.contains(name), restored);
        assertTrue(restored.contains(ingredient + ": 0.25g"), restored);
        assertTrue(restored.contains("Meal 1: " + name), restored);
        assertTrue(restored.contains("Day 2"), restored);
    }

    @Test
    void jar_existingProcessLock_refusesSecondSession() throws Exception {
        try (JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("data/mishmash.json"))) {
            assertTrue(storage.load().warning().isEmpty());
            String output = runJar("Tester\nbye\n");
            assertTrue(output.contains("already using this data folder"), output);
            assertFalse(output.contains("Who's cooking today?"), output);
        }
    }

    @Test
    void jar_forcedTerminationAfterFeedback_keepsAutosavedChangeAndReleasesLock() throws Exception {
        Path output = Files.createTempFile(directory, "interrupted-", ".txt");
        Process process = startJar(output);
        try {
            process.getOutputStream().write(("Tester\n" + ADD_RECIPE + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_TIMEOUT_SECONDS);
            while (!Files.readString(output).contains("New recipe added!") && System.nanoTime() < deadline) {
                assertTrue(process.isAlive(), Files.readString(output));
                Thread.sleep(OUTPUT_POLL_INTERVAL_MILLIS);
            }
            assertTrue(Files.readString(output).contains("New recipe added!"), "Autosave did not finish");
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS), "JAR did not terminate");
        }
        String restored = runJar("Tester\nlist-recipes\nbye\n");
        assertTrue(restored.contains("1. A (100 kcal)"), restored);
    }

    @Test
    void jar_failedSave_warnsRetainsMemoryAndPreservesLastGoodFile() throws Exception {
        assertFailedSavePreservesLastGoodFile("bye\n");
    }

    @Test
    void jar_failedSaveAtEof_retriesBeforeExitingAndWarnsWithoutGoodbye() throws Exception {
        String output = assertFailedSavePreservesLastGoodFile("");
        assertFalse(output.contains("Goodbye!"), output);
    }

    /**
     * Checks that both exit routes retry unsaved changes without replacing the last recoverable snapshot.
     */
    private String assertFailedSavePreservesLastGoodFile(String exitCommand) throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path primary = directory.resolve("data/mishmash.json");
        String saved = Files.readString(primary);
        Path backup = directory.resolve("data/mishmash.backup.json");
        Files.delete(backup);
        Files.createDirectory(backup);
        String output = runJar("Tester\nedit-recipe 1 n/Unsaved\nlist-recipes\n" + exitCommand);
        assertTrue(output.contains("[WARN] Recipe updated!"), output);
        assertTrue(output.contains("1. Unsaved (100 kcal)"), output);
        assertTrue(output.contains("Exiting with unsaved changes."), output);
        assertFalse(output.contains("[DONE] Recipe updated!"), output);
        assertEquals(saved, Files.readString(primary));
        Files.delete(backup);
        assertTrue(runJar("Tester\nlist-recipes\nbye\n").contains("1. A (100 kcal)"));
        return output;
    }

    @Test
    void jar_corruptData_recoversBackupAndPreservesOriginal() throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path file = directory.resolve("data/mishmash.json");
        Files.writeString(file, "damaged primary");
        String output = runJar("Tester\nlist-recipes\nbye\n");
        assertTrue(output.contains("[WARN] Saved data was damaged"), output);
        assertTrue(output.contains("1. A (100 kcal)"), output);
        try (var paths = Files.list(file.getParent())) {
            Path preserved = paths.filter(path -> path.toString().contains(".corrupt-")).findFirst().orElseThrow();
            assertEquals("damaged primary", Files.readString(preserved));
        }
    }

    @Test
    void jar_noOpRejectedAndCancelledCommands_doNotRewriteSave() throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path file = directory.resolve("data/mishmash.json");
        java.nio.file.attribute.FileTime marker = java.nio.file.attribute.FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(file, marker);
        String output = runJar("Tester\nclear-diet\ncustomize-meal d/1 m/1 i/Rice:1g\n"
                + "adjust-portion d/1 m/1 portions/0\ndelete-recipe 1\nn\nlist-recipes\nbye\n");
        assertTrue(output.contains("[NOTE] Dietary preferences are already clear"), output);
        assertTrue(output.contains("Cancelled. Nothing was changed."), output);
        assertTrue(output.contains("[ERROR]"), output);
        assertEquals(marker, Files.getLastModifiedTime(file));
    }

    @Test
    void jar_failedDietaryRepair_retainsWarningOutcomeAndClearedPlanAcrossRestart() throws Exception {
        String setup = String.join("\n", "Tester", ADD_RECIPE,
                ADD_RECIPE.replace("n/A", "n/B"), ADD_RECIPE.replace("n/A", "n/C"),
                "generate-plan d/2", "generate-list", "record-meal 1", "set-diet exclude/Rice", "yes", "bye", "");
        String first = runJar(setup);
        assertTrue(first.contains("[WARN] Dietary preferences saved, but"), first);
        String restored = runJar("Tester\nview-diet\nview-plan\nview-list\nview-meals\nbye\n");
        assertTrue(restored.contains("Excluded ingredients: rice"), restored);
        assertTrue(restored.contains("No active meal plan."), restored);
        assertTrue(restored.contains("No grocery list"), restored);
        assertTrue(restored.contains("Meal 1: A (100 kcal |"), restored);
    }

    @Test
    void jar_restartsWithAllStateAndDefensiveCustomizations() throws Exception {
        String setup = String.join("\n", "Tester",
                "add-recipe n/A cal/100 p/10 c/10 f/1 i/Rice:100.001g food/none",
                "add-recipe n/B cal/100 p/10 c/10 f/1 i/Beans:100g food/none",
                "add-recipe n/C cal/100 p/10 c/10 f/1 i/Tofu:100g food/none",
                "set-diet diet/vegetarian", "generate-plan d/2", "adjust-portion d/1 m/1 portions/2",
                "customize-meal d/1 m/1 i/rice:12.500g", "generate-list", "record-meal 1",
                "edit-recipe 1 n/Edited", "set-targets cal/2000", "bye", "");
        String first = runJar(setup);
        assertTrue(first.contains("[WARN] Meal 1 on Day 1 ingredients customised."), first);
        assertTrue(Files.exists(directory.resolve("data/mishmash.json")));
        String second = runJar("Tester\nlist-recipes\nview-diet\nview-targets\nview-plan\nview-list\nview-meals\n");
        assertTrue(second.contains("Edited"), second);
        assertTrue(second.contains("vegetarian"), second);
        assertTrue(second.contains("2000"), second);
        assertTrue(second.contains("custom ingredients"), second);
        assertTrue(second.contains("rice: 25g"), second);
        assertTrue(second.contains("Meal 1: A (100 kcal |"), second);
        assertTrue(second.contains("Day 2"), second);
    }

    @Test
    void jar_mealCorrectionsAndDeletions_retainDatesServingsAndSnapshots() throws Exception {
        String first = runJar(String.join("\n", "Tester", ADD_RECIPE,
                "record-meal 1 date/2024-01-02 s/3", "record-meal 1 date/2024-01-03 s/2",
                "edit-meal 1 date/2024-02-02 s/4", "delete-meal 2", "yes", "edit-recipe 1 n/Changed", "bye", ""));
        assertFalse(first.contains("[ERROR]"), first);
        String restored = runJar("Tester\nview-meals date/2024-02-02\nbye\n");
        assertTrue(restored.contains("Meal 1: A (400 kcal |"), restored);
        assertTrue(restored.contains("4 servings | 2024-02-02"), restored);
        assertFalse(restored.contains("Meal 2:"), restored);
        assertFalse(restored.contains("Changed"), restored);
        String deleted = runJar("Tester\ndelete-meal 1\nyes\nbye\n");
        assertFalse(deleted.contains("[ERROR]"), deleted);
        assertTrue(runJar("Tester\nview-meals\nbye\n").contains("No meals recorded yet."));
    }

    /**
     * Runs with bounded time and redirected output to avoid pipe deadlocks and native-terminal dependencies.
     */
    private String runJar(String input) throws Exception {
        Path output = Files.createTempFile(directory, "output-", ".txt");
        Process process = startJar(output);
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS), "JAR process timed out");
            String transcript = Files.readString(output).replaceAll("\u001B\\[[;\\d]*m", "");
            assertEquals(0, process.exitValue(), transcript);
            return transcript;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Starts the production launcher using Java 25 and an external working directory.
     */
    private Process startJar(Path output) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path jar = Path.of(System.getProperty("mishmash.test.jar")).toAbsolutePath();
        List<String> arguments = new ArrayList<>();
        arguments.add(java.toString());
        // The transcript is read as UTF-8; Windows otherwise selects its native redirected-output code page.
        arguments.add("-Dstdout.encoding=UTF-8");
        arguments.add("-Dstderr.encoding=UTF-8");
        addCoverageAgent(arguments);
        arguments.add("-jar");
        arguments.add(jar.toString());
        return new ProcessBuilder(arguments)
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }

    /**
     * Records each normally exiting child JVM separately so parallel runs cannot overwrite coverage data.
     */
    private void addCoverageAgent(List<String> arguments) throws IOException {
        String agent = System.getProperty("mishmash.test.coverage.agent");
        if (agent == null) {
            return;
        }
        Path coverageDirectory = Path.of(System.getProperty("mishmash.test.coverage.directory"));
        Files.createDirectories(coverageDirectory);
        Path recording = Files.createTempFile(coverageDirectory, "launcher-", ".exec");
        arguments.add(agent.replaceFirst("destfile=[^,]*",
                Matcher.quoteReplacement("destfile=" + recording.toAbsolutePath())));
    }
}
