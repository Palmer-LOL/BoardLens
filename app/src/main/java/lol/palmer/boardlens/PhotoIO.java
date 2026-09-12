package lol.palmer.boardlens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lol.palmer.boardlens.core.AtomicStorage;

final class PhotoIO {
    private static final long MAX_BYTES = 24L * 1024 * 1024;

    static Bitmap importPhoto(Context context, Uri uri) throws IOException {
        File temporary = File.createTempFile("import-", ".image", context.getCacheDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("The selected image is unavailable.");
                byte[] buffer = new byte[16384];
                long count = 0;
                int n;
                while ((n = input.read(buffer)) != -1) {
                    count += n;
                    if (count > MAX_BYTES) throw new IOException("Choose an image smaller than 24 MB.");
                    output.write(buffer, 0, n);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getPath(), options);
            if (options.outWidth < 128 || options.outHeight < 128 ||
                    (long) options.outWidth * options.outHeight > 120_000_000L) {
                throw new IOException("Use a clear board photo at least 128 pixels across, up to 120 megapixels.");
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1800)
                options.inSampleSize *= 2;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(temporary.getPath(), options);
            if (bitmap == null) throw new IOException("This image format could not be decoded.");
            Matrix transform = new Matrix();
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                orientation = new ExifInterface(temporary.getPath()).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (IOException ignored) { /* Some valid image formats do not contain EXIF. */ }
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: transform.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: transform.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: transform.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    transform.setRotate(90); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: transform.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    transform.setRotate(-90); transform.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: transform.setRotate(-90); break;
                default: break;
            }
            if (!transform.isIdentity()) {
                Bitmap normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, true);
                if (normalized != bitmap) bitmap.recycle();
                bitmap = normalized;
            }
            return bitmap;
        } finally { AtomicStorage.delete(temporary); }
    }

    static void save(Bitmap bitmap, File file, boolean png) throws IOException {
        AtomicStorage.write(file, output -> {
            if (!bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, output))
                throw new IOException("Could not save the image.");
        });
    }

    static int[] pixels(Bitmap bitmap) {
        int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(data, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return data;
    }
}
