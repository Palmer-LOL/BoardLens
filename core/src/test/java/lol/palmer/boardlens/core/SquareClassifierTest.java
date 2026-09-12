package lol.palmer.boardlens.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Deterministic rendered image fixtures exercise the actual pixel classifier.
 * These deliberately simple shapes are NOT evidence of physical-board accuracy.
 */
public final class SquareClassifierTest {
    private static final String LABELS = ".PNBRQKpnbrqk";
    private static final int SIZE = 256;

    public static void main(String[] args) throws Exception {
        missingCalibrationNeedsReview();
        startingAndSwappedBoardsCompleteCoverage();
        recognizesChangedPlacements();
        toleratesSmallIlluminationChanges();
        recognizesEmptyBoardWithoutInventingKings();
        separatesSquareColors();
        ambiguousClassesNeedReview();
        unfamiliarAppearanceNeedsReview();
        roundTripAndClear();
        rejectsInvalidExamplesAtomically();
        boundsReferenceBoards();
        rejectsCorruptSerialization();
        preservesStreamOwnershipAndIoFailures();
        System.out.println("SquareClassifierTest: 13 tests passed");
    }

    private static void missingCalibrationNeedsReview() {
        SquareClassifier c = new SquareClassifier();
        check(c.exampleCount() == 0 && !c.ready(), "new classifier is uncalibrated");
        check(c.coverageSummary() != null && !c.coverageSummary().isBlank(), "coverage guidance");
        SquareClassifier.Result r = c.recognize(render(start(false), SIZE, 1, 0, false), SIZE);
        check(r != null, "recognition returns a result even without calibration");
        for (int i = 0; i < 64; i++) {
            check(r.pieces[i] == '.' && r.review[i], "uncalibrated square must require correction");
            check(r.distances[i] == Double.POSITIVE_INFINITY, "no reference distance is infinity");
        }
        c.addExample(render(start(false), SIZE, 1, 0, false), SIZE, start(false));
        r = c.recognize(render(start(false), SIZE, 1, 0, false), SIZE);
        for (boolean review : r.review) check(review, "incomplete color coverage requires review");
    }

    private static void startingAndSwappedBoardsCompleteCoverage() {
        SquareClassifier c = new SquareClassifier();
        char[] labels = start(false);
        int[] pixels = render(labels, SIZE, 1, 0, false);
        c.addExample(pixels, SIZE, labels);
        check(c.exampleCount() == 1 && !c.ready(), "one start board lacks opposite-color kings/queens");
        labels[0] = '?'; Arrays.fill(pixels, 0);
        c.addExample(render(start(true), SIZE, 1, 0, false), SIZE, start(true));
        check(c.exampleCount() == 2 && c.ready(), "two corrected reference boards cover all labels");
        String summary = c.coverageSummary().toLowerCase();
        check(summary.contains("light") && summary.contains("dark") && summary.contains("13/13"),
                "coverage reports both square colors");
        SquareClassifier.Result r = c.recognize(render(start(false), SIZE, 1, 0, false), SIZE);
        expectPieces(start(false), r, "caller mutations must not change stored references");
        for (int i = 8; i < 16; i++) check(!r.review[i], "duplicate pawn references are not class ambiguity");
    }

    private static void recognizesChangedPlacements() {
        SquareClassifier c = calibrated(false);
        char[] moved = changed();
        SquareClassifier.Result r = c.recognize(render(moved, SIZE, 1, 0, false), SIZE);
        expectPieces(moved, r, "new placements, including multiple kings and promoted pieces");
        for (int i = 0; i < 64; i++) {
            check(Double.isFinite(r.distances[i]) && r.distances[i] >= 0, "finite heuristic distance");
        }
        // The black queen and rook tops have a small class gap on a light square.
        // Correct recognition must still allow a conservative request for review.
        check(r.review[32], "near competing queen/rook appearances require review");
        r.pieces[0] = '?'; r.review[0] = true; r.distances[0] = -1;
        expectPieces(moved, c.recognize(render(moved, SIZE, 1, 0, false), SIZE), "results are independent");
    }

