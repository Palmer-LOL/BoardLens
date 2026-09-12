package lol.palmer.boardlens.core;

import java.util.Arrays;

/** Standalone deterministic raster/geometry tests; no Android or test framework. */
public final class BoardGeometryTest {
    public static void main(String[] args) {
        defaultsAreIndependent();
        acceptsConvexCornersInEitherWinding();
        rejectsInvalidCoordinates();
        rejectsInvalidQuadrilaterals();
        identityPreservesPixelsAndOrientation();
        rotationFollowsCornerLabels();
        interpolatesRgbAtPixelCenters();
        perspectiveIsProjective();
        insetUsesOuterBoardEdges();
        rejectsInvalidRasterDimensions();
        System.out.println("BoardGeometryTest: 10 tests passed");
    }

    private static void defaultsAreIndependent() {
        BoardGeometry.Point[] p = BoardGeometry.defaults();
        check(p != null && p.length == 4, "four default corners");
        near(p[0].x, .1); near(p[0].y, .1);
        near(p[1].x, .9); near(p[1].y, .1);
        near(p[2].x, .9); near(p[2].y, .9);
        near(p[3].x, .1); near(p[3].y, .9);
        p[0] = new BoardGeometry.Point(.2, .2);
        near(BoardGeometry.defaults()[0].x, .1);
    }

    private static void acceptsConvexCornersInEitherWinding() {
        BoardGeometry.validate(full());
        BoardGeometry.validate(points(.1, .2, .8, .1, .9, .9, .2, .7));
        BoardGeometry.validate(points(0, 0, 0, 1, 1, 1, 1, 0));
        BoardGeometry.validate(points(.5, 0, 1, .5, .5, 1, 0, .5));
    }

