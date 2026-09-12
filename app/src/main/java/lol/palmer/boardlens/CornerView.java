package lol.palmer.boardlens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import lol.palmer.boardlens.core.BoardGeometry;

final class CornerView extends View {
    private final Bitmap photo;
    private BoardGeometry.Point[] corners;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF frame = new RectF();
    private final float density;
    private int dragging = -1;
    private final Runnable changed;
    private static final String[] LABELS = {"a8", "h8", "h1", "a1"};

    CornerView(Context context, Bitmap photo, BoardGeometry.Point[] corners, Runnable changed) {
        super(context);
        this.photo = photo;
        this.corners = corners.clone();
        this.changed = changed;
        density = getResources().getDisplayMetrics().density;
        setContentDescription("Board corner alignment. Drag labeled corners to the outside corners of the board. Numeric corner controls are available below.");
        setFocusable(true);
    }

    BoardGeometry.Point[] corners() { return corners.clone(); }
    void setCorners(BoardGeometry.Point[] value) { corners = value.clone(); invalidate(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, (int) Math.min(width * 1.18, Math.max(width * .7, width * (double) photo.getHeight() / photo.getWidth())));
    }

    private float px(int i) { return frame.left + (float) corners[i].x * frame.width(); }
    private float py(int i) { return frame.top + (float) corners[i].y * frame.height(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = 20 * density;
        float scale = Math.min((getWidth() - inset * 2) / photo.getWidth(), (getHeight() - inset * 2) / photo.getHeight());
        float w = photo.getWidth() * scale, h = photo.getHeight() * scale;
        frame.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(photo, null, frame, paint);
        float[] target = new float[8];
        for (int i = 0; i < 4; i++) { target[i * 2] = px(i); target[i * 2 + 1] = py(i); }
        Matrix projection = new Matrix();
        if (projection.setPolyToPoly(new float[]{0, 0, 8, 0, 8, 8, 0, 8}, 0, target, 0, 4)) {
            paint.setColor(Ui.ACCENT);
            paint.setStrokeWidth(density);
            for (int i = 0; i <= 8; i++) {
                float[] line = {i, 0, i, 8, 0, i, 8, i};
                projection.mapPoints(line);
                canvas.drawLine(line[0], line[1], line[2], line[3], paint);
                canvas.drawLine(line[4], line[5], line[6], line[7], paint);
            }
        }
        for (int i = 0; i < 4; i++) {
            paint.setColor(Ui.BG);
            canvas.drawCircle(px(i), py(i), 17 * density, paint);
            paint.setColor(i == 3 ? Ui.ACCENT : Ui.HEADING);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * density);
            canvas.drawCircle(px(i), py(i), 17 * density, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(13 * density); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(LABELS[i], px(i), py(i) + 4.5f * density, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float best = 38 * density;
            dragging = -1;
            for (int i = 0; i < 4; i++) {
                float distance = (float) Math.hypot(event.getX() - px(i), event.getY() - py(i));
                if (distance < best) { best = distance; dragging = i; }
            }
            if (dragging < 0) return false;
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (dragging < 0) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            double x = Math.max(0, Math.min(1, (event.getX() - frame.left) / frame.width()));
            double y = Math.max(0, Math.min(1, (event.getY() - frame.top) / frame.height()));
            corners[dragging] = new BoardGeometry.Point(x, y);
            invalidate();
            changed.run();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = -1;
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}
