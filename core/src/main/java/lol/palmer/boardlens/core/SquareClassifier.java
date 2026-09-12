package lol.palmer.boardlens.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * Local, calibrated nearest-template classifier for a rectified overhead photo.
 * Supply corrected a8..h1 labels for YOUR board and piece set. The start position
 * plus a second board with both kings and queens swapped covers all thirteen
 * labels on both square colors. No pretrained weights or chess-count rules exist.
 *
 * <p>Each square is area-resampled to 24x24. Its border estimates the background;
 * background-relative luminance and two opponent colors reduce modest lighting
 * changes. An 8x8 spatial grid retains these three channels, foreground contrast,
 * and edge strength: 320 bounded floats per square. Templates are kept separately
 * for light and dark squares, and each class uses its nearest stored template.
 *
 * <p>Use a clear, centered OVERHEAD view with similar lighting and piece placement
 * to calibration. Robustness is limited under strong light/shadows, changed white
 * balance, viewing angle/parallax, unfamiliar sets, rotation of asymmetric pieces,
 * off-center pieces, or occlusion. Similar-looking tops can be indistinguishable.
 * Review flags are conservative heuristics, not calibrated probabilities or a
 * guarantee of correctness. Synthetic fixture tests do not establish accuracy on
 * physical boards; all predictions should remain user-correctable.
 *
 * <p>At most 20 reference boards are retained; adding another throws rather than
 * silently discarding corrections. V1 storage is exactly 20 + boards * 81,984
 * bytes (163,988 for two boards, 1,639,700 at capacity), excluding runtime object
 * overhead. Methods synchronize model state; callers must not mutate input arrays
 * during a call. Input/output streams remain owned by the caller.
 */
public final class SquareClassifier {
    private static final String LABELS = ".PNBRQKpnbrqk";
    private static final int MAX_BOARDS = 20;
    private static final int PATCH = 24;
    private static final int GRID = 8;
    private static final int CHANNELS = 5;
    private static final int DESCRIPTOR_LENGTH = GRID * GRID * CHANNELS;
    private static final int MAGIC = 0x424c5343; // BLSC
    private static final int VERSION = 1;
    // Fixed heuristic cutoffs, NOT fitted/calibrated probabilities.
    private static final double MAX_DISTANCE = .16;
    private static final double MIN_CLASS_GAP = .012;
    private static final double MAX_DISTANCE_RATIO = .80;

    private final List<Reference> references = new ArrayList<>();
    private final boolean[][] coverage = new boolean[2][LABELS.length()];

    /** Independent mutable output arrays in a8..h1 order, each of length 64. */
    public static final class Result {
        public final char[] pieces;
        public final boolean[] review;
        /** Weighted RMS descriptor distance; lower is better, never a probability.
         * Positive infinity means no template exists for that square color. */
        public final double[] distances;

        private Result(char[] pieces, boolean[] review, double[] distances) {
            this.pieces = pieces;
            this.review = review;
            this.distances = distances;
        }
    }

    private static final class Reference {
        final byte[] labels;
        final float[][] descriptors;

        Reference(byte[] labels, float[][] descriptors) {
            this.labels = labels;
            this.descriptors = descriptors;
        }
    }

    public SquareClassifier() { }

    /**
     * Adds one corrected reference board atomically, copying labels and extracting
     * descriptors immediately. Raw pixels are not retained. Size must be 64..2048,
     * divisible by eight, with exactly size*size ARGB pixels; alpha is ignored.
     *
     * @throws IllegalArgumentException for invalid dimensions, lengths, or labels
     * @throws IllegalStateException when twenty reference boards already exist
     */
    public synchronized void addExample(int[] boardPixels, int size, char[] pieces) {
        validateRaster(boardPixels, size);
        if (pieces == null || pieces.length != 64) {
            throw new IllegalArgumentException("Exactly 64 corrected labels are required");
        }
        byte[] labels = new byte[64];
        for (int i = 0; i < 64; i++) {
            int label = LABELS.indexOf(pieces[i]);
            if (label < 0) throw new IllegalArgumentException("Invalid piece label at square " + i);
            labels[i] = (byte) label;
        }
        if (references.size() >= MAX_BOARDS) {
            throw new IllegalStateException("Calibration is full (20 boards); clear it to start again");
        }
        float[][] descriptors = new float[64][];
        for (int i = 0; i < 64; i++) descriptors[i] = describe(boardPixels, size, i);
        append(new Reference(labels, descriptors));
    }

