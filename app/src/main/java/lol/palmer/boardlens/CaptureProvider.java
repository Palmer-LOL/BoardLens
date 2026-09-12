package lol.palmer.boardlens;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Camera access is limited to one unpredictable, explicitly granted cache file. */
public final class CaptureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        List<String> path = uri.getPathSegments();
        if (!"content".equals(uri.getScheme()) ||
                !(getContext().getPackageName() + ".capture").equals(uri.getAuthority()) ||
                uri.getQuery() != null || uri.getFragment() != null ||
                path.size() != 1 || !path.get(0).matches("[a-f0-9-]{36}\\.jpg")) {
            throw new FileNotFoundException("Unknown capture");
        }
        File file = new File(new File(getContext().getCacheDir(), "captures"), path.get(0));
        if (!file.isFile()) throw new FileNotFoundException("Capture expired");
        return file;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int flags;
        switch (mode) {
            case "r": flags = ParcelFileDescriptor.MODE_READ_ONLY; break;
            case "w": case "wt":
                flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE; break;
            case "rw": flags = ParcelFileDescriptor.MODE_READ_WRITE; break;
            case "rwt": flags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE; break;
            default: throw new FileNotFoundException("Unsupported mode");
        }
        return ParcelFileDescriptor.open(resolve(uri), flags);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null
                    ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            }
            cursor.addRow(row);
            return cursor;
        } catch (FileNotFoundException ignored) { return null; }
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
