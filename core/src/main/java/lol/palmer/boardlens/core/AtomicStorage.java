package lol.palmer.boardlens.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Durable, atomic replacement of a single local file. */
public final class AtomicStorage {
    private AtomicStorage() {}

    @FunctionalInterface
    public interface Writer {
        /** Write all content without closing the supplied stream. */
        void write(OutputStream output) throws IOException;
    }

    /** No non-atomic fallback: sync, close, or move failures are reported to the caller. */
    public static void write(File file, Writer writer) throws IOException {
        Path target = path(file);
        if (target.getParent() == null || target.getFileName() == null)
            throw new IOException("A storage file must have a parent directory and a name.");
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + "-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                writer.write(output);
                output.flush();
                output.getFD().sync();
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException | Error failure) {
            try { Files.deleteIfExists(temporary); }
            catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static void delete(File file) throws IOException {
        Files.deleteIfExists(path(file));
    }

    private static Path path(File file) throws IOException {
        try { return file.toPath().toAbsolutePath(); }
        catch (InvalidPathException invalid) { throw new IOException("Invalid storage path.", invalid); }
    }
}