    private static void toleratesSmallIlluminationChanges() {
        SquareClassifier c = calibrated(false);
        for (double gain : new double[] {.92, 1.06}) {
            int bias = gain < 1 ? -3 : 3;
            expectPieces(changed(), c.recognize(render(changed(), SIZE, gain, bias, false), SIZE),
                    "modest brightness gain and offset " + gain);
        }
        expectPieces(changed(), c.recognize(render(changed(), 512, 1, 0, false), 512),
                "same set at a different raster resolution");
    }

    private static void recognizesEmptyBoardWithoutInventingKings() {
        SquareClassifier c = calibrated(false);
        char[] empty = empty();
        SquareClassifier.Result r = c.recognize(render(empty, SIZE, 1.04, 2, false), SIZE);
        expectPieces(empty, r, "empty board, no inserted kings or legal-count repairs");
        for (boolean review : r.review) check(!review, "familiar empty squares are clear matches");
    }

    private static void separatesSquareColors() {
        int[] sameAppearance = new int[SIZE * SIZE];
        Arrays.fill(sameAppearance, 0xff777777);
        char[] labels = new char[64];
        for (int i = 0; i < 64; i++) labels[i] = ((i / 8 + i % 8) & 1) == 0 ? 'P' : 'p';
        SquareClassifier c = new SquareClassifier();
        c.addExample(sameAppearance, SIZE, labels);
        expectPieces(labels, c.recognize(sameAppearance, SIZE), "identical descriptors isolated by square color");
        check(!c.ready(), "two labels do not constitute complete calibration");
    }

    private static void ambiguousClassesNeedReview() {
        // This fixture set deliberately gives kings and queens identical visible tops.
        SquareClassifier c = calibrated(true);
        char[] labels = empty();
        labels[27] = 'K'; labels[28] = 'Q'; labels[35] = 'q'; labels[36] = 'k';
        SquareClassifier.Result r = c.recognize(render(labels, SIZE, 1, 0, true), SIZE);
        for (int i : new int[] {27, 28, 35, 36}) {
            check(r.review[i], "indistinguishable classes must be flagged at " + i);
            check(Character.isUpperCase(r.pieces[i]) == Character.isUpperCase(labels[i]), "piece color");
            check("KQkq".indexOf(r.pieces[i]) >= 0, "ambiguous nearest class retained");
        }
    }

    private static void unfamiliarAppearanceNeedsReview() {
        SquareClassifier c = calibrated(false);
        int[] pixels = render(empty(), SIZE, 1, 0, false);
        int side = SIZE / 8;
        for (int y = 1; y < side - 1; y++) {
            for (int x = 1; x < side - 1; x++) {
                pixels[(3 * side + y) * SIZE + 3 * side + x] = ((x / 3 + y / 3) & 1) == 0
                        ? 0xffff00ff : 0xff00ffff;
            }
        }
        SquareClassifier.Result r = c.recognize(pixels, SIZE);
        check(r.review[27] && r.distances[27] > .1, "unfamiliar/occluded square needs absolute-distance review");
    }

    private static void roundTripAndClear() throws IOException {
        SquareClassifier c = calibrated(false);
        int[] query = render(changed(), SIZE, .96, 1, false);
        SquareClassifier.Result before = c.recognize(query, SIZE);
        SquareClassifier restored = SquareClassifier.read(new ByteArrayInputStream(save(c)));
        check(restored.ready() && restored.exampleCount() == 2, "coverage survives save/load");
        SquareClassifier.Result after = restored.recognize(query, SIZE);
        check(Arrays.equals(before.pieces, after.pieces), "saved labels");
        check(Arrays.equals(before.review, after.review), "saved review decisions");
        check(Arrays.equals(before.distances, after.distances), "exact saved descriptors");
        restored.clear();
        check(!restored.ready() && restored.exampleCount() == 0, "clear removes references and coverage");
        for (boolean review : restored.recognize(query, SIZE).review) check(review, "clear resets recognition");
        SquareClassifier empty = SquareClassifier.read(new ByteArrayInputStream(save(restored)));
        check(!empty.ready() && empty.exampleCount() == 0, "empty serialization round trip");
        restored.addExample(render(start(false), SIZE, 1, 0, false), SIZE, start(false));
        check(restored.exampleCount() == 1, "calibration works again after clear");
        check(c.exampleCount() == 2, "restored model has independent state");
    }

