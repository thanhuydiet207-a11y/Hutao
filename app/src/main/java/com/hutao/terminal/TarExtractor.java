package com.hutao.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Minimal tar reader for Linux rootfs tarballs (ustar + pax path/linkpath).
 * Android has no reliable tar binary on older versions, and the rootfs's own busybox can't run before it exists.
 */
public final class TarExtractor {
    private TarExtractor() {}

    public static void extract(InputStream in, File destDir) throws IOException {
        Path root = destDir.toPath();
        Files.createDirectories(root);
        root = root.toRealPath();
        byte[] header = new byte[512];
        String paxPath = null, paxLink = null;
        while (readFully(in, header)) {
            if (isZero(header)) break;
            String name = str(header, 0, 100);
            String prefix = str(header, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            String link = str(header, 157, 100);
            int mode = (int) octal(header, 100, 8);
            long size = octal(header, 124, 12);
            char type = (char) header[156];

            if (type == 'x') { // pax extended header for the next entry
                String pax = new String(readBytes(in, size), StandardCharsets.UTF_8);
                skipPadding(in, size);
                paxPath = paxValue(pax, "path");
                paxLink = paxValue(pax, "linkpath");
                continue;
            }
            if (type == 'g') { skip(in, size); skipPadding(in, size); continue; }
            // shortcut: GNU 'L'/'K' long names unsupported; Alpine/Ubuntu rootfs tarballs use pax.
            if (paxPath != null) name = paxPath;
            if (paxLink != null) link = paxLink;
            paxPath = paxLink = null;

            Path out = safeResolve(root, name);
            if (out == null) { skip(in, size); skipPadding(in, size); continue; } // the root itself, e.g. "./"
            switch (type) {
                case '5':
                    Files.createDirectories(out);
                    applyMode(out.toFile(), mode);
                    break;
                case '2':
                    Files.createDirectories(out.getParent());
                    checkInside(root, out.getParent());
                    Files.deleteIfExists(out);
                    Files.createSymbolicLink(out, Paths.get(link));
                    break;
                case '1': {
                    Path target = safeResolve(root, link);
                    Files.createDirectories(out.getParent());
                    checkInside(root, out.getParent());
                    Files.copy(target, out, StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
                case '0': case '\0': case '7': {
                    Files.createDirectories(out.getParent());
                    checkInside(root, out.getParent());
                    Files.deleteIfExists(out);
                    try (java.io.OutputStream os = Files.newOutputStream(out)) {
                        copy(in, os, size);
                    }
                    applyMode(out.toFile(), mode);
                    skipPadding(in, size);
                    break;
                }
                default: // devices, fifos: not creatable without root
                    skip(in, size);
                    skipPadding(in, size);
            }
        }
    }

    /** Resolves an archive path under root, refusing anything that climbs out with "..". Null for the root itself. */
    static Path safeResolve(Path root, String name) throws IOException {
        Path p = root.resolve(name.replaceFirst("^/+", "")).normalize();
        if (!p.startsWith(root)) throw new IOException("tar entry escapes destination: " + name);
        return p.equals(root) ? null : p;
    }

    /** A parent dir reached through a symlink must still be inside the root. */
    private static void checkInside(Path root, Path dir) throws IOException {
        if (!dir.toRealPath().startsWith(root)) throw new IOException("tar entry writes through symlink outside: " + dir);
    }

    private static void applyMode(File f, int mode) {
        f.setReadable(true, (mode & 0004) == 0);
        f.setWritable(true, true);
        if ((mode & 0111) != 0) f.setExecutable(true, (mode & 0011) == 0);
    }

    static String paxValue(String pax, String key) {
        // Records look like "<len> <key>=<value>\n".
        for (String line : pax.split("\n")) {
            int sp = line.indexOf(' ');
            if (sp > 0 && line.startsWith(key + "=", sp + 1)) return line.substring(sp + 2 + key.length());
        }
        return null;
    }

    private static String str(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            if (b[i] >= '0' && b[i] <= '7') v = v * 8 + (b[i] - '0');
            else if (v > 0 || (b[i] != ' ' && b[i] != 0)) break;
        }
        return v;
    }

    private static boolean isZero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int n = 0;
        while (n < buf.length) {
            int r = in.read(buf, n, buf.length - n);
            if (r < 0) {
                if (n == 0) return false;
                throw new IOException("truncated tar");
            }
            n += r;
        }
        return true;
    }

    private static byte[] readBytes(InputStream in, long size) throws IOException {
        byte[] b = new byte[(int) size];
        if (!readFully(in, b) && size > 0) throw new IOException("truncated tar");
        return b;
    }

    private static void copy(InputStream in, java.io.OutputStream out, long size) throws IOException {
        byte[] buf = new byte[65536];
        while (size > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, size));
            if (r < 0) throw new IOException("truncated tar");
            if (out != null) out.write(buf, 0, r);
            size -= r;
        }
    }

    private static void skip(InputStream in, long size) throws IOException {
        copy(in, null, size);
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        skip(in, (512 - size % 512) % 512);
    }
}