    private static void rejectsInvalidCoordinates() {
        invalid(() -> BoardGeometry.validate(null));
        invalid(() -> BoardGeometry.validate(new BoardGeometry.Point[3]));
        invalid(() -> BoardGeometry.validate(new BoardGeometry.Point[4]));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, -.001, 1.001}) {
            invalid(() -> BoardGeometry.validate(points(bad, 0, 1, 0, 1, 1, 0, 1)));
            invalid(() -> BoardGeometry.validate(points(0, bad, 1, 0, 1, 1, 0, 1)));
        }
    }

    private static void rejectsInvalidQuadrilaterals() {
        invalid(() -> BoardGeometry.validate(points(0, 0, 1, 1, 1, 0, 0, 1)));
        invalid(() -> BoardGeometry.validate(points(0, 0, 1, 0, .2, .2, 0, 1)));
        invalid(() -> BoardGeometry.validate(points(0, 0, 1, 0, 1, 0, 0, 1)));
        invalid(() -> BoardGeometry.validate(points(0, 0, .5, 0, 1, 0, 0, 1)));
        invalid(() -> BoardGeometry.validate(points(0, 0, 1, 0, 1, 1e-8, 0, 1e-8)));
        invalid(() -> BoardGeometry.validate(points(.5, .5, .50001, .5,
                .50001, .50001, .5, .50001)));
        invalid(() -> BoardGeometry.validate(points(0, 0, .5, 1e-9, 1, 0, 0, 1)));
    }

    private static void identityPreservesPixelsAndOrientation() {
        int[] source = new int[64 * 64];
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                source[y * 64 + x] = 0xff000000 | x << 16 | y << 8 | (x ^ y);
            }
        }
        int[] before = source.clone();
        int[] warped = BoardGeometry.warp(source, 64, 64, full(), 64);
        check(Arrays.equals(warped, before), "identity warp including all four edges");
        check(Arrays.equals(source, before), "warp must not modify source");
    }

    private static void rotationFollowsCornerLabels() {
        int[] source = new int[64 * 64];
        for (int i = 0; i < source.length; i++) source[i] = 0xff000000 | i;
        int[] result = BoardGeometry.warp(source, 64, 64,
                points(1, 1, 0, 1, 0, 0, 1, 0), 64);
        for (int i = 0; i < source.length; i++) {
            check(result[i] == source[source.length - 1 - i], "a8 defines orientation");
        }
        int[] clockwise = BoardGeometry.warp(source, 64, 64,
                points(1, 0, 1, 1, 0, 1, 0, 0), 64);
        check(clockwise[0] == source[63], "90 degree a8");
        check(clockwise[63] == source[4095], "90 degree h8");
        check(clockwise[4032] == source[0], "90 degree a1");
    }

    private static void interpolatesRgbAtPixelCenters() {
        // Alpha is deliberately mixed: RGB interpolation returns opaque output.
        int[] source = {0x00000000, 0x40ff0000, 0x8000ff00, 0xffffffff};
        int[] result = BoardGeometry.warp(source, 2, 2, full(), 64);
        // Output (31,31) is at normalized 31.5/64, source index .484375.
        // R = G = round(255 * .484375); B = round(255 * .484375^2).
        check(result[31 * 64 + 31] == 0xff7c7c3c, "bilinear RGB, not nearest neighbor");
        check(result[0] == 0xff000000, "clamp at top-left image edge");
        check(result[4095] == 0xffffffff, "clamp at bottom-right image edge");
        int[] one = BoardGeometry.warp(new int[] {0x00123456}, 1, 1, full(), 64);
        for (int pixel : one) check(pixel == 0xff123456, "single pixel image");
    }

    private static void perspectiveIsProjective() {
        int[] source = new int[256 * 256];
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) source[y * 256 + x] = 0xff000000 | x << 16 | y << 8;
        }
        BoardGeometry.Point[] p = points(.125, .125, .875, .125,
                2.0 / 3, 2.0 / 3, 1.0 / 6, 2.0 / 3);
        int[] result = BoardGeometry.warp(source, 256, 256, p, 64);
        // Independent analytic fixture: H(u,v) =
        // ((1/8 + 3u/4 + v/8)/(1+v/2), (1/8 + 7v/8)/(1+v/2)).
        // A bilinearly blended quadrilateral has the wrong interior positions.
        for (int row : new int[] {0, 7, 19, 32, 47, 63}) {
            for (int col : new int[] {0, 9, 25, 40, 63}) {
                double u = (col + .5) / 64, v = (row + .5) / 64;
                int r = (int) Math.round(256 * (.125 + .75 * u + .125 * v) / (1 + .5 * v) - .5);
                int g = (int) Math.round(256 * (.125 + .875 * v) / (1 + .5 * v) - .5);
                check(result[row * 64 + col] == (0xff000000 | r << 16 | g << 8),
                        "projective interior at " + row + "," + col);
            }
        }
    }

    private static void insetUsesOuterBoardEdges() {
        int[] source = new int[80 * 80];
        Arrays.fill(source, 0xffee00ee);
        for (int y = 8; y < 72; y++) {
            for (int x = 8; x < 72; x++) {
                source[y * 80 + x] = ((x - 8) / 8 + (y - 8) / 8) % 2 == 0
                        ? 0xffeeeeee : 0xff222222;
            }
        }
        int[] result = BoardGeometry.warp(source, 80, 80, BoardGeometry.defaults(), 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int expected = (x / 8 + y / 8) % 2 == 0 ? 0xffeeeeee : 0xff222222;
                check(result[y * 64 + x] == expected, "eight full squares with no outer frame");
            }
        }
    }

    private static void rejectsInvalidRasterDimensions() {
        int[] small = new int[64 * 64];
        for (int size : new int[] {-8, 0, 8, 63, 65, 2049, 2056, Integer.MAX_VALUE}) {
            invalid(() -> BoardGeometry.warp(small, 64, 64, full(), size));
        }
        invalid(() -> BoardGeometry.warp(null, 64, 64, full(), 64));
        invalid(() -> BoardGeometry.warp(small, 0, 64, full(), 64));
        invalid(() -> BoardGeometry.warp(small, -64, -64, full(), 64));
        invalid(() -> BoardGeometry.warp(small, 63, 64, full(), 64));
        invalid(() -> BoardGeometry.warp(small, Integer.MAX_VALUE, Integer.MAX_VALUE, full(), 64));
        invalid(() -> BoardGeometry.warp(small, 65536, 65536, full(), 64));
        invalid(() -> BoardGeometry.warp(small, 64, 64, null, 64));
        int[] max = BoardGeometry.warp(new int[] {0xff123456}, 1, 1, full(), 2048);
        check(max.length == 2048 * 2048 && max[max.length - 1] == 0xff123456,
                "maximum output size supported");
        check(BoardGeometry.warp(small, 64, 64, full(), 72).length == 72 * 72,
                "non-power-of-two multiple of eight");
    }

    private static BoardGeometry.Point[] full() { return points(0, 0, 1, 0, 1, 1, 0, 1); }

    private static BoardGeometry.Point[] points(double... xy) {
        BoardGeometry.Point[] result = new BoardGeometry.Point[xy.length / 2];
        for (int i = 0; i < result.length; i++) result[i] = new BoardGeometry.Point(xy[2 * i], xy[2 * i + 1]);
        return result;
    }

    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void near(double actual, double expected) {
        check(Math.abs(actual - expected) < 1e-12, "expected " + expected + ", got " + actual);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
