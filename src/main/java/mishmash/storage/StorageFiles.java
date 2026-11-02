package mishmash.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/** Isolates fallible file operations so recovery paths can be tested without OS-specific permission tricks. */
class StorageFiles {
    /** Opens a channel with the same semantics as the platform filesystem. */
    FileChannel open(Path path, OpenOption... options) throws IOException {
        return FileChannel.open(path, options);
    }

    /** Moves a file using the requested replacement and atomicity options. */
    Path move(Path source, Path target, CopyOption... options) throws IOException {
        return Files.move(source, target, options);
    }

    /** Removes an abandoned temporary file if it still exists. */
    void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
