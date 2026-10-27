package com.hutao.terminal;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Target of Android's "Share"/"Open with": saves what it receives into ~/downloads. */
public class ShareReceiverActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File dir = new File(getFilesDir(), "home/downloads");
        dir.mkdirs();
        Intent in = getIntent();
        List<String> saved = new ArrayList<>();
        try {
            if (Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) {
                saved.add(copy(in.getData(), dir));
            } else if (Intent.ACTION_SEND.equals(in.getAction())) {
                Uri stream = in.getParcelableExtra(Intent.EXTRA_STREAM);
                CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (stream != null) saved.add(copy(stream, dir));
                else if (text != null) saved.add(write(dir, "shared-" + System.currentTimeMillis() + ".txt",
                        text.toString().getBytes(StandardCharsets.UTF_8)));
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(in.getAction())) {
                ArrayList<Uri> streams = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (streams != null) for (Uri u : streams) saved.add(copy(u, dir));
            }
            Toast.makeText(this, saved.isEmpty() ? "Không có gì để lưu"
                    : "Đã lưu vào ~/downloads: " + String.join(", ", saved), Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Lưu file lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private String copy(Uri uri, File dir) throws IOException {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (RuntimeException ignored) {
            // some providers reject queries; fall back to the URI path
        }
        if (name == null) name = uri.getLastPathSegment();
        try (InputStream src = getContentResolver().openInputStream(uri)) {
            if (src == null) throw new IOException("không mở được " + uri);
            File out = uniqueFile(dir, name);
            try (OutputStream dst = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                for (int n; (n = src.read(buf)) > 0; ) dst.write(buf, 0, n);
            }
            return out.getName();
        }
    }

    private String write(File dir, String name, byte[] data) throws IOException {
        File out = uniqueFile(dir, name);
        try (OutputStream dst = new FileOutputStream(out)) {
            dst.write(data);
        }
        return out.getName();
    }

    /** Sender-supplied names are untrusted: keep only the last path part and never overwrite. */
    static File uniqueFile(File dir, String name) {
        String base = name == null ? "" : name.substring(name.lastIndexOf('/') + 1).trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "file";
        File f = new File(dir, base);
        for (int i = 1; f.exists(); i++) f = new File(dir, i + "_" + base);
        return f;
    }
}
