package lol.palmer.boardlens;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    // Keep these roles in sync with res/values/colors.xml for platform widgets.
    static final int BG = 0xFF121212, PANEL = 0xFF303030, TEXT = 0xFFFFE5D1,
            MUTED = 0xFFB9A898, HEADING = 0xFFFFFFFF, ACCENT = 0xFFFF4D00;
    private final Context context;
    Ui(Context context) { this.context = context; }
    int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    TextView text(String value, int sp, int color) {
        TextView text = new TextView(context); text.setText(value); text.setTextColor(color);
        text.setTextSize(sp); text.setPadding(0, dp(5), 0, dp(5));
        text.setLineSpacing(dp(2), 1.05f);
        return text;
    }
    TextView heading(String value, int sp) {
        TextView view = text(value, sp, HEADING); view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    LinearLayout card() {
        LinearLayout view = column(); view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setBackground(background(PANEL, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8)); view.setLayoutParams(params);
        return view;
    }
    GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color);
        drawable.setCornerRadius(dp(radius)); return drawable;
    }
    Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(context); button.setText(label); button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{MUTED, primary ? BG : TEXT}));
        StateListDrawable surfaces = new StateListDrawable();
        surfaces.addState(new int[]{-android.R.attr.state_enabled}, background(PANEL, 12));
        GradientDrawable focused = background(primary ? ACCENT : BG, 12);
        focused.setStroke(dp(2), primary ? HEADING : ACCENT);
        surfaces.addState(new int[]{android.R.attr.state_focused}, focused);
        GradientDrawable normal = background(primary ? ACCENT : BG, 12);
        normal.setStroke(dp(1), primary ? ACCENT : PANEL);
        surfaces.addState(new int[]{}, normal);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x33121212 : 0x33FF4D00), surfaces, null));
        button.setMinHeight(dp(50)); button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4)); button.setLayoutParams(params);
        return button;
    }
    TextView notice(String value, int sp) {
        TextView view = text(value, sp, TEXT);
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_notice, 0, 0, 0);
        view.setCompoundDrawablePadding(dp(8));
        return view;
    }
    void pair(LinearLayout container, Button first, Button second) {
        LinearLayout row = row();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1); a.setMarginEnd(dp(4));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 1); b.setMarginStart(dp(4));
        row.addView(first, a); row.addView(second, b); container.addView(row);
    }
    void space(LinearLayout parent, int height) {
        View spacer = new View(context); parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }
}
