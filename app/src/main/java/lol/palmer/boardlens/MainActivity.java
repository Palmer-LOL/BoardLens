package lol.palmer.boardlens;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lol.palmer.boardlens.core.AtomicStorage;
import lol.palmer.boardlens.core.BoardGeometry;
import lol.palmer.boardlens.core.Position;

public final class MainActivity extends Activity {
    private static final int CAMERA = 10, PICK_PHOTO = 11;
    private AppSession state;
    private Ui ui;
    private CornerView cornerView;
    private boolean flipped;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        state = ((BoardLensApp) getApplication()).session();
        ui = new Ui(this);
        flipped = saved != null && saved.getBoolean("flipped");
    }
    @Override protected void onStart() {
        super.onStart(); state.listener = this::render; render();
    }
    @Override protected void onStop() { state.listener = null; state.save(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("flipped", flipped); state.save(); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (!state.busy && state.page != AppSession.HOME) goHome(); else super.onBackPressed();
    }
    private void goHome() { state.page = AppSession.HOME; state.save(); render(); }

    private void render() {
        cornerView = null;
        LinearLayout shell = ui.column(); shell.setBackgroundColor(Ui.BG);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
                view.setPadding(left, top, right, bottom);
            }
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = ui.column(); content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(28));
        LinearLayout title = ui.row();
        TextView brand = ui.heading("BoardLens", 23);
        title.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = ui.text("ON DEVICE", 10, Ui.ACCENT); chip.setLetterSpacing(.12f); title.addView(chip);
        content.addView(title);
        if (state.page != AppSession.HOME) content.addView(ui.button("‹  Workspace", false, this::goHome));
        if (!state.message.isEmpty()) {
            TextView message = ui.notice(state.message, 14);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            content.addView(message);
        }
        if (!state.saveError.isEmpty()) {
            content.addView(ui.notice(state.saveError, 14));
            content.addView(ui.button("Retry saving changes", false, state::save));
        } else if (state.saving) content.addView(ui.text("Saving local changes…", 12, Ui.MUTED));
        if (state.busy) {
            ui.space(content, 32); content.addView(new ProgressBar(this));
            content.addView(ui.text("Processing locally. Your photo stays on this phone.", 15, Ui.MUTED));
        } else if (state.page == AppSession.ALIGN && state.photo != null) buildAlignment(content);
        else if (state.page == AppSession.REVIEW) buildReview(content);
        else buildHome(content);
        scroll.addView(content); shell.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        setContentView(shell); shell.requestApplyInsets();
    }

    private void buildHome(LinearLayout parent) {
        ui.space(parent, 18);
        parent.addView(ui.heading("From board\nto position.", 35));
        parent.addView(ui.text("Photograph a position. Review the pieces. Take the FEN with you.", 17, Ui.TEXT));
        ui.space(parent, 10);
        if (state.pendingCapture != null) {
            LinearLayout pending = ui.card();
            pending.addView(ui.heading("A captured photo is waiting", 19));
            pending.addView(ui.text("It has not been imported successfully. Retry it, or discard the temporary capture.", 14, Ui.MUTED));
            ui.pair(pending, ui.button("Retry photo", true, this::retryCapture),
                    ui.button("Discard", false, () -> {
                        if (state.captureInFlight) { toast("Finish or cancel the current camera capture first."); return; }
                        finishCapture(); render();
                    }));
            parent.addView(pending);
        }
        LinearLayout scan = ui.card();
        scan.addView(ui.heading("Scan a chessboard", 21));
        scan.addView(ui.text("Use a clear overhead photo with every square visible. Match your calibration lighting and camera angle.", 14, Ui.TEXT));
        Button camera = ui.button("Take a photo", true, () -> acquire(AppSession.SCAN, true));
        Button gallery = ui.button("Choose photo", false, () -> acquire(AppSession.SCAN, false));
        camera.setEnabled(state.calibrated); gallery.setEnabled(state.calibrated);
        ui.pair(scan, camera, gallery);
        if (!state.calibrated) scan.addView(ui.notice("Complete local calibration below to enable recognition.", 14));
        parent.addView(scan);

        LinearLayout calibration = ui.card();
        calibration.addView(ui.heading(state.calibrated ? "Your board is calibrated" : "Teach it your chess set", 20));
        calibration.addView(ui.text(state.referenceCount + " reference photo(s) · " + state.profileSummary, 13, Ui.MUTED));
        calibration.addView(ui.text("1. Photograph the standard starting position.\n2. Swap the king and queen for both colors, then take another photo.\nKeep the same overhead view. Verify the labels before saving each reference.", 14, Ui.TEXT));
        ui.pair(calibration,
                ui.button("1 · Starting setup", false, () -> chooseSource(AppSession.START_REFERENCE)),
                ui.button("2 · Swap K / Q", false, () -> chooseSource(AppSession.SWAPPED_REFERENCE)));
        calibration.addView(ui.text("Calibration is specific to your board, pieces and view. Add corrected scans as references if needed. Accuracy is experimental.", 13, Ui.MUTED));
        parent.addView(calibration);
        ui.pair(parent, ui.button("Edit position", false, () -> { state.page = AppSession.REVIEW; render(); }),
                ui.button("Import FEN", false, this::importFen));
        if (state.photo != null) parent.addView(ui.button("Align current photo", false, () -> {
            state.page = AppSession.ALIGN; state.save(); render();
        }));
        parent.addView(ui.button("Help & local data", false, this::showHelp));
    }

    private void chooseSource(int mode) {
        new AlertDialog.Builder(this).setTitle(mode == AppSession.START_REFERENCE ? "Starting-position reference" : "Swapped king / queen reference")
                .setMessage(mode == AppSession.START_REFERENCE
                        ? "Set up a normal starting position. Photograph the board from overhead with White nearest you. All four board edges must be visible."
                        : "From the starting position, exchange the white king and queen, then exchange the black king and queen. Keep all other pieces in place and use the same overhead view.")
                .setPositiveButton("Take photo", (d, w) -> acquire(mode, true))
                .setNeutralButton("Choose photo", (d, w) -> acquire(mode, false))
                .setNegativeButton("Cancel", null).show();
    }

    private void acquire(int mode, boolean camera) {
        if (state.busy) return;
        if (state.captureInFlight) { toast("Finish or cancel the current camera capture first."); return; }
        if (state.pendingCapture != null) {
            new AlertDialog.Builder(this).setTitle("Replace the waiting photo?")
                    .setMessage("A previous capture has not been imported. You can retry it from the workspace, or discard it and continue.")
                    .setPositiveButton("Discard and continue", (d, w) -> { finishCapture(); acquire(mode, camera); })
                    .setNegativeButton("Keep photo", null).show();
            return;
        }
        state.captureMode = mode;
        if (camera) {
            try {
                File directory = new File(getCacheDir(), "captures");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot prepare the camera file.");
                File output = new File(directory, UUID.randomUUID() + ".jpg");
                if (!output.createNewFile()) throw new IOException("Cannot prepare the camera file.");
                Uri uri = new Uri.Builder().scheme("content").authority(getPackageName() + ".capture").appendPath(output.getName()).build();
                state.pendingCapture = uri.toString();
                launchSavedCapture(uri);
            } catch (IOException | ActivityNotFoundException | SecurityException error) {
                finishCapture(); alert("Camera unavailable", "Try Choose photo, or check that a camera app is installed.");
            }
        } else {
            state.save();
            Intent intent = Build.VERSION.SDK_INT >= 33 ? new Intent(MediaStore.ACTION_PICK_IMAGES) : documentPicker();
            intent.setType("image/*");
            try { startActivityForResult(intent, PICK_PHOTO); }
            catch (ActivityNotFoundException error) {
                try { startActivityForResult(documentPicker(), PICK_PHOTO); }
                catch (ActivityNotFoundException missing) { alert("Picker unavailable", "No image picker is installed on this device."); }
            }
        }
    }

    private Intent documentPicker() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
    }

    private void retryCapture() {
        if (state.pendingCapture == null) return;
        if (state.captureInFlight) { toast("Finish or cancel the current camera capture first."); return; }
        Uri uri = Uri.parse(state.pendingCapture);
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-f0-9-]{36}\\.jpg")) { finishCapture(); return; }
        File captured = new File(new File(getCacheDir(), "captures"), name);
        if (!captured.isFile()) {
            finishCapture();
            state.message = "The temporary capture is no longer available. You can align the retained photo or take another.";
            render();
        } else if (captured.length() == 0) launchSavedCapture(uri);
        else state.loadPhoto(uri, captureCleanup());
    }

    private void launchSavedCapture(Uri uri) {
        if (state.captureInFlight) return;
        final AppSession session = state;
        session.captureInFlight = true;
        WeakReference<MainActivity> owner = new WeakReference<>(this);
        // Persist the URI and mode before the camera can background or outlive us.
        state.save(() -> {
            MainActivity activity = owner.get();
            if (activity == null || activity.isDestroyed() || activity.isFinishing() ||
                    !uri.toString().equals(activity.state.pendingCapture)) {
                session.captureInFlight = false;
                return;
            }
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.setClipData(ClipData.newRawUri("Board photo", uri));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { activity.startActivityForResult(intent, CAMERA); }
            catch (ActivityNotFoundException | SecurityException error) {
                session.captureInFlight = false;
                activity.finishCapture();
                activity.alert("Camera unavailable", "Try Choose photo, or check that a camera app is installed.");
            }
        }, () -> session.captureInFlight = false);
    }

    private Runnable captureCleanup() {
        final String pending = state.pendingCapture;
        final Context application = getApplicationContext();
        final AppSession session = state;
        return () -> {
            if (pending == null) return;
            Uri uri = Uri.parse(pending);
            application.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String filename = uri.getLastPathSegment();
            try {
                if (filename != null && filename.matches("[a-f0-9-]{36}\\.jpg"))
                    AtomicStorage.delete(new File(new File(application.getCacheDir(), "captures"), filename));
            } catch (IOException | SecurityException error) {
                session.message = "The temporary capture could not be removed. Retry or discard it from the workspace.";
                session.notifyChanged();
                return;
            }
            if (pending.equals(session.pendingCapture)) session.pendingCapture = null;
            session.save();
        };
    }
    private void finishCapture() { captureCleanup().run(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CAMERA) {
            state.captureInFlight = false;
            if (state.pendingCapture != null) getApplicationContext().revokeUriPermission(Uri.parse(state.pendingCapture),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (result == RESULT_OK && state.pendingCapture != null)
                state.loadPhoto(Uri.parse(state.pendingCapture), captureCleanup());
            else finishCapture();
        } else if (request == PICK_PHOTO && result == RESULT_OK && data != null && data.getData() != null)
            state.loadPhoto(data.getData(), null);
    }

    private void buildAlignment(LinearLayout parent) {
        parent.addView(ui.heading("Align the board", 28));
        parent.addView(ui.text("Drag each handle to an outer board corner. The labels must match the real squares: a1 is White’s left corner. Rotate labels if needed.", 15, Ui.MUTED));
        cornerView = new CornerView(this, state.photo, state.corners, () -> state.corners = cornerView.corners());
        parent.addView(cornerView, new LinearLayout.LayoutParams(-1, -2));
        ui.pair(parent,
                ui.button("Rotate labels ↻", false, () -> {
                    BoardGeometry.Point[] c = state.corners;
                    state.corners = new BoardGeometry.Point[]{c[3], c[0], c[1], c[2]};
                    cornerView.setCorners(state.corners); state.save();
                }),
                ui.button("Corner coordinates", false, this::editCorners));
        parent.addView(ui.text("The grid should follow the square boundaries. A slanted view can hide pieces even after the board is straightened.", 13, Ui.MUTED));
        parent.addView(ui.button(state.photoMode == AppSession.SCAN ? "Recognize position" : "Review reference labels", true, () -> {
            try { BoardGeometry.validate(state.corners); state.process(); }
            catch (IllegalArgumentException error) { alert("Check the corners", error.getMessage()); }
        }));
    }

    private void editCorners() {
        LinearLayout form = dialogForm();
        form.addView(ui.text("Percent of photo width / height, measured from its top left. Values 0–100.", 14, Ui.MUTED));
        String[] labels = {"a8", "h8", "h1", "a1"};
        EditText[] fields = new EditText[8];
        for (int i = 0; i < 4; i++) {
            fields[i * 2] = field(form, labels[i] + " · horizontal %", String.format(Locale.ROOT, "%.2f", state.corners[i].x * 100), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            fields[i * 2 + 1] = field(form, labels[i] + " · vertical %", String.format(Locale.ROOT, "%.2f", state.corners[i].y * 100), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Corner coordinates").setView(scrollForm(form))
                .setPositiveButton("Apply", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                BoardGeometry.Point[] corners = new BoardGeometry.Point[4];
                for (int i = 0; i < 4; i++) corners[i] = new BoardGeometry.Point(
                        Double.parseDouble(fields[i * 2].getText().toString()) / 100,
                        Double.parseDouble(fields[i * 2 + 1].getText().toString()) / 100);
                BoardGeometry.validate(corners); state.corners = corners; state.save(); dialog.dismiss(); render();
            } catch (IllegalArgumentException error) { toast("Enter four valid corners in board order."); }
        })); dialog.show();
    }

    private void buildReview(LinearLayout parent) {
        parent.addView(ui.heading(state.referenceLabels ? "Check the reference" : "Review the position", 28));
        int flags = 0; for (boolean flag : state.review) if (flag) flags++;
        parent.addView(flags == 0 ? ui.text("Tap a square to edit its piece.", 14, Ui.MUTED)
                : ui.notice(flags + " square(s) need review · Tap a square to confirm or correct it.", 14));
        TextView coordinates = ui.text(flipped ? "h   g   f   e   d   c   b   a     · Black’s view" : "a   b   c   d   e   f   g   h     · White’s view", 13, Ui.MUTED);
        coordinates.setGravity(Gravity.CENTER); parent.addView(coordinates);
        parent.addView(new PieceBoard(this, state.position, state.review, flipped, this::editSquare), new LinearLayout.LayoutParams(-1, -2));
        ui.pair(parent, ui.button("Flip view", false, () -> { flipped = !flipped; render(); }),
                ui.button("Edit by square name", false, this::editNamedSquare));
        if (state.photo != null) parent.addView(ui.button("Align current photo", false, () -> {
            state.page = AppSession.ALIGN; state.save(); render();
        }));
        if (state.paired && state.warped != null) {
            LinearLayout source = ui.card();
            source.addView(ui.text("PHOTO REFERENCE", 11, Ui.MUTED));
            ImageView image = new ImageView(this); image.setImageBitmap(state.warped);
            image.setContentDescription("Rectified source board photo, a8 at top left. Tap for a larger image.");
            image.setScaleType(ImageView.ScaleType.FIT_CENTER); source.addView(image, new LinearLayout.LayoutParams(-1, ui.dp(180)));
            image.setOnClickListener(v -> showPhoto()); image.setFocusable(true);
            source.addView(ui.button(state.referenceLabels ? "Save this reference" : "Learn from corrected photo", false, this::confirmReference));
            parent.addView(source);
        }
        LinearLayout fen = ui.card();
        fen.addView(ui.heading("FEN", 21));
        TextView output = ui.text(state.position.toFen(), 15, Ui.TEXT); output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true); fen.addView(output);
        fen.addView(state.historyConfirmed ? ui.text("History fields reviewed.", 13, Ui.MUTED)
                : ui.notice("History is unconfirmed: White to move, no castling / en passant, counters 0 / 1 are defaults. A photo cannot determine them.", 13));
        fen.addView(ui.button("Turn, castling & move history", false, () -> editMetadata(null)));
        List<String> issues = state.position.validationIssues();
        if (!issues.isEmpty()) fen.addView(ui.notice("Position checks:\n• " + String.join("\n• ", issues), 13));
        ui.pair(fen, ui.button("Copy FEN", true, () -> requestExport(false)),
                ui.button("Share FEN", false, () -> requestExport(true)));
        parent.addView(fen);
        ui.pair(parent, ui.button("Import FEN", false, this::importFen), ui.button("New empty board", false, () ->
                new AlertDialog.Builder(this).setTitle("Clear the position?").setMessage("Replace the current position with an empty editable board?")
                        .setPositiveButton("Clear", (d, w) -> state.replacePosition(Position.empty())).setNegativeButton("Cancel", null).show()));
    }

    private void showPhoto() {
        ImageView image = new ImageView(this); image.setImageBitmap(state.warped);
        image.setAdjustViewBounds(true); image.setContentDescription("Source photo: a8 top left, h1 bottom right.");
        new AlertDialog.Builder(this).setTitle("Photo · a8 at top left").setView(image).setPositiveButton("Done", null).show();
    }

    private void editSquare(int square) {
        char[] pieces = ".PNBRQKpnbrqk".toCharArray();
        String[] names = new String[pieces.length]; int selected = 0;
        for (int i = 0; i < pieces.length; i++) {
            names[i] = PieceBoard.name(pieces[i]); if (pieces[i] == state.position.pieceAt(square)) selected = i;
        }
        final int[] choice = {selected};
        new AlertDialog.Builder(this).setTitle("Square " + Position.squareName(square))
                .setSingleChoiceItems(names, selected, (dialog, which) -> choice[0] = which)
                .setPositiveButton("Confirm square", (d, w) -> {
                    state.position.setPiece(square, pieces[choice[0]]); state.review[square] = false;
                    state.save(); render();
                }).setNegativeButton("Cancel", null).show();
    }

    private void editNamedSquare() {
        LinearLayout form = dialogForm(); EditText square = field(form, "Square (a1–h8)", "", InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit a square").setView(form)
                .setPositiveButton("Choose piece", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = square.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-h][1-8]")) { square.setError("Use a square such as e4."); return; }
            dialog.dismiss(); editSquare((8 - (name.charAt(1) - '0')) * 8 + name.charAt(0) - 'a');
        })); dialog.show();
    }

    private void confirmReference() {
        new AlertDialog.Builder(this).setTitle("Save labeled photo?")
                .setMessage("Every square label must match the photo, including empty squares. Incorrect labels teach incorrect matches. Up to 20 reference photos can be retained.")
                .setPositiveButton("Save reference", (d, w) -> state.addReference()).setNegativeButton("Keep reviewing", null).show();
    }

    private void importFen() {
        LinearLayout form = dialogForm(); EditText input = field(form, "Six-field FEN", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3); input.setTypeface(Typeface.MONOSPACE);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Import FEN").setView(form)
                .setPositiveButton("Import", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { Position parsed = Position.parse(input.getText().toString().trim()); state.replacePosition(parsed); state.historyConfirmed = true; state.save(); dialog.dismiss(); render(); }
            catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
        })); dialog.show();
    }

    private void editMetadata(Runnable afterSave) {
        LinearLayout form = dialogForm();
        form.addView(ui.text("A photograph shows placement, not move history. Set what you know. Leaving defaults is a deliberate assumption.", 14, Ui.MUTED));
        form.addView(ui.text("Side to move", 14, Ui.TEXT));
        Spinner side = new Spinner(this);
        side.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"White", "Black"}));
        side.setSelection(state.position.activeColor == 'w' ? 0 : 1); side.setContentDescription("Side to move"); form.addView(side);
        form.addView(ui.text("Castling rights (only if king and rook have never moved)", 14, Ui.TEXT));
        String[] castlingLabels = {"White kingside", "White queenside", "Black kingside", "Black queenside"};
        String rights = "KQkq"; CheckBox[] castle = new CheckBox[4];
        for (int i = 0; i < 4; i++) {
            castle[i] = new CheckBox(this); castle[i].setText(castlingLabels[i]);
            castle[i].setChecked(state.position.castling.indexOf(rights.charAt(i)) >= 0); form.addView(castle[i]);
        }
        EditText ep = field(form, "En passant target (– or square)", state.position.enPassant, InputType.TYPE_CLASS_TEXT);
        EditText half = field(form, "Halfmoves since pawn move or capture", Integer.toString(state.position.halfmove), InputType.TYPE_CLASS_NUMBER);
        EditText full = field(form, "Fullmove number (starts at 1)", Integer.toString(state.position.fullmove), InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Position history").setView(scrollForm(form))
                .setPositiveButton("Use these fields", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            StringBuilder selected = new StringBuilder();
            for (int i = 0; i < 4; i++) if (castle[i].isChecked()) selected.append(rights.charAt(i));
            String target = ep.getText().toString().trim().toLowerCase(Locale.ROOT).replace('–', '-');
            if (target.isEmpty()) target = "-";
            String candidate = state.position.placement() + (side.getSelectedItemPosition() == 0 ? " w " : " b ") +
                    (selected.length() == 0 ? "-" : selected) + " " + target + " " + half.getText() + " " + full.getText();
            try {
                state.position = Position.parse(candidate); state.historyConfirmed = true; state.save();
                dialog.dismiss(); render(); if (afterSave != null) afterSave.run();
            } catch (IllegalArgumentException error) { toast(error.getMessage()); }
        })); dialog.show();
    }

    private void requestExport(boolean share) {
        if (!state.historyConfirmed) { editMetadata(() -> requestExport(share)); return; }
        int flags = 0; for (boolean flag : state.review) if (flag) flags++;
        List<String> issues = state.position.validationIssues();
        if (!issues.isEmpty() || flags > 0) {
            new AlertDialog.Builder(this).setTitle("Export with review items?")
                    .setMessage(flags + " square(s) are still marked for review.\n" + String.join("\n", issues) + "\n\nThe FEN will preserve the position exactly as shown.")
                    .setPositiveButton("Export as shown", (d, w) -> exportFen(share)).setNegativeButton("Review", null).show();
        } else exportFen(share);
    }

    private void exportFen(boolean share) {
        String fen = state.position.toFen();
        if (share) {
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fen);
            try { startActivity(Intent.createChooser(intent, "Share FEN")); }
            catch (ActivityNotFoundException error) { toast("No app is available to share text."); }
        } else {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Chess position FEN", fen)); toast("FEN copied");
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Local, by design")
                .setMessage("BoardLens has no Internet permission, accounts or analytics. The installed camera app handles capture; the system picker handles imports.\n\nOnly the current photo, its rectified board, the position and calibration descriptors are retained in app-private storage. Photo metadata is stripped. Cloud backup and app data transfer are disabled.\n\nRecognition uses calibrated appearance matching. It is experimental: changed lighting, piece rotation, a low camera angle or occlusion can cause mistakes. Dots indicate heuristic review flags, not statistical confidence. Check every square.\n\nClear photos or calibration below. Android’s Clear storage removes all app data. Clipboard and Share send FEN only when you choose them.")
                .setPositiveButton("Done", null)
                .setNeutralButton("Remove photo", (d, w) -> state.clearPhoto())
                .setNegativeButton("Reset calibration", (d, w) -> new AlertDialog.Builder(this)
                        .setTitle("Reset calibration?").setMessage("Delete all learned reference descriptors? You will need to calibrate again.")
                        .setPositiveButton("Reset", (a, b) -> state.clearCalibration()).setNegativeButton("Cancel", null).show()).show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = ui.column(); form.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(12)); return form;
    }
    private ScrollView scrollForm(LinearLayout form) { ScrollView scroll = new ScrollView(this); scroll.addView(form); return scroll; }
    private EditText field(LinearLayout form, String label, String value, int type) {
        TextView title = ui.text(label, 14, Ui.TEXT); form.addView(title);
        EditText input = new EditText(this); input.setId(View.generateViewId()); title.setLabelFor(input.getId());
        input.setInputType(type); input.setText(value); input.setTextSize(16); input.setMinHeight(ui.dp(48));
        input.setSelectAllOnFocus(true); form.addView(input); return input;
    }
    private void alert(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
