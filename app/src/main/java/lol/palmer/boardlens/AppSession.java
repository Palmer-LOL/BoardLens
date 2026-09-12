package lol.palmer.boardlens;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lol.palmer.boardlens.core.AtomicStorage;
import lol.palmer.boardlens.core.BoardGeometry;
import lol.palmer.boardlens.core.Position;
import lol.palmer.boardlens.core.SquareClassifier;

/** Process-scoped jobs never retain an Activity; all UI state changes happen on the main thread. */
final class AppSession {
    static final int HOME = 0, ALIGN = 1, REVIEW = 2;
    static final int SCAN = 0, START_REFERENCE = 1, SWAPPED_REFERENCE = 2;
    static final int BOARD_SIZE = 640;
    final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private SquareClassifier classifier = new SquareClassifier();
    private PhotoAttempt retryPhoto;
    private int pendingSaves;
    Position position = Position.empty();
    BoardGeometry.Point[] corners = BoardGeometry.defaults();
    Bitmap photo, warped;
    boolean[] review = new boolean[64];
    int page = HOME, captureMode = SCAN, photoMode = SCAN;
    boolean busy, calibrated, paired, referenceLabels, historyConfirmed;
    boolean initialized, saving;
    boolean captureInFlight;
    String saveError = "";
    int referenceCount;
    String profileSummary = "No references yet";
    String pendingCapture;
    private String photoFileName;
    String message = "";
    Runnable listener;