    /**
     * Predicts squares independently; never inserts kings or repairs legal counts.
     * Missing color coverage, a large distance, or close competing CLASS distances
     * requires review. Repeated references of one class are not competitors.
     * Without references the result is '.', review=true, distance=positive infinity.
     * Input raster requirements are the same as {@link #addExample}.
     */
    public synchronized Result recognize(int[] boardPixels, int size) {
        validateRaster(boardPixels, size);
        char[] pieces = new char[64];
        boolean[] review = new boolean[64];
        double[] distances = new double[64];
        Arrays.fill(pieces, '.');
        Arrays.fill(review, true);
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        boolean[] complete = {covered(0) == LABELS.length(), covered(1) == LABELS.length()};
        if (!references.isEmpty()) {
            for (int square = 0; square < 64; square++) {
                int color = color(square);
                float[] descriptor = describe(boardPixels, size, square);
                double[] classDistances = new double[LABELS.length()];
                Arrays.fill(classDistances, Double.POSITIVE_INFINITY);
                for (Reference reference : references) {
                    for (int i = 0; i < 64; i++) {
                        if (color(i) != color) continue;
                        int label = reference.labels[i];
                        classDistances[label] = Math.min(classDistances[label],
                                squaredDistance(descriptor, reference.descriptors[i]));
                    }
                }
                double best = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
                int bestLabel = 0;
                for (int label = 0; label < LABELS.length(); label++) {
                    double distance = Math.sqrt(classDistances[label]);
                    if (distance < best) {
                        second = best;
                        best = distance;
                        bestLabel = label;
                    } else if (distance < second) {
                        second = distance;
                    }
                }
                pieces[square] = LABELS.charAt(bestLabel);
                distances[square] = best;
                review[square] = !complete[color] || !Double.isFinite(best)
                        || best > MAX_DISTANCE || second - best < MIN_CLASS_GAP
                        || best >= MAX_DISTANCE_RATIO * second;
            }
        }
        return new Result(pieces, review, distances);
    }

    /** Number of reference boards, not number of squares/templates. */
    public synchronized int exampleCount() { return references.size(); }

    /** True only when both square colors have all .PNBRQKpnbrqk labels.
     * This reports coverage, not image quality or recognition accuracy. */
    public synchronized boolean ready() {
        return covered(0) == LABELS.length() && covered(1) == LABELS.length();
    }

    public synchronized String coverageSummary() {
        return coverageText(0, "Light") + "; " + coverageText(1, "Dark");
    }

    private String coverageText(int color, String name) {
        StringBuilder text = new StringBuilder(name).append(" squares: ")
                .append(covered(color)).append("/13");
        if (covered(color) < LABELS.length()) {
            text.append(" (missing ");
            for (int label = 0; label < LABELS.length(); label++) {
                if (!coverage[color][label]) text.append(LABELS.charAt(label));
            }
            text.append(')');
        }
        return text.toString();
    }

    private int covered(int color) {
        int count = 0;
        for (boolean present : coverage[color]) if (present) count++;
        return count;
    }

    /** Discards all reference boards and coverage; the classifier may be reused. */
    public synchronized void clear() {
        references.clear();
        for (boolean[] color : coverage) Arrays.fill(color, false);
    }

