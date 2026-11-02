package mishmash.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import mishmash.model.ApplicationState;
import mishmash.model.GroceryItem;
import mishmash.model.Recipe;

/** Exercises resource failures and atomic-replacement fallback through controlled real-file operations. */
class JsonSessionStorageFailureTest {
    @TempDir
    Path directory;

    @Test
    void open_missingValuesAndInvalidLocations_rejectsWithoutLeakingLock() throws Exception {
        assertThrows(NullPointerException.class, () -> JsonSessionStorage.open(null));
        assertThrows(StorageException.class, () -> JsonSessionStorage.open(directory.getRoot()));
        Path parentFile = directory.resolve("not-a-directory");
        Files.writeString(parentFile, "keep");
        assertThrows(StorageException.class, () -> JsonSessionStorage.open(parentFile.resolve("state.json")));
        Path blockedLock = directory.resolve("state.lock");
        Files.createDirectory(blockedLock);
        assertThrows(StorageException.class, () -> JsonSessionStorage.open(directory.resolve("state.json")));
        assertEquals("keep", Files.readString(parentFile));
    }

    @Test
    void open_nullLockAndAcquisitionFailure_closeOpenedChannel() throws Exception {
        for (boolean noLock : new boolean[]{true, false}) {
            FaultyFiles files = new FaultyFiles();
            files.noLock = noLock;
            files.failAcquisition = !noLock;
            assertThrows(StorageException.class,
                    () -> JsonSessionStorage.open(directory.resolve("state.json"), Clock.systemUTC(), files));
            assertFalse(files.channel.isOpen());
        }
    }

    @Test
    void open_acquisitionAndCleanupFail_keepsOriginalCauseAndSuppressesCleanup() {
        FaultyFiles files = new FaultyFiles();
        files.failAcquisition = true;
        files.failCloseOnOpen = true;

        StorageException error = assertThrows(StorageException.class,
                () -> JsonSessionStorage.open(directory.resolve("state.json"), Clock.systemUTC(), files));

        assertSame(files.channel.acquisitionFailure, error.getCause());
        assertEquals(1, error.getCause().getSuppressed().length);
        assertEquals("Close failed", error.getCause().getSuppressed()[0].getMessage());
        assertFalse(files.channel.isOpen());
    }

    @TestFactory
    Stream<DynamicTest> open_runtimeAcquisitionAndCleanupFail_preservesOriginalException() {
        return Stream.of(new IllegalStateException("Acquisition failed"), new OverlappingFileLockException())
                .map(failure -> DynamicTest.dynamicTest(failure.getClass().getSimpleName(), () -> {
                    FaultyFiles files = new FaultyFiles();
                    files.acquisitionRuntimeFailure = failure;
                    files.failCloseOnOpen = true;
                    StorageException error = assertThrows(StorageException.class,
                            () -> JsonSessionStorage.open(directory.resolve("state.json"), Clock.systemUTC(), files));
                    assertSame(failure, error.getCause());
                    assertEquals(1, failure.getSuppressed().length);
                    assertEquals("Close failed", failure.getSuppressed()[0].getMessage());
                    assertFalse(files.channel.isOpen());
                }));
    }

    @Test
    void open_noLockAndCleanupFail_preservesAlreadyRunningExplanation() {
        FaultyFiles files = new FaultyFiles();
        files.noLock = true;
        files.failCloseOnOpen = true;
        StorageException error = assertThrows(StorageException.class,
                () -> JsonSessionStorage.open(directory.resolve("state.json"), Clock.systemUTC(), files));
        assertTrue(error.getMessage().contains("already using this data folder"));
        assertEquals(1, error.getSuppressed().length);
        assertEquals("Close failed", error.getSuppressed()[0].getMessage());
        assertFalse(files.channel.isOpen());
    }