    AppSession(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("session", Context.MODE_PRIVATE);
        // Snapshot every preference before callbacks/lifecycle saves can change them.
        // Capture bookkeeping is restored now and is never reapplied by the async callback.
        try {
            Map<String, ?> saved = new HashMap<>(preferences.getAll());
            pendingCapture = (String) saved.get("pendingCapture");
            captureMode = restored(saved, "captureMode", SCAN);
            photoFileName = restored(saved, "photoFileName", "");
            if (!photoFileName.matches("photo-[0-2]-[a-f0-9-]{36}\\.jpg")) photoFileName = null;
            photoMode = photoFileName == null ? SCAN : photoFileName.charAt(6) - '0';
            position = Position.parse(restored(saved, "fen", Position.empty().toFen()));
            historyConfirmed = restored(saved, "historyConfirmed", false);
            paired = restored(saved, "paired", false);
            referenceLabels = restored(saved, "referenceLabels", false);
            String flags = restored(saved, "review", "");
            for (int i = 0; i < 64; i++) review[i] = flags.length() != 64 || flags.charAt(i) == '1';
            page = restored(saved, "page", HOME);
            for (int i = 0; i < 4; i++) {
                corners[i] = new BoardGeometry.Point(restored(saved, "x" + i, (float) corners[i].x),
                        restored(saved, "y" + i, (float) corners[i].y));
            }
        } catch (RuntimeException error) {
            paired = false;
            message = "Some of the previous workspace could not be restored. Your changes can still be saved.";
        }
        try { BoardGeometry.validate(corners); }
        catch (IllegalArgumentException ignored) { corners = BoardGeometry.defaults(); }
        final boolean savedPair = paired;
        final String restoreWarning = message;
        run("Loading local workspace…", () -> {
            String warning = restoreWarning;
            File profile = file("calibration.bin");
            if (profile.isFile()) {
                try (InputStream in = new FileInputStream(profile)) { classifier = SquareClassifier.read(in); }
                catch (IOException | IllegalArgumentException ex) {
                    warning = "Saved calibration could not be read. Use Reset calibration to rebuild it.";
                }
            }
            Bitmap loadedPhoto = photoFileName == null ? null : BitmapFactory.decodeFile(file(photoFileName).getPath());
            Bitmap loadedBoard = BitmapFactory.decodeFile(file("board.png").getPath());
            final String resultWarning = warning;
            return () -> {
                photo = loadedPhoto; warped = loadedBoard;
                paired = loadedBoard != null && savedPair;
                if (page < HOME || page > REVIEW || (page == ALIGN && photo == null)) page = HOME;
                updateProfileSummary();
                message = resultWarning;
            };
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T restored(Map<String, ?> saved, String key, T fallback) {
        Object value = saved.get(key);
        return fallback.getClass().isInstance(value) ? (T) value : fallback;
    }

    File file(String name) { return new File(context.getFilesDir(), name); }

    private interface Work { Runnable perform() throws Exception; }
    private void run(String label, Work work) {
        // Camera/picker results may arrive during restoration after process death.
        if (busy) { pending.addLast(() -> run(label, work)); return; }
        busy = true; message = label; notifyChanged();
        worker.execute(() -> {
            try {
                Runnable commit = work.perform();
                main.post(() -> finishJob(commit, null));
            } catch (Exception | OutOfMemoryError error) {
                main.post(() -> finishJob(null, error));
            }
        });
    }

    private void finishJob(Runnable commit, Throwable error) {
        if (error == null) {
            try { commit.run(); }
            catch (RuntimeException | OutOfMemoryError failure) { error = failure; }
        }
        // A failed restore or acceptance callback must also release the initialization gate.
        if (!initialized && error != null) { paired = false; page = HOME; }
        initialized = true;
        busy = false;
        if (error == null) save();
        else message = error instanceof OutOfMemoryError ? "The image is too large. Try a smaller photo."
                : error.getMessage() == null ? "That operation failed. Please try again." : error.getMessage();
        notifyChanged(); nextJob();
    }

    private void nextJob() { if (!pending.isEmpty()) pending.removeFirst().run(); }

    void notifyChanged() { if (listener != null) listener.run(); }

    void loadPhoto(Uri uri, Runnable afterRead) {
        final int mode = captureMode;
        run("Preparing photo on this device…", () -> {
            // Retain decoded work after a failed save, even if a picker grant later expires.
            if (retryPhoto == null || !uri.equals(retryPhoto.uri))
                retryPhoto = new PhotoAttempt(uri, PhotoIO.importPhoto(context, uri), mode);
            PhotoAttempt attempt = retryPhoto;
            String acceptedName = "photo-" + attempt.mode + "-" + UUID.randomUUID() + ".jpg";
            PhotoIO.save(attempt.bitmap, file(acceptedName), false);
            // Publish the image pointer and its corner state together. The old image
            // stays intact until this checked metadata commit succeeds. The purpose
            // is also encoded in the filename, so it cannot be paired with another photo.
            SharedPreferences.Editor accepted = preferences.edit().putString("photoFileName", acceptedName)
                    .putInt("photoMode", attempt.mode);
            BoardGeometry.Point[] defaults = BoardGeometry.defaults();
            for (int i = 0; i < 4; i++) {
                accepted.putFloat("x" + i, (float) defaults[i].x).putFloat("y" + i, (float) defaults[i].y);
            }
            if (!accepted.commit()) throw new IOException("The photo could not be registered. It is retained for retry.");
            retryPhoto = null;
            return () -> {
                photo = attempt.bitmap;
                photoFileName = acceptedName;
                corners = BoardGeometry.defaults();
                photoMode = attempt.mode;
                page = ALIGN;
                message = "Match the four labels to the outer board corners.";
                // Source deletion is permitted only after durable replacement and acceptance.
                if (afterRead != null) afterRead.run();
                worker.execute(() -> {
                    // Obsolete accepted images are removed after the new pointer is durable.
                    // Interrupted or failed cleanup can be completed with Remove photo.
                    File[] old = context.getFilesDir().listFiles((d, name) ->
                            name.matches("photo-[0-2]-[a-f0-9-]{36}\\.jpg") && !name.equals(acceptedName));
                    if (old != null) for (File candidate : old) {
                        try { AtomicStorage.delete(candidate); } catch (IOException ignored) { }
                    }
                });
            };
        });
    }

    private static final class PhotoAttempt {
        final Uri uri;
        final Bitmap bitmap;
        final int mode;
        PhotoAttempt(Uri uri, Bitmap bitmap, int mode) {
            this.uri = uri; this.bitmap = bitmap; this.mode = mode;
        }
    }

    void process() {
        if (photo == null) return;
        if (photoMode == SCAN && !calibrated) {
            message = "Complete local calibration before recognizing this photo. Your position has been retained.";
            notifyChanged();
            return;
        }
        final Bitmap input = photo;
        final BoardGeometry.Point[] selected = corners.clone();
        final int mode = photoMode;
        paired = false;
        run(mode == SCAN ? "Recognizing 64 squares on this device…" : "Preparing labeled reference…", () -> {
            BoardGeometry.validate(selected);
            int[] pixels = BoardGeometry.warp(PhotoIO.pixels(input), input.getWidth(), input.getHeight(), selected, BOARD_SIZE);
            Bitmap boardImage = Bitmap.createBitmap(pixels, BOARD_SIZE, BOARD_SIZE, Bitmap.Config.ARGB_8888);
            Position detected;
            boolean[] flags;
            if (mode == SCAN) {
                SquareClassifier.Result result = classifier.recognize(pixels, BOARD_SIZE);
                detected = Position.empty();
                for (int i = 0; i < 64; i++) detected.setPiece(i, result.pieces[i]);
                flags = result.review;
            } else {
                detected = Position.fromPlacement(mode == START_REFERENCE
                        ? "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
                        : "rnbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBKQBNR");
                flags = new boolean[64];
            }
            // Invalidate the on-disk image/label association BEFORE changing its image.
            // A process kill between files must never train a new photo using stale labels.
            if (!preferences.edit().putBoolean("paired", false).commit())
                throw new IOException("Could not prepare the saved position. Please try again.");
            PhotoIO.save(boardImage, file("board.png"), true);
            final Position resultPosition = detected;
            return () -> {
                warped = boardImage; position = resultPosition; review = flags;
                paired = true; referenceLabels = mode != SCAN; historyConfirmed = false;
                page = REVIEW;
                message = mode == SCAN ? "Review every square, especially those marked with a dot."
                        : "These are reference labels, not a prediction. Check they match your photograph before saving.";
            };
        });
    }

    void addReference() {
        if (!paired || warped == null) return;
        final Bitmap image = warped;
        final char[] labels = position.pieces();
        run("Saving reference on this device…", () -> {
            // Stage on a copy so a failed disk write cannot leave memory ahead of the saved model.
            java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
            classifier.write(data);
            SquareClassifier next = SquareClassifier.read(new java.io.ByteArrayInputStream(data.toByteArray()));
            next.addExample(PhotoIO.pixels(image), BOARD_SIZE, labels);
            AtomicStorage.write(file("calibration.bin"), next::write);
            classifier = next;
            return () -> {
                updateProfileSummary(); referenceLabels = false;
                message = calibrated ? "Reference saved. You can now scan a new position."
                        : "Reference saved. Add the kings-and-queens-swapped photo to complete calibration.";
                page = HOME;
            };
        });
    }

    void clearCalibration() {
        run("Clearing calibration…", () -> {
            AtomicStorage.delete(file("calibration.bin"));
            classifier = new SquareClassifier();
            return () -> { updateProfileSummary(); message = "Calibration cleared."; };
        });
    }

    void clearPhoto() {
        if (busy) return;
        paired = false;
        final String capture = pendingCapture;
        run("Removing photo…", () -> {
            if (!preferences.edit().putBoolean("paired", false).commit())
                throw new IOException("Could not prepare photo removal. Please try again.");
            AtomicStorage.delete(file("photo.jpg"));
            File[] acceptedPhotos = context.getFilesDir().listFiles((d, name) ->
                    (name.startsWith("photo-") && name.endsWith(".jpg")) ||
                            ((name.startsWith(".photo") || name.startsWith(".board.png-")) && name.endsWith(".tmp")));
            if (acceptedPhotos == null) throw new IOException("Could not read saved photos. Please try again.");
            for (File acceptedPhoto : acceptedPhotos) AtomicStorage.delete(acceptedPhoto);
            AtomicStorage.delete(file("board.png"));
            removePhotoCaches(capture);
            retryPhoto = null;
            return () -> {
                photo = null; warped = null; paired = false; referenceLabels = false;
                photoFileName = null;
                if (capture != null && capture.equals(pendingCapture)) pendingCapture = null;
                page = HOME; message = "Photo removed; the editable position is retained.";
            };
        });
    }

    private void removePhotoCaches(String capture) throws IOException {
        File cache = context.getCacheDir();
        File[] imports = cache.listFiles((directory, name) -> name.startsWith("import-") && name.endsWith(".image"));
        if (imports == null) throw new IOException("Could not read the photo cache. Please try again.");
        for (File imported : imports) AtomicStorage.delete(imported);
        File directory = new File(cache, "captures");
        if (!directory.exists()) return;
        File[] captures = directory.listFiles((parent, name) -> name.matches("[a-f0-9-]{36}\\.jpg"));
        if (captures == null) throw new IOException("Could not read the capture cache. Please try again.");
        String pendingName = capture == null ? null : Uri.parse(capture).getLastPathSegment();
        File pendingFile = null;
        for (File captured : captures) {
            if (captured.getName().equals(pendingName)) pendingFile = captured;
            else AtomicStorage.delete(captured);
        }
        // Delete the recoverable source last; earlier failures leave it available for retry/discard.
        if (pendingFile != null) AtomicStorage.delete(pendingFile);
    }

    void replacePosition(Position value) {
        if (busy) return;
        position = value; review = new boolean[64]; paired = false; referenceLabels = false;
        historyConfirmed = false; page = REVIEW; message = ""; save(); notifyChanged();
    }

    private void updateProfileSummary() {
        calibrated = classifier.ready(); referenceCount = classifier.exampleCount();
        profileSummary = classifier.coverageSummary();
    }

    void save() { save(null, null); }

    void save(Runnable afterSaved) { save(afterSaved, null); }

    void save(Runnable afterSaved, Runnable afterFailure) {
        Map<String, Object> values = new HashMap<>();
        values.put("captureMode", captureMode);
        values.put("pendingCapture", pendingCapture);
        if (initialized) {
            values.put("fen", position.toFen()); values.put("page", page);
            // Busy-time snapshots may have been taken before a new photo was accepted.
            // Do not let such queued saves overwrite its durable pointer or reset corners.
            if (!busy) {
                values.put("photoFileName", photoFileName);
                values.put("photoMode", photoMode);
                for (int i = 0; i < 4; i++) {
                    values.put("x" + i, (float) corners[i].x); values.put("y" + i, (float) corners[i].y);
                }
            }
            // A lifecycle snapshot taken during image replacement must never reinstate old labels.
            values.put("paired", paired && !busy);
            values.put("referenceLabels", referenceLabels);
            values.put("historyConfirmed", historyConfirmed);
            StringBuilder flags = new StringBuilder();
            for (boolean flag : review) flags.append(flag ? '1' : '0');
            values.put("review", flags.toString());
        }
        final Map<String, Object> snapshot = Collections.unmodifiableMap(values);
        pendingSaves++;
        boolean wasSaving = saving;
        saving = true;
        // Use the same worker as image/model jobs so snapshots and invalidation commits stay ordered.
        worker.execute(() -> {
            String error = "";
            try {
                SharedPreferences.Editor edit = preferences.edit();
                for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                    String key = entry.getKey(); Object value = entry.getValue();
                    if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                    else if (value instanceof Float) edit.putFloat(key, (Float) value);
                    else edit.putString(key, (String) value);
                }
                if (!edit.commit()) throw new IOException("Preferences commit failed.");
            } catch (IOException | RuntimeException failure) {
                error = "Changes could not be saved on this device. Your edits are still here. Retry saving.";
            }
            final String result = error;
            main.post(() -> {
                saving = --pendingSaves > 0;
                saveError = result;
                notifyChanged();
                if (result.isEmpty() && afterSaved != null) afterSaved.run();
                else if (!result.isEmpty() && afterFailure != null) afterFailure.run();
            });
        });
        if (!wasSaving) notifyChanged();
    }
}
