package com.hutao.terminal;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TarExtractorTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void entry(ByteArrayOutputStream out, String name, char type, String link, byte[] data, int mode) {
        byte[] h = new byte[512];
        put(h, 0, name);
        put(h, 100, String.format("%07o", mode));
        put(h, 124, String.format("%011o", data.length));
        h[156] = (byte) type;
        put(h, 157, link);
        put(h, 257, "ustar");
        out.write(h, 0, 512);
        out.write(data, 0, data.length);
        out.write(new byte[(512 - data.length % 512) % 512], 0, (512 - data.length % 512) % 512);
    }

    private static void put(byte[] h, int off, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, off, b.length);
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test public void extractsFilesDirsSymlinksAndPaxNames() throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        entry(tar, "./", '5', "", new byte[0], 0755);
        entry(tar, "bin/", '5', "", new byte[0], 0755);
        entry(tar, "bin/busybox", '0', "", bytes("#!/bin/true\n"), 0755);
        entry(tar, "bin/sh", '2', "/bin/busybox", new byte[0], 0777);
        entry(tar, "etc/hostname", '0', "", bytes("hutao"), 0644);
        entry(tar, "etc/hostname2", '1', "etc/hostname", new byte[0], 0644);
        String longName = "usr/share/" + "x".repeat(120) + ".txt";
        entry(tar, "PaxHeader", 'x', "", bytes((longName.length() + 9 + 3) + " path=" + longName + "\n"), 0644);
        entry(tar, "truncated", '0', "", bytes("long"), 0644);
        tar.write(new byte[1024], 0, 1024);

        File root = tmp.newFolder("root");
        TarExtractor.extract(new ByteArrayInputStream(tar.toByteArray()), root);

        assertEquals("#!/bin/true\n", new String(Files.readAllBytes(new File(root, "bin/busybox").toPath())));
        assertTrue(new File(root, "bin/busybox").canExecute());
        assertEquals("/bin/busybox", Files.readSymbolicLink(new File(root, "bin/sh").toPath()).toString());
        assertEquals("hutao", new String(Files.readAllBytes(new File(root, "etc/hostname2").toPath())));
        assertEquals("long", new String(Files.readAllBytes(new File(root, longName).toPath())));
    }

    @Test public void rejectsPathTraversal() throws IOException {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        entry(tar, "../evil", '0', "", bytes("x"), 0644);
        File root = tmp.newFolder("root");
        assertThrows(IOException.class, () -> TarExtractor.extract(new ByteArrayInputStream(tar.toByteArray()), root));
        assertFalse(new File(root.getParentFile(), "evil").exists());
    }

    @Test public void rejectsWritingThroughSymlinkOutsideRoot() throws IOException {
        File outside = tmp.newFolder("outside");
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        entry(tar, "lib", '2', outside.getAbsolutePath(), new byte[0], 0777);
        entry(tar, "lib/evil", '0', "", bytes("x"), 0644);
        File root = tmp.newFolder("root");
        assertThrows(IOException.class, () -> TarExtractor.extract(new ByteArrayInputStream(tar.toByteArray()), root));
        assertFalse(new File(outside, "evil").exists());
    }

    @Test public void paxValueParsesRecords() {
        assertEquals("a/b", TarExtractor.paxValue("12 path=a/b\n20 linkpath=c\n", "path"));
        assertEquals("c", TarExtractor.paxValue("12 path=a/b\n20 linkpath=c\n", "linkpath"));
        assertNull(TarExtractor.paxValue("12 path=a/b\n", "linkpath"));
    }
}
