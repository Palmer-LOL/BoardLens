package lol.palmer.boardlens.core;

/**
 * Projective rectification of a board's OUTER corners, ordered a8, h8, h1, a1.
 * Coordinates span the image's outer edges: (0,0) is the top-left edge and (1,1)
 * the bottom-right edge. Pixel (x,y) has center ((x+.5)/width,(y+.5)/height).
 * The output follows the supplied labels, even when the camera image is rotated.
 *
 * <p>Use a clear overhead photo. A homography rectifies the board plane; it cannot
 * remove piece parallax, a low viewing angle, or occlusion of one piece by another.
 */
public final class BoardGeometry {
    private static final int MAX_IMAGE_SIDE = 16_384;
    private static final long MAX_IMAGE_PIXELS = 32_000_000;
    private static final double MIN_EDGE = 1e-4;
    private static final double MIN_AREA = 1e-4;

    private BoardGeometry() { }

    /** Immutable normalized image coordinate; {@link #validate} checks its range. */
    public static final class Point {
        public final double x;
        public final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /** Returns a fresh a8, h8, h1, a1 array inset by ten percent on every side. */
    public static Point[] defaults() {
        return new Point[] {new Point(.1, .1), new Point(.9, .1),
                new Point(.9, .9), new Point(.1, .9)};
    }

    /**
     * Requires four finite, in-range, strictly convex corners in either winding.
     * Rejects repeated/crossed corners, area below 0.0001 of the image, edges below
     * 0.0001 of its normalized extent, and nearly collinear adjacent edges.
     *
     * @throws IllegalArgumentException if the quadrilateral is invalid or unstable
     */
    public static void validate(Point[] corners) {
        if (corners == null || corners.length != 4) {
            throw new IllegalArgumentException("Exactly four outer board corners are required");
        }
        for (Point p : corners) {
            if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y)
                    || p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1) {
                throw new IllegalArgumentException("Corner coordinates must be finite and within [0,1]");
            }
        }
        double winding = 0;
        double twiceArea = 0;
        for (int i = 0; i < 4; i++) {
            Point a = corners[i], b = corners[(i + 1) % 4], c = corners[(i + 2) % 4];
            double dx = b.x - a.x, dy = b.y - a.y;
            double ex = c.x - b.x, ey = c.y - b.y;
            double length = Math.hypot(dx, dy), nextLength = Math.hypot(ex, ey);
            double cross = dx * ey - dy * ex;
            // All four strict turns must agree. For a quadrilateral this also
            // excludes self-intersection; either camera-space winding is fine.
            if (length < MIN_EDGE || nextLength < MIN_EDGE
                    || Math.abs(cross) < 1e-6
                    || Math.abs(cross) / (length * nextLength) < 1e-3
                    || (i > 0 && cross * winding <= 0)) {
                throw new IllegalArgumentException("Board corners must form a nondegenerate convex quadrilateral");
            }
            winding = cross;
            twiceArea += a.x * b.y - a.y * b.x;
        }
        if (Math.abs(twiceArea) < 2 * MIN_AREA) {
            throw new IllegalArgumentException("Board quadrilateral is too small");
        }
    }

    /**
     * Maps output pixel centers through a real homography and interpolates RGB
     * bilinearly. Samples reaching the image boundary clamp to its nearest pixel;
     * alpha is ignored and output is opaque ARGB. Inputs are never modified.
     *
     * @param size output side, 64..2048 inclusive and divisible by eight
     * @throws IllegalArgumentException for invalid corners, dimensions, or length;
     *         source sides must be 1..16384 and contain at most 32 million pixels
     */
    public static int[] warp(int[] argb, int width, int height, Point[] corners, int size) {
        if (width < 1 || height < 1 || width > MAX_IMAGE_SIDE || height > MAX_IMAGE_SIDE
                || (long) width * height > MAX_IMAGE_PIXELS
                || argb == null || argb.length != (long) width * height) {
            throw new IllegalArgumentException("Invalid source raster dimensions or pixel count");
        }
        if (size < 64 || size > 2048 || size % 8 != 0) {
            throw new IllegalArgumentException("Output side must be 64..2048 and divisible by eight");
        }
        Point[] p = corners == null ? null : corners.clone();
        validate(p);
        double[] h = homography(p);
        int[] result = new int[size * size];
        for (int row = 0; row < size; row++) {
            double v = (row + .5) / size;
            for (int col = 0; col < size; col++) {
                double u = (col + .5) / size;
                double denominator = h[6] * u + h[7] * v + 1;
                double x = (h[0] * u + h[1] * v + h[2]) / denominator;
                double y = (h[3] * u + h[4] * v + h[5]) / denominator;
                result[row * size + col] = bilinear(argb, width, height,
                        x * width - .5, y * height - .5);
            }
        }
        return result;
    }

    /** Unit square to quadrilateral, with the last matrix element fixed to one. */
    private static double[] homography(Point[] p) {
        double dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x;
        double dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y;
        double dx3 = p[0].x - p[1].x + p[2].x - p[3].x;
        double dy3 = p[0].y - p[1].y + p[2].y - p[3].y;
        double determinant = dx1 * dy2 - dx2 * dy1;
        double g = (dx3 * dy2 - dx2 * dy3) / determinant;
        double h = (dx1 * dy3 - dx3 * dy1) / determinant;
        // A convex finite quadrilateral keeps this affine denominator positive
        // throughout the unit square. Check its extrema at the four vertices.
        if (!Double.isFinite(g) || !Double.isFinite(h)
                || Math.min(Math.min(1 + g, 1 + h), 1 + g + h) <= 1e-8) {
            throw new IllegalArgumentException("Unstable board homography");
        }
        return new double[] {
                p[1].x - p[0].x + g * p[1].x, p[3].x - p[0].x + h * p[3].x, p[0].x,
                p[1].y - p[0].y + g * p[1].y, p[3].y - p[0].y + h * p[3].y, p[0].y,
                g, h};
    }

    private static int bilinear(int[] pixels, int width, int height, double x, double y) {
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(x0 + 1, width - 1), y1 = Math.min(y0 + 1, height - 1);
        double fx = x - x0, fy = y - y0;
        int a = pixels[y0 * width + x0], b = pixels[y0 * width + x1];
        int c = pixels[y1 * width + x0], d = pixels[y1 * width + x1];
        int color = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            double top = ((a >>> shift) & 255) * (1 - fx) + ((b >>> shift) & 255) * fx;
            double bottom = ((c >>> shift) & 255) * (1 - fx) + ((d >>> shift) & 255) * fx;
            color |= (int) Math.round(top * (1 - fy) + bottom * fy) << shift;
        }
        return color;
    }
}
