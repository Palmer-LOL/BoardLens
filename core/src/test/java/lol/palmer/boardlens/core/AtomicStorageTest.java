package lol.palmer.boardlens.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Actual-filesystem checks; run directly with java, without a test framework. */
public final class AtomicStorageTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("atomic-storage-test-");
        try {
            successfulReplacement(directory);
            failedWriterPreservesOldFile(directory);
            uncheckedFailurePreservesOldFile(directory);
            failedFirstWriteLeavesNoFile(directory);
            malformedPathsFail(directory);
            syncFailurePreservesOldFile(directory);
            failedMoveCleansTemporaryFile(directory);
            deletionReportsFailure(directory);
            System.out.println("AtomicStorageTest: 8 tests passed");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.delete(path);
            }
        }
    }

    private static void successfulReplacement(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("success"));
        File target = directory.resolve("value").toFile();
        AtomicStorage.write(target, output -> output.write(bytes("old")));
        AtomicStorage.write(target, output -> {
            output.write(bytes("new content"));
            check(Files.readString(target.toPath()).equals("old"), "old file visible during write");
        });
        check(Files.readString(target.toPath()).equals("new content"), "new content installed");
        checkOnlyTarget(directory, target.toPath());
    }

    private static void failedWriterPreservesOldFile(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("writer"));
        Path target = Files.writeString(directory.resolve("value"), "old");
        IOException expected = new IOException("writer failed");
        IOException actual = expectIo(() -> AtomicStorage.write(target.toFile(), output -> {
            output.write(bytes("partial"));
            throw expected;
        }));
        check(actual == expected, "original write error propagated");
        check(Files.readString(target).equals("old"), "failed writer preserves old contents");
        checkOnlyTarget(directory, target);
    }

    private static void uncheckedFailurePreservesOldFile(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("unchecked"));
        Path target = Files.writeString(directory.resolve("value"), "old");
        RuntimeException expected = new IllegalStateException("encoder failed");
        try {
            AtomicStorage.write(target.toFile(), output -> { output.write(bytes("partial")); throw expected; });
            throw new AssertionError("unchecked writer error swallowed");
        } catch (RuntimeException actual) {
            check(actual == expected, "original unchecked error propagated");
        }
        check(Files.readString(target).equals("old"), "unchecked failure preserves old contents");
        checkOnlyTarget(directory, target);
    }

    private static void failedFirstWriteLeavesNoFile(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("first"));
        expectIo(() -> AtomicStorage.write(directory.resolve("value").toFile(), output -> {
            output.write(bytes("partial")); throw new IOException("failed");
        }));
        try (Stream<Path> paths = Files.list(directory)) {
            check(paths.count() == 0, "failed first write leaves neither base nor temporary file");
        }
    }

    private static void malformedPathsFail(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("paths"));
        Path fileParent = Files.writeString(directory.resolve("file"), "old");
        expectIo(() -> AtomicStorage.write(fileParent.resolve("child").toFile(), output -> output.write(1)));
        expectIo(() -> AtomicStorage.write(directory.resolve("missing/child").toFile(), output -> output.write(1)));
        expectIo(() -> AtomicStorage.write(new File("invalid\0path"), output -> output.write(1)));
        expectIo(() -> AtomicStorage.write(directory.getRoot().toFile(), output -> output.write(1)));
        check(Files.readString(fileParent).equals("old"), "malformed parent preserved");
        checkOnlyTarget(directory, fileParent);
    }

    private static void syncFailurePreservesOldFile(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("sync"));
        Path target = Files.writeString(directory.resolve("value"), "old");
        // Closing the real descriptor forces finalization to fail, even if the writer returns normally.
        expectIo(() -> AtomicStorage.write(target.toFile(), output -> {
            output.write(bytes("new")); output.close();
        }));
        check(Files.readString(target).equals("old"), "sync failure preserves old contents");
        checkOnlyTarget(directory, target);
    }

    private static void failedMoveCleansTemporaryFile(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("move"));
        Path target = Files.createDirectory(directory.resolve("value"));
        Path child = Files.writeString(target.resolve("child"), "old");
        expectIo(() -> AtomicStorage.write(target.toFile(), output -> output.write(bytes("new"))));
        check(Files.readString(child).equals("old"), "failed replacement preserves existing directory");
        checkOnlyTarget(directory, target);
    }

    private static void deletionReportsFailure(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve("delete"));
        Path target = Files.writeString(directory.resolve("value"), "old");
        expectIo(() -> AtomicStorage.delete(directory.toFile()));
        check(Files.readString(target).equals("old"), "failed deletion preserves existing contents");
        AtomicStorage.delete(target.toFile());
        AtomicStorage.delete(target.toFile());
        check(!Files.exists(target), "deletion succeeds and accepts missing file");
    }

    private interface IoAction { void run() throws IOException; }

    private static IOException expectIo(IoAction action) throws IOException {
        try { action.run(); }
        catch (IOException expected) { return expected; }
        throw new AssertionError("expected an IOException");
    }

    private static void checkOnlyTarget(Path directory, Path target) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            Path[] entries = paths.toArray(Path[]::new);
            check(entries.length == 1 && entries[0].equals(target), "no temporary files remain");
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
