package com.ceptarayici.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Uygulamanın önbellek klasöründeki iki alt klasörü paylaşır:
 *  camera/ : kamera uygulamasının çektiği fotoğrafı yazdığı yer
 *  share/  : paylaşılmak üzere hazırlanan PDF dosyaları
 */
public class FilesProvider extends ContentProvider {

    static Uri uriFor(Context c, String folder, String name) {
        return new Uri.Builder().scheme("content").authority(c.getPackageName() + ".files")
                .appendPath(folder).appendPath(name).build();
    }

    static File fileFor(Context c, Uri uri) throws FileNotFoundException {
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 2) throw new FileNotFoundException("Geçersiz yol");
        String folder = seg.get(0), name = seg.get(1);
        if (!("camera".equals(folder) || "share".equals(folder)) || name.contains("/") || name.contains(".."))
            throw new FileNotFoundException("Geçersiz yol");
        File dir = new File(c.getCacheDir(), folder);
        dir.mkdirs();
        return new File(dir, name);
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(getContext(), uri);
        int m = ParcelFileDescriptor.parseMode(mode);
        if ((m & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0 || (m & ParcelFileDescriptor.MODE_READ_WRITE) != 0) {
            m |= ParcelFileDescriptor.MODE_CREATE;
        }
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try { f = fileFor(getContext(), uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getLastPathSegment();
        if (p == null) return "application/octet-stream";
        p = p.toLowerCase();
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String sel, String[] args) { return 0; }
}