    @Test
    void channel_positionAndTruncate_returnWrapperAndKeepFailureInjection() throws Exception {
        Path file = directory.resolve("channel.bin");
        Files.write(file, new byte[]{1, 2, 3, 4});
        try (TestLockChannel channel = new TestLockChannel(FileChannel.open(file, StandardOpenOption.WRITE))) {
            FileChannel positioned = channel.position(3);
            assertSame(channel, positioned);
            assertEquals(3, channel.position());
            assertSame(channel, positioned.truncate(2));
            assertEquals(2, channel.size());
            assertEquals(2, channel.position());
            channel.failAcquisition = true;
            assertSame(channel.acquisitionFailure,
                    assertThrows(IOException.class, () -> channel.position(0).truncate(1).tryLock()));
        }
    }

    @TestFactory
    Stream<DynamicTest> close_releaseAndCloseFailures_preserveCausesAndAlwaysCloseChannel() {
        return Stream.of("release", "close", "both").map(failure -> DynamicTest.dynamicTest(failure, () -> {
            FaultyFiles files = new FaultyFiles();
            JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("state.json"),
                    Clock.systemUTC(), files);
            files.channel.failRelease = !failure.equals("close");
            files.channel.failClose = !failure.equals("release");
            StorageException error = assertThrows(StorageException.class, storage::close);
            assertNotNull(error.getCause());
            assertEquals(failure.equals("both") ? 1 : 0, error.getCause().getSuppressed().length);
            assertFalse(files.channel.isOpen());
            storage.close();
            assertThrows(StorageException.class, storage::load);
            assertThrows(StorageException.class, () -> storage.save(new ApplicationState()));
        }));
    }

    @Test
    void save_unsupportedAtomicMove_fallsBackAndLeavesNoTemporaryFiles() throws Exception {
        FaultyFiles files = new FaultyFiles();
        files.rejectAtomicMove = true;
        Path file = directory.resolve("state");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file, Clock.systemUTC(), files)) {
            assertEquals(file.toAbsolutePath(), storage.getDataFile());
            storage.save(JsonSessionStorageTest.populatedState());
            assertEquals(2, storage.load().state().getRecipeBook().size());
            assertEquals(2, files.fallbacks);
            try (Stream<Path> paths = Files.list(directory)) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".tmp")));
            }
        }
    }

    @Test
    void load_releasedLockOrClosedChannel_refusesAccessEvenWhenHolderStillExists() throws Exception {
        FaultyFiles files = new FaultyFiles();
        JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("state.json"),
                Clock.systemUTC(), files);
        files.channel.acquiredLock.release();
        assertThrows(StorageException.class, storage::load);
        storage.close();

        FaultyFiles closedFiles = new FaultyFiles();
        JsonSessionStorage closedStorage = JsonSessionStorage.open(directory.resolve("state.json"),
                Clock.systemUTC(), closedFiles);
        closedFiles.channel.reportValidAfterClose = true;
        closedFiles.channel.close();
        assertThrows(StorageException.class, closedStorage::load);
        assertThrows(StorageException.class, closedStorage::close);
    }

    @Test
    void load_missingPrimaryAndInvalidBackup_preservesBackupBeforeStartingEmpty() throws Exception {
        Path backup = directory.resolve("state.backup.json");
        Files.writeString(backup, "invalid backup");
        try (JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("state.json"))) {
            assertTrue(storage.load().warning().orElseThrow().contains("started with empty data"));
            assertFalse(Files.exists(backup));
            try (Stream<Path> paths = Files.list(directory)) {
                Path preserved = paths.filter(path -> path.toString().contains("corrupt-backup"))
                        .findFirst().orElseThrow();
                assertEquals("invalid backup", Files.readString(preserved));
            }
        }
    }

    @TestFactory
    Stream<DynamicTest> load_invalidLegacyShapeAndVersion_rejectsWithoutInventingDefaults() {
        return Stream.of("{\"schemaVersion\":1}", "{\"schemaVersion\":1,\"recordedMeals\":null}",
                "{\"schemaVersion\":2147483648}", "{\"schemaVersion\":\"1\"}")
                .map(json -> DynamicTest.dynamicTest(json, () -> {
                    Path file = Files.createTempDirectory(directory, "invalid-").resolve("state.json");
                    Files.writeString(file, json);
                    try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
                        assertTrue(storage.load().warning().isPresent());
                        assertFalse(Files.exists(file));
                    }
                }));
    }

    @Test
    void save_failedWriteAndCleanup_preservesPublishedSnapshotAndCanRetry() throws Exception {
        FaultyFiles files = new FaultyFiles();
        Path file = directory.resolve(".state");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file, Clock.systemUTC(), files)) {
            storage.save(JsonSessionStorageTest.populatedState());
            String before = Files.readString(file);
            files.failTemporaryOpen = true;
            files.failCleanup = true;
            assertThrows(StorageException.class, () -> storage.save(new ApplicationState()));
            assertEquals(before, Files.readString(file));
            files.failTemporaryOpen = false;
            files.failCleanup = false;
            storage.save(new ApplicationState());
            assertEquals(0, storage.load().state().getRecipeBook().size());
        }
    }

    @Test
    void load_quarantineFailure_keepsDamagedPrimaryAndValidBackup() throws Exception {
        FaultyFiles files = new FaultyFiles();
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file, Clock.systemUTC(), files)) {
            storage.save(JsonSessionStorageTest.populatedState());
            String backup = Files.readString(directory.resolve("state.backup.json"));
            Files.writeString(file, "damaged");
            files.failMove = true;
            assertThrows(StorageException.class, storage::load);
            assertEquals("damaged", Files.readString(file));
            assertEquals(backup, Files.readString(directory.resolve("state.backup.json")));
        }
    }

    @Test
    void save_replacementFailure_keepsPrimaryAndCleansTemporaryFile() throws Exception {
        FaultyFiles files = new FaultyFiles();
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file, Clock.systemUTC(), files)) {
            storage.save(JsonSessionStorageTest.populatedState());
            String before = Files.readString(file);
            files.failMove = true;
            assertThrows(StorageException.class, () -> storage.save(new ApplicationState()));
            assertEquals(before, Files.readString(file));
            try (Stream<Path> paths = Files.list(directory)) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".tmp")));
            }
        }
    }

    @Test
    void save_oversizedLiveSnapshot_rejectsBeforeReplacingEitherFile() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(new ApplicationState());
            String before = Files.readString(file);
            ApplicationState large = new ApplicationState();
            Recipe recipe = new Recipe("x".repeat(900_000), 0, 0, 0, 0,
                    List.of(new GroceryItem("Rice", BigDecimal.ONE, "g")));
            for (int index = 0; index < 13; index++) {
                large.getRecipeBook().add(recipe);
            }
            assertThrows(StorageException.class, () -> storage.save(large));
            assertEquals(before, Files.readString(file));
        }
    }

    /** Injects only the requested failure while all remaining operations use real temporary files. */
    private static class FaultyFiles extends StorageFiles {
        private TestLockChannel channel;
        private boolean noLock;
        private boolean failAcquisition;
        private boolean failCloseOnOpen;
        private RuntimeException acquisitionRuntimeFailure;
        private boolean rejectAtomicMove;
        private boolean failTemporaryOpen;
        private boolean failCleanup;
        private boolean failMove;
        private int fallbacks;

        @Override
        FileChannel open(Path path, OpenOption... options) throws IOException {
            if (path.toString().endsWith(".lock")) {
                channel = new TestLockChannel(super.open(path, options));
                channel.returnNoLock = noLock;
                channel.failAcquisition = failAcquisition;
                channel.failClose = failCloseOnOpen;
                channel.acquisitionRuntimeFailure = acquisitionRuntimeFailure;
                return channel;
            }
            if (failTemporaryOpen) {
                throw new IOException("Temporary file unavailable");
            }
            return super.open(path, options);
        }

        @Override
        Path move(Path source, Path target, CopyOption... options) throws IOException {
            if (failMove) {
                throw new IOException("Move failed");
            }
            if (rejectAtomicMove) {
                if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "simulated");
                }
                fallbacks++;
            }
            return super.move(source, target, options);
        }

        @Override
        void deleteIfExists(Path path) throws IOException {
            if (failCleanup) {
                throw new IOException("Cleanup failed");
            }
            super.deleteIfExists(path);
        }
    }
}
