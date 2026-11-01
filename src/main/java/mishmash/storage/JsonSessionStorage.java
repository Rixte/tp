package mishmash.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import mishmash.model.ApplicationState;
import tools.jackson.core.JsonFactory;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists complete application snapshots as strict, versioned JSON with backup recovery and process locking.
 */
public final class JsonSessionStorage implements SessionStorage {
    /** Default data file relative to the process working directory. */
    public static final Path DEFAULT_DATA_FILE = Path.of("data", "mishmash.json");

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_NESTING_DEPTH = 100;
    private static final int MAX_STRING_LENGTH = 1_000_000;
    private static final int MAX_NUMBER_LENGTH = 1_000;
    private static final DateTimeFormatter QUARANTINE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path dataFile;
    private final Path backupFile;
    private final Path lockFile;
    private final Clock clock;
    private final JsonMapper mapper;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean isClosed;

    /**
     * Opens storage and acquires an exclusive lock for the data file's directory.
     *
     * @param dataFile The primary JSON file to load and replace.
     * @return Locked storage that must be closed by its owner.
     * @throws StorageException If the directory or exclusive lock cannot be acquired.
     */
    public static JsonSessionStorage open(Path dataFile) throws StorageException {
        return open(dataFile, Clock.systemUTC());
    }