    private static void rejectsInvalidExamplesAtomically() {
        SquareClassifier c = new SquareClassifier();
        int[] pixels = render(start(false), SIZE, 1, 0, false);
        invalid(() -> c.addExample(null, SIZE, start(false)));
        invalid(() -> c.addExample(pixels, SIZE, null));
        invalid(() -> c.addExample(pixels, SIZE, new char[63]));
        char[] bad = start(false); bad[63] = '?';
        invalid(() -> c.addExample(pixels, SIZE, bad));
        bad[63] = '\u0100'; invalid(() -> c.addExample(pixels, SIZE, bad));
        for (int size : new int[] {0, -256, 8, 63, 65, 2049, 2056, 65536, Integer.MAX_VALUE}) {
            invalid(() -> c.addExample(pixels, size, start(false)));
            invalid(() -> c.recognize(pixels, size));
        }
        invalid(() -> c.recognize(null, SIZE));
        invalid(() -> c.recognize(new int[SIZE * SIZE - 1], SIZE));
        invalid(() -> c.addExample(new int[SIZE * SIZE + 1], SIZE, start(false)));
        check(c.exampleCount() == 0, "invalid final label must not partially insert a board");
        c.addExample(render(empty(), 64, 1, 0, false), 64, empty());
        c.recognize(render(empty(), 72, 1, 0, false), 72);
        check(c.exampleCount() == 1, "minimum and non-power-of-two sizes accepted");
    }

    private static void boundsReferenceBoards() throws IOException {
        SquareClassifier c = new SquareClassifier();
        char[] labels = start(false);
        int[] pixels = render(labels, 64, 1, 0, false);
        for (int i = 0; i < 20; i++) c.addExample(pixels, 64, labels);
        try { c.addExample(pixels, 64, labels); throw new AssertionError("reference overflow accepted"); }
        catch (IllegalStateException expected) { /* At capacity; no eviction of corrections. */ }
        check(c.exampleCount() == 20, "overflow leaves existing references intact");
        byte[] bytes = save(c);
        check(bytes.length < 4_000_000, "bounded compact descriptor storage");
        check(SquareClassifier.read(new ByteArrayInputStream(bytes)).exampleCount() == 20,
                "maximum model can be restored");
    }