    /**
     * V1 big-endian format: four int32s (BLSC magic, version, descriptor length,
     * board count), then boardCount*64 records (uint8 label index into LABELS and
     * 320 IEEE-754 float32s), then uint32 CRC32 over all preceding bytes.
     * The checksum detects accidental corruption; it is not authentication.
     * Flushes buffered bytes without closing the caller's stream.
     */
    public synchronized void write(OutputStream output) throws IOException {
        if (output == null) throw new IOException("Missing calibration output stream");
        CRC32 crc = new CRC32();
        DataOutputStream out = new DataOutputStream(
                new CheckedOutputStream(new BufferedOutputStream(output), crc));
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(DESCRIPTOR_LENGTH);
        out.writeInt(references.size());
        for (Reference reference : references) {
            for (int i = 0; i < 64; i++) {
                out.writeByte(reference.labels[i]);
                for (float value : reference.descriptors[i]) out.writeFloat(value);
            }
        }
        int checksum = (int) crc.getValue();
        out.writeInt(checksum);
        out.flush();
    }

    /**
     * Reads exactly one complete model, requiring EOF after its checksum. Rejects
     * unknown versions, excessive counts, invalid labels/descriptors, truncation,
     * checksum mismatch and trailing data with IOException, before exposing state.
     * Header sizes are checked before allocation; no Java object deserialization.
     * Does not close the caller's stream, including on failure.
     */
    public static SquareClassifier read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing calibration input stream");
        CRC32 crc = new CRC32();
        DataInputStream in = new DataInputStream(new CheckedInputStream(new BufferedInputStream(input), crc));
        if (in.readInt() != MAGIC) throw new IOException("Not a BoardLens calibration");
        if (in.readInt() != VERSION) throw new IOException("Unsupported calibration version");
        if (in.readInt() != DESCRIPTOR_LENGTH) throw new IOException("Invalid descriptor length");
        int count = in.readInt();
        if (count < 0 || count > MAX_BOARDS) throw new IOException("Invalid reference board count");
        SquareClassifier classifier = new SquareClassifier();
        for (int board = 0; board < count; board++) {
            byte[] labels = new byte[64];
            float[][] descriptors = new float[64][DESCRIPTOR_LENGTH];
            for (int square = 0; square < 64; square++) {
                int label = in.readUnsignedByte();
                if (label >= LABELS.length()) throw new IOException("Invalid calibration label");
                labels[square] = (byte) label;
                for (int feature = 0; feature < DESCRIPTOR_LENGTH; feature++) {
                    float value = in.readFloat();
                    if (!Float.isFinite(value) || value < -1 || value > 1
                            || (feature % CHANNELS >= 3 && value < 0)) {
                        throw new IOException("Invalid calibration descriptor");
                    }
                    descriptors[square][feature] = value;
                }
            }
            classifier.append(new Reference(labels, descriptors));
        }
        int checksum = (int) crc.getValue();
        if (in.readInt() != checksum) throw new IOException("Calibration checksum mismatch");
        if (in.read() != -1) throw new IOException("Trailing calibration data");
        return classifier;
    }

    private void append(Reference reference) {
        references.add(reference);
        for (int i = 0; i < 64; i++) coverage[color(i)][reference.labels[i]] = true;
    }

    private static int color(int square) { return (square / 8 + square % 8) & 1; }

    private static void validateRaster(int[] pixels, int size) {
        if (size < 64 || size > 2048 || size % 8 != 0
                || pixels == null || pixels.length != (long) size * size) {
            throw new IllegalArgumentException("Board must contain size*size pixels, size 64..2048 divisible by eight");
        }
    }

    private static float[] describe(int[] pixels, int size, int square) {
        double[][] patch = sampleSquare(pixels, size, square);
        double[] background = new double[3];
        for (int channel = 0; channel < 3; channel++) {
            double[] border = new double[PATCH * PATCH - (PATCH - 6) * (PATCH - 6)];
            int count = 0;
            for (int y = 0; y < PATCH; y++) {
                for (int x = 0; x < PATCH; x++) {
                    if (x < 3 || y < 3 || x >= PATCH - 3 || y >= PATCH - 3) {
                        border[count++] = patch[channel][y * PATCH + x];
                    }
                }
            }
            Arrays.sort(border);
            background[channel] = (border[count / 2 - 1] + border[count / 2]) / 2;
        }
        double scale = Math.max(.20, luminance(background[0], background[1], background[2]));
        double[] foreground = new double[PATCH * PATCH];
        for (int i = 0; i < PATCH * PATCH; i++) {
            double r = (patch[0][i] - background[0]) / scale;
            double g = (patch[1][i] - background[1]) / scale;
            double b = (patch[2][i] - background[2]) / scale;
            patch[0][i] = clamp(luminance(r, g, b));
            patch[1][i] = clamp(r - g);
            patch[2][i] = clamp(b - g);
            // A soft foreground mask avoids turning tiny board texture into a
            // full-strength piece shape, while retaining contrast on both colors.
            foreground[i] = Math.min(1, Math.max(0, (Math.sqrt((r * r + g * g + b * b) / 3) - .035) / .22));
        }
        double[] features = new double[DESCRIPTOR_LENGTH];
        int cellSide = PATCH / GRID;
        double cellArea = cellSide * cellSide;
        for (int y = 0; y < PATCH; y++) {
            for (int x = 0; x < PATCH; x++) {
                int i = y * PATCH + x;
                int cell = ((y / cellSide) * GRID + x / cellSide) * CHANNELS;
                for (int channel = 0; channel < 3; channel++) features[cell + channel] += patch[channel][i] / cellArea;
                features[cell + 3] += foreground[i] / cellArea;
                double edge = 0;
                int left = y * PATCH + Math.max(0, x - 1), right = y * PATCH + Math.min(PATCH - 1, x + 1);
                int up = Math.max(0, y - 1) * PATCH + x, down = Math.min(PATCH - 1, y + 1) * PATCH + x;
                for (int channel = 0; channel < 3; channel++) {
                    double weight = channel == 0 ? 1.5 : .3;
                    edge += weight * (Math.abs(patch[channel][right] - patch[channel][left])
                            + Math.abs(patch[channel][down] - patch[channel][up]));
                }
                features[cell + 4] += Math.min(1, edge) / cellArea;
            }
        }
        float[] result = new float[DESCRIPTOR_LENGTH];
        for (int i = 0; i < result.length; i++) result[i] = (float) clamp(features[i]);
        return result;
    }

    /** Area averaging includes every source pixel, also for noninteger cell sizes. */
    private static double[][] sampleSquare(int[] pixels, int size, int square) {
        int side = size / 8, originX = square % 8 * side, originY = square / 8 * side;
        double step = (double) side / PATCH;
        double[][] patch = new double[3][PATCH * PATCH];
        for (int y = 0; y < PATCH; y++) {
            double top = y * step, bottom = (y + 1) * step;
            for (int x = 0; x < PATCH; x++) {
                double left = x * step, right = (x + 1) * step;
                int i = y * PATCH + x;
                for (int sy = (int) top; sy < Math.min(side, (int) Math.ceil(bottom)); sy++) {
                    double wy = Math.min(sy + 1, bottom) - Math.max(sy, top);
                    for (int sx = (int) left; sx < Math.min(side, (int) Math.ceil(right)); sx++) {
                        double wx = Math.min(sx + 1, right) - Math.max(sx, left);
                        double weight = wx * wy / (step * step * 255);
                        int rgb = pixels[(originY + sy) * size + originX + sx];
                        patch[0][i] += ((rgb >>> 16) & 255) * weight;
                        patch[1][i] += ((rgb >>> 8) & 255) * weight;
                        patch[2][i] += (rgb & 255) * weight;
                    }
                }
            }
        }
        return patch;
    }

    private static double squaredDistance(float[] a, float[] b) {
        double total = 0;
        for (int i = 0; i < DESCRIPTOR_LENGTH; i += CHANNELS) {
            double l = a[i] - b[i], rg = a[i + 1] - b[i + 1], bg = a[i + 2] - b[i + 2];
            double mask = a[i + 3] - b[i + 3], edge = a[i + 4] - b[i + 4];
            total += l * l + .3 * (rg * rg + bg * bg) + .8 * mask * mask + .5 * edge * edge;
        }
        return total / (GRID * GRID * 2.9);
    }

    private static double luminance(double r, double g, double b) { return .2126 * r + .7152 * g + .0722 * b; }

    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