    /** Opens storage with an injectable clock for deterministic recovery tests. */
    static JsonSessionStorage open(Path dataFile, Clock clock) throws StorageException {
        Path normalizedFile = Objects.requireNonNull(dataFile, "dataFile").toAbsolutePath().normalize();
        Path parent = normalizedFile.getParent();
        if (parent == null || normalizedFile.getFileName() == null) {
            throw new StorageException("The data file must have a parent directory and file name.");
        }
        try {
            Files.createDirectories(parent);
            Path lockFile = siblingWithSuffix(normalizedFile, ".lock");
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    channel.close();
                    throw alreadyRunning();
                }
                return new JsonSessionStorage(normalizedFile, clock, channel, lock);
            } catch (OverlappingFileLockException e) {
                channel.close();
                throw alreadyRunning(e);
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }
        } catch (StorageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new StorageException("Could not prepare the MishMash data directory.", e);
        }
    }

    private JsonSessionStorage(Path dataFile, Clock clock, FileChannel lockChannel, FileLock lock) {
        this.dataFile = dataFile;
        this.backupFile = siblingWithSuffix(dataFile, ".backup.json");
        this.lockFile = siblingWithSuffix(dataFile, ".lock");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lockChannel = Objects.requireNonNull(lockChannel, "lockChannel");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.mapper = createMapper();
    }

    @Override
    public LoadResult load() throws StorageException {
        requireOpen();
        if (Files.notExists(dataFile)) {
            if (Files.notExists(backupFile)) {
                return LoadResult.normal(new ApplicationState());
            }
            return recoverFromBackup("The primary data file was missing.");
        }

        try {
            return LoadResult.normal(readState(dataFile));
        } catch (StorageException primaryFailure) {
            if (Files.exists(backupFile)) {
                try {
                    ApplicationState backupState = readState(backupFile);
                    Path quarantined = quarantine(dataFile, "corrupt");
                    writeAtomically(dataFile, Files.readAllBytes(backupFile));
                    return LoadResult.recovered(backupState,
                            "Saved data was damaged, so MishMash recovered the last valid backup."
                                    + System.lineSeparator() + "The damaged file was preserved as "
                                    + quarantined.getFileName() + ".");
                } catch (StorageException | IOException backupFailure) {
                    primaryFailure.addSuppressed(backupFailure);
                }
            }
            return quarantineAndStartEmpty(primaryFailure);
        }
    }

    @Override
    public void save(ApplicationState state) throws StorageException {
        requireOpen();
        Objects.requireNonNull(state, "state");
        final byte[] newData;
        try {
            newData = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(SessionData.fromState(state));
            validateBytes(newData);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new StorageException("Could not create a valid application snapshot.", e);
        }

        byte[] previousGoodData = readValidBytes(dataFile).orElseGet(() -> readValidBytes(backupFile)
                .orElse(newData));
        quarantineIfInvalid(dataFile);
        quarantineIfInvalid(backupFile);
        writeAtomically(backupFile, previousGoodData);
        writeAtomically(dataFile, newData);
    }

    @Override
    public void close() throws StorageException {
        if (isClosed) {
            return;
        }
        isClosed = true;
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException e) {
            failure = e;
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw new StorageException("Could not release the MishMash data-file lock.", failure);
        }
    }

    /** Returns the primary file for diagnostics and tests without exposing a mutable path. */
    public Path getDataFile() {
        return dataFile;
    }

    /** Creates the strict mapper shared by load, pre-save validation, and backup recovery. */
    private static JsonMapper createMapper() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxDocumentLength(MAX_FILE_SIZE_BYTES)
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .build();
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        return JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /** Recovers a missing primary from a validated backup. */
    private LoadResult recoverFromBackup(String reason) throws StorageException {
        try {
            ApplicationState state = readState(backupFile);
            writeAtomically(dataFile, Files.readAllBytes(backupFile));
            return LoadResult.recovered(state, reason + " MishMash restored its last valid backup.");
        } catch (IOException | StorageException e) {
            return quarantineAndStartEmpty(e);
        }
    }

    /** Preserves all unreadable candidates before starting with a new empty state. */
    private LoadResult quarantineAndStartEmpty(Exception cause) throws StorageException {
        StringBuilder preserved = new StringBuilder();
        if (Files.exists(dataFile)) {
            preserved.append(quarantine(dataFile, "corrupt").getFileName());
        }
        if (Files.exists(backupFile)) {
            if (preserved.length() > 0) {
                preserved.append(", ");
            }
            preserved.append(quarantine(backupFile, "corrupt-backup").getFileName());
        }
        String files = preserved.length() == 0 ? "No readable save file was available."
                : "Unreadable files were preserved as " + preserved + ".";
        return LoadResult.recovered(new ApplicationState(),
                "Saved data could not be recovered, so MishMash started with empty data."
                        + System.lineSeparator() + files);
    }

    /** Reads, bounds, parses, and reconstructs one complete snapshot. */
    private ApplicationState readState(Path path) throws StorageException {
        try {
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE_BYTES) {
                throw new StorageException("Saved data exceeds the 10 MiB safety limit.");
            }
            return validateBytes(Files.readAllBytes(path));
        } catch (StorageException e) {
            throw e;
        } catch (IOException | IllegalArgumentException | ArithmeticException e) {
            throw new StorageException("Saved data is invalid: " + path.getFileName() + ".", e);
        }
    }

    /** Validates serialized bytes using the same strict conversion used at startup. */
    private ApplicationState validateBytes(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IOException("Saved data exceeds the 10 MiB safety limit.");
        }
        SessionData data = mapper.readValue(bytes, SessionData.class);
        if (data == null) {
            throw new IOException("Saved data must contain one JSON object.");
        }
        return data.toState();
    }

    /** Returns file bytes only when the candidate is present and fully valid. */
    private Optional<byte[]> readValidBytes(Path path) {
        if (Files.notExists(path)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            validateBytes(bytes);
            return Optional.of(bytes);
        } catch (IOException | IllegalArgumentException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    /** Preserves an invalid existing file instead of replacing it silently. */
    private void quarantineIfInvalid(Path path) throws StorageException {
        if (Files.exists(path) && readValidBytes(path).isEmpty()) {
            quarantine(path, "corrupt-during-save");
        }
    }

    /** Moves a damaged or unsupported file to a unique diagnostic name. */
    private Path quarantine(Path source, String reason) throws StorageException {
        String timestamp = QUARANTINE_TIME.format(Instant.now(clock));
        String uniqueName = stem(source) + "." + reason + "-" + timestamp + "-" + UUID.randomUUID() + ".json";
        Path destination = source.resolveSibling(uniqueName);
        try {
            return Files.move(source, destination);
        } catch (IOException e) {
            throw new StorageException("Could not preserve invalid saved data safely.", e);
        }
    }

    /** Replaces one file through a flushed same-directory temporary file. */
    private void writeAtomically(Path target, byte[] bytes) throws StorageException {
        Path temporary = null;
        try {
            validateBytes(bytes);
            temporary = Files.createTempFile(target.getParent(), stem(target) + "-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException | IllegalArgumentException | ArithmeticException e) {
            throw new StorageException("Could not replace " + target.getFileName() + " safely.", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The save failure remains primary; a later startup can safely ignore an unreferenced temp file.
                }
            }
        }
    }

    /** Rejects operations after the process lock has been released. */
    private void requireOpen() throws StorageException {
        if (isClosed || !lock.isValid() || !lockChannel.isOpen()) {
            throw new StorageException("Session storage is closed.");
        }
    }

    /** Creates a sibling name from the primary base name and a storage suffix. */
    private static Path siblingWithSuffix(Path source, String suffix) {
        return source.resolveSibling(stem(source) + suffix);
    }

    /** Returns a file name without its final extension. */
    private static String stem(Path path) {
        String name = path.getFileName().toString();
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }

    /** Returns the stable error used when another process owns the same file. */
    private static StorageException alreadyRunning() {
        return new StorageException("MishMash is already using this data folder. Close the other instance first.");
    }

    /** Returns the stable lock error while retaining its original cause. */
    private static StorageException alreadyRunning(Throwable cause) {
        return new StorageException("MishMash is already using this data folder. Close the other instance first.",
                cause);
    }
}
