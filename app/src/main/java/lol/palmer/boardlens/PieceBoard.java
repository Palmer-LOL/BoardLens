package lol.palmer.boardlens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.TextView;
import lol.palmer.boardlens.core.Position;

/** Real child views give each square its own TalkBack target. */
final class PieceBoard extends GridLayout {
    interface OnSquare { void selected(int square); }
    private final TextView[] cells = new TextView[64];
    private final Ui ui;
    PieceBoard(Context context, Position position, boolean[] review, boolean flipped, OnSquare listener) {
        super(context);
        ui = new Ui(context); setColumnCount(8); setRowCount(8);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        for (int visual = 0; visual < 64; visual++) {
            final int square = flipped ? 63 - visual : visual;
            char piece = position.pieceAt(square);
            TextView cell = new SquareCell(context, Character.isUpperCase(piece));
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            cell.setText(glyph(piece) + (review[square] ? "·" : ""));
            cell.setTextColor(Character.isUpperCase(piece) ? Ui.HEADING : Ui.BG);
            cell.setBackgroundColor((square / 8 + square % 8) % 2 == 0 ? Ui.MUTED : Ui.PANEL);
            cell.setForeground(new RippleDrawable(ColorStateList.valueOf(0x66FF4D00), null, new ColorDrawable(Ui.HEADING)));
            cell.setContentDescription(Position.squareName(square) + ", " + name(piece) +
                    (review[square] ? ", needs review" : "") + ". Double tap to edit.");
            cell.setFocusable(true); cell.setClickable(true);
            cell.setOnClickListener(v -> listener.selected(square));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(visual / 8, 1f), GridLayout.spec(visual % 8, 1f));
            params.width = 0; params.height = 0;
            addView(cell, params); cells[visual] = cell;
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        float sp = Math.max(18, (width / getResources().getDisplayMetrics().scaledDensity) / 12.8f);
        for (TextView cell : cells) cell.setTextSize(sp);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY));
    }

    /** Solid outlines keep either piece color visible on both square colors. */
    private final class SquareCell extends TextView {
        private final Paint piecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int outline;
        SquareCell(Context context, boolean white) {
            super(context);
            outline = white ? Ui.BG : Ui.TEXT;
            piecePaint.setTextAlign(Paint.Align.CENTER);
            piecePaint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            piecePaint.setTypeface(getTypeface());
            piecePaint.setTextSize(getTextSize());
            float baseline = getHeight() / 2f - (piecePaint.ascent() + piecePaint.descent()) / 2f;
            String label = getText().toString();
            piecePaint.setStyle(Paint.Style.STROKE);
            piecePaint.setStrokeWidth(ui.dp(1.5f));
            piecePaint.setColor(outline);
            canvas.drawText(label, getWidth() / 2f, baseline, piecePaint);
            piecePaint.setStyle(Paint.Style.FILL);
            piecePaint.setColor(getCurrentTextColor());
            canvas.drawText(label, getWidth() / 2f, baseline, piecePaint);
        }
    }

    static String glyph(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'k': return "♚"; case 'q': return "♛"; case 'r': return "♜";
            case 'b': return "♝"; case 'n': return "♞"; case 'p': return "♟";
            default: return "";
        }
    }
    static String name(char piece) {
        if (piece == '.') return "empty";
        String name;
        switch (Character.toLowerCase(piece)) {
            case 'k': name = "king"; break; case 'q': name = "queen"; break;
            case 'r': name = "rook"; break; case 'b': name = "bishop"; break;
            case 'n': name = "knight"; break; default: name = "pawn";
        }
        return (Character.isUpperCase(piece) ? "white " : "black ") + name;
    }
}