    private static void rejectsCorruptSerialization() throws IOException {
        byte[] valid = save(calibrated(false));
        for (int length : new int[] {0, 1, 15, 16, 17, valid.length / 2, valid.length - 1}) {
            badModel(Arrays.copyOf(valid, length));
        }
        byte[] corrupt = valid.clone(); corrupt[corrupt.length / 2] ^= 1; badModel(corrupt);
        badModel(Arrays.copyOf(valid, valid.length + 1));
        // The documented v1 header is magic, version, descriptor length, board count.
        // Recompute CRC so these tests exercise validation, not just the checksum.
        for (int[] change : new int[][] {{0, 0}, {4, 2}, {8, Integer.MAX_VALUE},
                {12, -1}, {12, 21}, {12, Integer.MAX_VALUE}}) {
            corrupt = valid.clone(); ByteBuffer.wrap(corrupt).putInt(change[0], change[1]);
            badModel(checksummed(corrupt));
        }
        corrupt = valid.clone(); corrupt[16] = 13; badModel(checksummed(corrupt));
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.MAX_VALUE, 1.01f, -1.01f}) {
            corrupt = valid.clone(); ByteBuffer.wrap(corrupt).putFloat(17, bad);
            badModel(checksummed(corrupt));
        }
    }

    private static void preservesStreamOwnershipAndIoFailures() throws IOException {
        SquareClassifier c = calibrated(false);
        class Output extends ByteArrayOutputStream {
            boolean closed;
            @Override public void close() { closed = true; }
        }
        Output output = new Output(); c.write(output);
        check(!output.closed, "write leaves caller's stream open");
        class Input extends ByteArrayInputStream {
            boolean closed;
            Input(byte[] bytes) { super(bytes); }
            @Override public void close() { closed = true; }
        }
        Input input = new Input(output.toByteArray()); SquareClassifier.read(input);
        check(!input.closed, "read leaves caller's stream open");
        ioFailure(() -> c.write(new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("disk full"); }
        }));
        ioFailure(() -> SquareClassifier.read(new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failure"); }
        }));
        ioFailure(() -> c.write(null));
        ioFailure(() -> SquareClassifier.read(null));
    }

    private static SquareClassifier calibrated(boolean twins) {
        SquareClassifier c = new SquareClassifier();
        for (boolean swapped : new boolean[] {false, true}) {
            char[] labels = start(swapped);
            c.addExample(render(labels, SIZE, 1, 0, twins), SIZE, labels);
        }
        return c;
    }

    private static char[] start(boolean swapped) {
        return ((swapped ? "rnbkqbnr" : "rnbqkbnr") + "pppppppp" + ".".repeat(32)
                + "PPPPPPPP" + (swapped ? "RNBKQBNR" : "RNBQKBNR")).toCharArray();
    }

    private static char[] changed() {
        char[] labels = new char[64];
        for (int i = 0; i < 64; i++) labels[i] = LABELS.charAt((i * 5 + 7) % 13);
        return labels;
    }

    private static char[] empty() { char[] labels = new char[64]; Arrays.fill(labels, '.'); return labels; }

    private static int[] render(char[] labels, int size, double gain, int bias, boolean twins) {
        int[] result = new int[size * size];
        int side = size / 8;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int row = y / side, col = x / side;
                double u = (x % side + .5) / side - .5, v = (y % side + .5) / side - .5;
                boolean light = ((row + col) & 1) == 0;
                int r = light ? 181 : 83, g = light ? 157 : 105, b = light ? 119 : 79;
                char piece = labels[row * 8 + col];
                if (piece != '.') {
                    boolean white = Character.isUpperCase(piece);
                    double radius = Math.hypot(u, v);
                    if (radius < .30) {
                        r = white ? 158 : 62; g = white ? 153 : 62; b = white ? 141 : 67;
                    }
                    char kind = Character.toUpperCase(piece);
                    if (twins && kind == 'K') kind = 'Q';
                    boolean top = switch (kind) {
                        case 'P' -> radius < .135;
                        case 'N' -> (u > -.18 && u < -.025 && Math.abs(v) < .23)
                                || (v > -.23 && v < -.07 && u > -.1 && u < .23);
                        case 'B' -> u * u / (.13 * .13) + v * v / (.255 * .255) < 1
                                && Math.abs(u - .4 * v) > .027;
                        case 'R' -> Math.abs(u) < .225 && Math.abs(v) < .225
                                && !(Math.abs(u) < .075 && Math.abs(v) > .12);
                        case 'Q' -> radius < .205 + .045 * Math.cos(6 * Math.atan2(v, u));
                        case 'K' -> (Math.abs(u) < .065 && Math.abs(v) < .26)
                                || (Math.abs(v) < .065 && Math.abs(u) < .24);
                        default -> throw new AssertionError("invalid fixture label");
                    };
                    if (top) { r = white ? 230 : 35; g = white ? 223 : 43; b = white ? 201 : 52; }
                }
                // A gentle spatial illumination gradient and fixed fine texture.
                double illumination = gain * (.94 + .08 * x / size + .04 * y / size);
                int texture = (x * 17 + y * 29) % 5 - 2;
                result[y * size + x] = 0xff000000 | channel(r * illumination + bias + texture) << 16
                        | channel(g * illumination + bias + texture) << 8
                        | channel(b * illumination + bias + texture);
            }
        }
        return result;
    }

    private static int channel(double value) { return (int) Math.max(0, Math.min(255, Math.round(value))); }

    private static byte[] save(SquareClassifier c) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); c.write(out); return out.toByteArray();
    }

    private static byte[] checksummed(byte[] bytes) {
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer.wrap(bytes).putInt(bytes.length - 4, (int) crc.getValue());
        return bytes;
    }

    private static void badModel(byte[] bytes) { ioFailure(() -> SquareClassifier.read(new ByteArrayInputStream(bytes))); }

    private interface IoAction { void run() throws IOException; }

    private static void ioFailure(IoAction action) {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("expected IOException");
    }

    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void expectPieces(char[] expected, SquareClassifier.Result actual, String message) {
        check(actual.pieces.length == 64 && actual.review.length == 64 && actual.distances.length == 64,
                "64 output squares");
        check(Arrays.equals(expected, actual.pieces), message + "\nexpected " + new String(expected)
                + "\nactual   " + new String(actual.pieces));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
