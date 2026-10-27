package com.hutao.terminal;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Alpine Linux userland run through proot, which gives a real package manager (apk). */
public final class Linux {
    private static final String MIRROR = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/";

    public interface Progress { void update(String message); }

    private Linux() {}

    static File rootfs(Context c) { return new File(c.getFilesDir(), "alpine"); }

    static boolean isInstalled(Context c) { return new File(rootfs(c), ".hutao-installed").exists(); }

    private static String alpineArch() {
        switch (Build.SUPPORTED_ABIS[0]) {
            case "arm64-v8a": return "aarch64";
            case "armeabi-v7a": return "armv7";
            case "x86_64": return "x86_64";
            case "x86": return "x86";
            default: throw new IllegalStateException("unsupported ABI " + Build.SUPPORTED_ABIS[0]);
        }
    }

    /** Downloads, verifies (sha256) and unpacks the Alpine minirootfs. Blocking; call off the UI thread. */
    static void install(Context c, Progress p) throws Exception {
        String arch = alpineArch();
        p.update("Đang tìm bản Alpine…");
        String file = null, sha256 = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                open(MIRROR + arch + "/latest-releases.yaml"), StandardCharsets.UTF_8))) {
            boolean mini = false;
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.startsWith("flavor:")) mini = line.endsWith("alpine-minirootfs");
                else if (mini && line.startsWith("file:")) file = line.substring(5).trim();
                else if (mini && line.startsWith("sha256:")) { sha256 = line.substring(7).trim(); break; }
            }
        }
        if (file == null || sha256 == null) throw new IOException("không tìm thấy minirootfs cho " + arch);

        File tgz = new File(c.getCacheDir(), file);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new DigestInputStream(open(MIRROR + arch + "/" + file), md);
             FileOutputStream out = new FileOutputStream(tgz)) {
            byte[] buf = new byte[65536];
            long total = 0;
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                total += n;
                p.update("Đang tải " + file + ": " + total / 1024 + " KB");
            }
        }
        if (!sha256.equalsIgnoreCase(hex(md.digest()))) {
            tgz.delete();
            throw new IOException("sai checksum, tải lại sau");
        }

        p.update("Đang giải nén…");
        File root = rootfs(c);
        try (InputStream in = new GZIPInputStream(new java.io.FileInputStream(tgz))) {
            TarExtractor.extract(in, root);
        } finally {
            tgz.delete();
        }
        try (FileOutputStream out = new FileOutputStream(new File(root, "etc/resolv.conf"))) {
            out.write("nameserver 8.8.8.8\nnameserver 1.1.1.1\n".getBytes(StandardCharsets.UTF_8));
        }
        new File(root, ".hutao-installed").createNewFile();
    }

    /** argv for a login shell inside Alpine; argv[0] is the proot binary. */
    static String[] command(Context c) {
        String lib = c.getApplicationInfo().nativeLibraryDir;
        File home = new File(c.getFilesDir(), "home");
        List<String> a = new ArrayList<>();
        a.add(lib + "/libproot.so");
        a.add("--kill-on-exit");
        a.add("--link2symlink"); // apk creates hard links, which Android's app storage forbids
        a.add("-0");             // pretend to be root so apk can install packages
        a.add("-r"); a.add(rootfs(c).getAbsolutePath());
        a.add("-b"); a.add("/dev");
        a.add("-b"); a.add("/proc");
        a.add("-b"); a.add("/sys");
        a.add("-b"); a.add(home.getAbsolutePath() + ":/mnt/hutao");
        a.add("-w"); a.add("/root");
        a.add("/usr/bin/env"); a.add("-i");
        a.add("HOME=/root");
        a.add("TERM=xterm-256color");
        a.add("COLORTERM=truecolor");
        a.add("LANG=C.UTF-8");
        a.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        a.add("/bin/sh"); a.add("-l");
        return a.toArray(new String[0]);
    }

    /** Variables proot itself needs: where its loader lives and a writable tmp dir. */
    static void putProotEnv(Context c, Map<String, String> env) {
        String lib = c.getApplicationInfo().nativeLibraryDir;
        env.put("PROOT_LOADER", lib + "/libproot-loader.so");
        if (new File(lib, "libproot-loader32.so").exists()) env.put("PROOT_LOADER_32", lib + "/libproot-loader32.so");
        env.put("PROOT_TMP_DIR", c.getCacheDir().getAbsolutePath());
        env.put("LD_LIBRARY_PATH", lib);
    }

    /** `~/bin/alpine` so the plain Android shell can enter Linux too. */
    static void writeLauncherScript(Context c, File binDir, Map<String, String> env) throws IOException {
        StringBuilder s = new StringBuilder("#!/system/bin/sh\n");
        for (String k : new String[]{"PROOT_LOADER", "PROOT_LOADER_32", "PROOT_TMP_DIR", "LD_LIBRARY_PATH"}) {
            if (env.containsKey(k)) s.append("export ").append(k).append('=').append(quote(env.get(k))).append('\n');
        }
        s.append("[ -e ").append(quote(rootfs(c) + "/.hutao-installed"))
                .append(" ] || { echo 'Chưa cài Linux: bấm nút Linux trên thanh tab.'; exit 1; }\n");
        s.append("exec");
        for (String arg : command(c)) s.append(' ').append(quote(arg));
        s.append(" \"$@\"\n");
        File f = new File(binDir, "alpine");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.toString().getBytes(StandardCharsets.UTF_8));
        }
        f.setExecutable(true, true);
    }

    static String quote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    private static InputStream open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode() + " " + url);
        return conn.getInputStream();
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
