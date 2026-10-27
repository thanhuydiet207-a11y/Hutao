package com.hutao.terminal;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Exposes the terminal $HOME to the system file picker and file managers. Document IDs are absolute paths. */
public class HomeDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOT_COLUMNS = {Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_ICON, Root.COLUMN_AVAILABLE_BYTES};
    private static final String[] DOC_COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};

    private File home() {
        return new File(getContext().getFilesDir(), "home");
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        File home = home();
        home.mkdirs();
        c.newRow()
                .add(Root.COLUMN_ROOT_ID, "home")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD | Root.FLAG_LOCAL_ONLY)
                .add(Root.COLUMN_TITLE, "Hutao")
                .add(Root.COLUMN_DOCUMENT_ID, home.getAbsolutePath())
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(Root.COLUMN_AVAILABLE_BYTES, home.getFreeSpace());
        return c;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLUMNS);
        addRow(c, fileFor(documentId));
        return c;
    }

    @Override public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLUMNS);
        File[] children = fileFor(parentId).listFiles();
        if (children == null) return c;
        for (File f : children) {
            try {
                addRow(c, fileFor(f.getAbsolutePath())); // hides symlinks leading out of $HOME, e.g. ~/storage
            } catch (FileNotFoundException outside) {
                // not exposed
            }
        }
        return c;
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public String createDocument(String parentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = fileFor(parentId);
        File f = new File(parent, new File(displayName).getName()); // drop any path parts from the name
        for (int i = 1; f.exists(); i++) f = new File(parent, i + "_" + new File(displayName).getName());
        try {
            boolean ok = Document.MIME_TYPE_DIR.equals(mimeType) ? f.mkdir() : f.createNewFile();
            if (!ok) throw new IOException("create failed");
        } catch (IOException e) {
            throw new FileNotFoundException("Không tạo được " + f + ": " + e.getMessage());
        }
        return f.getAbsolutePath();
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        File f = fileFor(documentId);
        if (f.equals(home())) throw new FileNotFoundException("Không xoá được thư mục home");
        try {
            TarExtractor.deleteTree(f);
        } catch (IOException e) {
            throw new FileNotFoundException("Không xoá được " + f + ": " + e.getMessage());
        }
    }

    @Override public String getDocumentType(String documentId) throws FileNotFoundException {
        return mimeType(fileFor(documentId));
    }

    @Override public boolean isChildDocument(String parentId, String documentId) {
        try {
            return fileFor(documentId).getCanonicalPath().startsWith(fileFor(parentId).getCanonicalPath() + "/");
        } catch (IOException e) {
            return false;
        }
    }

    /** Maps a document ID back to a file, refusing anything outside $HOME (IDs come from other apps). */
    private File fileFor(String documentId) throws FileNotFoundException {
        try {
            File f = new File(documentId);
            String home = home().getCanonicalPath();
            String canonical = f.getCanonicalPath();
            if (!canonical.equals(home) && !canonical.startsWith(home + "/")) {
                throw new FileNotFoundException("Ngoài thư mục home: " + documentId);
            }
            if (!f.exists()) throw new FileNotFoundException(documentId);
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private void addRow(MatrixCursor c, File f) {
        int flags = f.canWrite() ? Document.FLAG_SUPPORTS_DELETE : 0;
        if (f.isDirectory() && f.canWrite()) flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        else if (f.canWrite()) flags |= Document.FLAG_SUPPORTS_WRITE;
        c.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, f.getAbsolutePath())
                .add(Document.COLUMN_DISPLAY_NAME, f.equals(home()) ? "Hutao" : f.getName())
                .add(Document.COLUMN_MIME_TYPE, mimeType(f))
                .add(Document.COLUMN_FLAGS, flags)
                .add(Document.COLUMN_SIZE, f.length())
                .add(Document.COLUMN_LAST_MODIFIED, f.lastModified());
    }

    private static String mimeType(File f) {
        if (f.isDirectory()) return Document.MIME_TYPE_DIR;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
        return type != null ? type : "application/octet-stream";
    }
}
